package com.black.feature.usagelog.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.model.WorkSpec
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.black.feature.usagelog.model.SendReason
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.UUID
import java.util.concurrent.TimeUnit

/** [UsageLogScheduler]의 고유 작업 예약과 취소, 옛 Worker 클래스 주기 작업 갱신, 전송 작업의 네트워크 조건과 재시도 설정 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogSchedulerTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var scheduler: UsageLogScheduler

    /** 실제 수집·전송을 돌리지 않고 정해 둔 결과를 돌려주는 작업자 */
    private class NoOpWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            reasons.add(inputData.getString(UsageLogSendWorker.KEY_REASON).orEmpty())
            return result
        }

        companion object {
            /** 실행된 작업이 받은 입력 reason 기록 */
            val reasons = ArrayList<String>()

            /** 실행 결과 */
            var result: Result = Result.success()
        }
    }

    /**
     * 업데이트로 경로가 바뀌어 사라진 옛 주기 수집 Worker 클래스를 흉내 내는 작업자
     *
     * 생성자가 예외를 던져, 업데이트 뒤 옛 클래스 이름으로 저장된 작업처럼 WorkManager가 작업자를 만들지 못하고 FAILED로 끝냄.
     */
    private class LegacyCollectWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        init {
            throw ClassNotFoundException("com.black.app.usagelog.work.UsageLogCollectWorker")
        }

        override fun doWork(): Result = Result.success()
    }

    /** 옛 Worker 클래스는 생성을 시도하고(실패), 그 밖의 클래스는 정해 둔 결과만 돌려주는 작업자로 바꾸는 생성기 */
    private val noOpWorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = when (workerClassName) {
            LegacyCollectWorker::class.java.name -> LegacyCollectWorker(appContext, workerParameters)
            else -> NoOpWorker(appContext, workerParameters)
        }
    }

    /**
     * 작업자를 정해 둔 결과만 돌려주는 것으로 바꿔(옛 Worker 클래스는 생성 실패) WorkManager 초기화
     *
     * 지연이 없는 주기 작업은 예약 즉시 실행되며, 실행 뒤에도 ENQUEUED로 돌아옴.
     */
    @Before
    fun setupScheduler() {
        NoOpWorker.reasons.clear()
        NoOpWorker.result = ListenableWorker.Result.success()
        val configuration = Configuration.Builder()
            .setWorkerFactory(noOpWorkerFactory)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        scheduler = UsageLogScheduler(context)
    }

    private fun workInfos() = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(UsageLogScheduler.PERIODIC_COLLECT_WORK_NAME).get()

    private fun sendWorkInfos(name: String) =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    /** 예약된 작업의 백오프·가속 실행 설정을 읽기 위한 WorkManager 내부 저장값 */
    private fun workSpec(id: UUID): WorkSpec =
        checkNotNull((WorkManager.getInstance(context) as WorkManagerImpl).workDatabase.workSpecDao().getWorkSpec(id.toString()))

    /**
     * 주기 수집 작업 이름으로 옛 Worker 클래스의 주기 작업 예약
     *
     * [initialDelayHours]가 0이면 예약 즉시 실행돼 작업자를 만들지 못하고 FAILED로 끝나며, 0보다 크면 실행되지 않고 대기함.
     */
    private fun enqueueLegacyPeriodicCollect(initialDelayHours: Long) {
        val request = PeriodicWorkRequestBuilder<LegacyCollectWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(initialDelayHours, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UsageLogScheduler.PERIODIC_COLLECT_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * 두 번 예약해도 고유 작업이 하나만 대기하고 네트워크 조건이 없는지 검증
     *
     * Given: 예약 없음
     * When: schedulePeriodicCollect 두 번 호출
     * Then: 작업 하나, 상태 ENQUEUED, 네트워크 조건 NOT_REQUIRED
     */
    @Test
    fun test_01_schedulePeriodicEnqueuesOneUniqueWork() = runTest {
        /** Given / When **/
        scheduler.schedulePeriodicCollect()
        scheduler.schedulePeriodicCollect()

        /** Then **/
        val info = workInfos().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.NOT_REQUIRED, info.constraints.requiredNetworkType)
    }

    /**
     * 취소하면 작업이 취소 상태가 되는지 검증
     *
     * Given: 예약된 주기 작업
     * When: cancelPeriodicCollect 호출
     * Then: 상태 CANCELLED
     */
    @Test
    fun test_02_cancelPeriodicCancels() = runTest {
        /** Given **/
        scheduler.schedulePeriodicCollect()

        /** When **/
        scheduler.cancelPeriodicCollect()

        /** Then **/
        assertEquals(WorkInfo.State.CANCELLED, workInfos().single().state)
    }

    /**
     * 정기 전송을 두 번 예약해도 대기 작업이 하나만 남는지 검증
     *
     * Given: 예약 없음
     * When: enqueueSend(PERIODIC) 두 번 호출
     * Then: SEND_WORK_NAME에 작업 하나, 실행하면 입력 reason "periodic"
     */
    @Test
    fun test_03_periodicSendKeepsPendingWork() = runTest {
        /** Given / When **/
        scheduler.enqueueSend(SendReason.PERIODIC)
        scheduler.enqueueSend(SendReason.PERIODIC)

        /** Then **/
        val infos = sendWorkInfos(UsageLogScheduler.SEND_WORK_NAME)
        assertEquals(1, infos.size)
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(infos.single().id)
        assertEquals(listOf("periodic"), NoOpWorker.reasons)
    }

    /**
     * 즉시 전송이 대기 중이던 즉시 전송을 대체하고 정기 전송과 별개인지 검증
     *
     * Given: 정기 전송과 MANUAL 즉시 전송이 대기 중
     * When: enqueueSend(HOME_ARRIVE) 호출
     * Then: SEND_NOW_WORK_NAME에서 취소되지 않은 작업은 하나이며 실행하면 입력 reason "home_arrive", 정기 전송은 대기 그대로
     */
    @Test
    fun test_04_immediateSendReplacesPendingWork() = runTest {
        /** Given **/
        scheduler.enqueueSend(SendReason.PERIODIC)
        scheduler.enqueueSend(SendReason.MANUAL)

        /** When **/
        scheduler.enqueueSend(SendReason.HOME_ARRIVE)

        /** Then **/
        val pending = sendWorkInfos(UsageLogScheduler.SEND_NOW_WORK_NAME).single { it.state != WorkInfo.State.CANCELLED }
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(pending.id)
        assertEquals(listOf("home_arrive"), NoOpWorker.reasons)
        assertEquals(WorkInfo.State.ENQUEUED, sendWorkInfos(UsageLogScheduler.SEND_WORK_NAME).single().state)
    }

    /**
     * 정기 전송이 네트워크 연결을 기다리고 1분부터 지수 백오프로 재시도하며 가속 실행을 요청하지 않는지 검증
     *
     * Given: 예약 없음
     * When: enqueueSend(PERIODIC) 호출
     * Then: 네트워크 조건 CONNECTED, 백오프 EXPONENTIAL 1분, 가속 실행 아님
     */
    @Test
    fun test_05_periodicSendRequiresNetworkAndBacksOffFromOneMinute() = runTest {
        /** Given / When **/
        scheduler.enqueueSend(SendReason.PERIODIC)

        /** Then **/
        val info = sendWorkInfos(UsageLogScheduler.SEND_WORK_NAME).single()
        val spec = workSpec(info.id)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(TimeUnit.MINUTES.toMillis(1), spec.backoffDelayDuration)
        assertFalse(spec.expedited)
    }

    /**
     * 즉시 전송이 네트워크 연결을 기다리고 최소 백오프로 재시도하며 가속 실행을 요청하는지 검증
     *
     * Given: 예약 없음, Robolectric API 31 이상
     * When: enqueueSend(HOME_LEAVE) 호출
     * Then: 네트워크 조건 CONNECTED, 백오프 EXPONENTIAL MIN_BACKOFF_MILLIS, 가속 실행
     */
    @Test
    fun test_06_immediateSendRequiresNetworkAndIsExpedited() = runTest {
        /** Given / When **/
        scheduler.enqueueSend(SendReason.HOME_LEAVE)

        /** Then **/
        val info = sendWorkInfos(UsageLogScheduler.SEND_NOW_WORK_NAME).single()
        val spec = workSpec(info.id)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(WorkRequest.MIN_BACKOFF_MILLIS, spec.backoffDelayDuration)
        assertTrue(spec.expedited)
    }

    /**
     * 재시도 대기 중인 정기 전송은 새 예약을 막는지 검증
     *
     * Given: 실행 결과가 retry인 정기 전송을 한 번 실행해 재시도 대기 중
     * When: enqueueSend(PERIODIC) 다시 호출
     * Then: SEND_WORK_NAME에는 처음 작업 하나만 ENQUEUED
     */
    @Test
    fun test_07_retryingPeriodicSendBlocksNextSchedule() = runTest {
        /** Given **/
        NoOpWorker.result = ListenableWorker.Result.retry()
        scheduler.enqueueSend(SendReason.PERIODIC)
        val first = sendWorkInfos(UsageLogScheduler.SEND_WORK_NAME).single()
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(first.id)

        /** When **/
        scheduler.enqueueSend(SendReason.PERIODIC)

        /** Then **/
        val infos = sendWorkInfos(UsageLogScheduler.SEND_WORK_NAME)
        assertEquals(listOf(first.id), infos.map { it.id })
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
        assertEquals(1, infos.single().runAttemptCount)
    }

    /**
     * 실패로 끝난 정기 전송은 다음 예약을 막지 않는지 검증
     *
     * Given: 실행 결과가 failure인 정기 전송을 한 번 실행
     * When: enqueueSend(PERIODIC) 다시 호출
     * Then: 처음 작업은 FAILED, SEND_WORK_NAME에 처음과 다른 id의 작업이 ENQUEUED
     */
    @Test
    fun test_08_failedPeriodicSendDoesNotBlockNextSchedule() = runTest {
        /** Given **/
        NoOpWorker.result = ListenableWorker.Result.failure()
        scheduler.enqueueSend(SendReason.PERIODIC)
        val first = sendWorkInfos(UsageLogScheduler.SEND_WORK_NAME).single()
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(first.id)
        val firstState = sendWorkInfos(UsageLogScheduler.SEND_WORK_NAME).single().state

        /** When **/
        scheduler.enqueueSend(SendReason.PERIODIC)

        /** Then **/
        assertEquals(WorkInfo.State.FAILED, firstState)
        val next = sendWorkInfos(UsageLogScheduler.SEND_WORK_NAME).single { it.state == WorkInfo.State.ENQUEUED }
        assertNotEquals(first.id, next.id)
    }

    /**
     * 대기 중인 옛 Worker 클래스의 주기 작업이 같은 id의 새 Worker 클래스 작업으로 바뀌는지 검증
     *
     * Given: 주기 수집 작업 이름으로 실행되지 않고 대기 중인 옛 Worker 클래스의 주기 작업
     * When: schedulePeriodicCollect 호출
     * Then: 작업 하나이며 id 그대로, Worker 클래스와 태그가 UsageLogCollectWorker이고 옛 클래스 태그 없음, 바로 실행돼 작업자가 한 번 돌고 ENQUEUED
     */
    @Test
    fun test_09_pendingLegacyWorkUpdatesToNewWorkerWithSameId() = runTest {
        /** Given **/
        enqueueLegacyPeriodicCollect(initialDelayHours = 1)
        val legacy = workInfos().single()

        /** When **/
        scheduler.schedulePeriodicCollect()

        /** Then **/
        val info = workInfos().single()
        assertEquals(WorkInfo.State.ENQUEUED, legacy.state)
        assertTrue(LegacyCollectWorker::class.java.name in legacy.tags)
        assertEquals(legacy.id, info.id)
        assertEquals(UsageLogCollectWorker::class.java.name, workSpec(info.id).workerClassName)
        assertTrue(UsageLogCollectWorker::class.java.name in info.tags)
        assertFalse(LegacyCollectWorker::class.java.name in info.tags)
        // 갱신된 요청은 초기 지연이 없어 테스트 스케줄러가 바로 실행
        assertEquals(1, NoOpWorker.reasons.size)
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
    }

    /**
     * 옛 Worker 클래스를 만들지 못해 FAILED로 끝난 주기 작업 대신 새 Worker 클래스 작업이 예약되는지 검증
     *
     * Given: 주기 수집 작업 이름으로 옛 Worker 클래스의 주기 작업을 예약해 바로 실행, 작업자 생성 실패로 FAILED
     * When: schedulePeriodicCollect 호출
     * Then: 옛 작업은 지워지고 다른 id의 작업 하나가 ENQUEUED, 태그가 UsageLogCollectWorker이고 옛 클래스 태그 없음
     */
    @Test
    fun test_10_failedLegacyWorkIsReplacedWithNewWorker() = runTest {
        /** Given **/
        enqueueLegacyPeriodicCollect(initialDelayHours = 0)
        val legacy = workInfos().single()

        /** When **/
        scheduler.schedulePeriodicCollect()

        /** Then **/
        val info = workInfos().single()
        assertEquals(WorkInfo.State.FAILED, legacy.state)
        assertNotEquals(legacy.id, info.id)
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertTrue(UsageLogCollectWorker::class.java.name in info.tags)
        assertFalse(LegacyCollectWorker::class.java.name in info.tags)
    }

    /**
     * 이미 새 Worker 클래스로 대기 중이면 다시 예약해도 id가 그대로인지 검증
     *
     * Given: schedulePeriodicCollect로 예약돼 대기 중인 주기 작업
     * When: schedulePeriodicCollect 다시 호출
     * Then: 작업 하나이며 id 그대로, 상태 ENQUEUED, 태그가 UsageLogCollectWorker
     */
    @Test
    fun test_11_pendingNewWorkKeepsId() = runTest {
        /** Given **/
        scheduler.schedulePeriodicCollect()
        val first = workInfos().single()

        /** When **/
        scheduler.schedulePeriodicCollect()

        /** Then **/
        val info = workInfos().single()
        assertEquals(first.id, info.id)
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertTrue(UsageLogCollectWorker::class.java.name in info.tags)
    }

    /**
     * 조회와 예약 사이에 옛 작업이 FAILED로 끝나 UPDATE가 적용되지 않아도 새 Worker 클래스 작업이 예약되는지 검증
     *
     * Given: 대기 중인 옛 Worker 클래스의 주기 작업, 끝나지 않은 작업 조회 직후 그 작업을 실행해 FAILED로 끝내도록 WorkManager 조회를 감쌈
     * When: schedulePeriodicCollect 호출
     * Then: 옛 작업은 지워지고 다른 id의 작업 하나가 ENQUEUED, 태그가 UsageLogCollectWorker
     */
    @Test
    fun test_12_legacyWorkFailingAfterQueryIsReplacedWithNewWorker() = runTest {
        /** Given **/
        enqueueLegacyPeriodicCollect(initialDelayHours = 1)
        val legacy = workInfos().single()
        val workManager = spyk(WorkManager.getInstance(context))
        var isFirstQuery = true
        every { workManager.getWorkInfosForUniqueWorkFlow(UsageLogScheduler.PERIODIC_COLLECT_WORK_NAME) } answers {
            callOriginal().onEach {
                // 첫 조회가 끝나지 않은 작업을 본 뒤 옛 작업이 실행돼 작업자 생성 실패로 FAILED
                if (isFirstQuery) {
                    isFirstQuery = false
                    WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(legacy.id)
                }
            }
        }
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any<Context>()) } returns workManager

        /** When **/
        scheduler.schedulePeriodicCollect()

        /** Then **/
        val info = workInfos().single()
        assertNotEquals(legacy.id, info.id)
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertTrue(UsageLogCollectWorker::class.java.name in info.tags)
    }
}
