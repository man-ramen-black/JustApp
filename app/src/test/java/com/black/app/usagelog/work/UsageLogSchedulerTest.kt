package com.black.app.usagelog.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
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
import com.black.app.usagelog.upload.SendReason
import com.black.test.BaseTest
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

/** [UsageLogScheduler]의 고유 작업 예약과 취소, 전송 작업의 네트워크 조건과 재시도 설정 검증 */
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

    private val noOpWorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = NoOpWorker(appContext, workerParameters)
    }

    /**
     * 작업자를 정해 둔 결과만 돌려주는 것으로 바꿔 WorkManager 초기화
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
}
