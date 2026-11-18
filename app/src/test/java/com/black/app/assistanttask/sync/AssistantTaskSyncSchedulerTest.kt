package com.black.app.assistanttask.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.model.WorkSpec
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.UUID
import java.util.concurrent.TimeUnit

/** [AssistantTaskSyncScheduler]의 주기 작업 예약 조건과 취소, 바로 동기화 작업의 조건과 이어 붙이기 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssistantTaskSyncSchedulerTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var scheduler: AssistantTaskSyncScheduler

    /** 실제 동기화를 돌리지 않고 성공만 돌려주는 작업자 */
    private class NoOpWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result = Result.success()
    }

    private val noOpWorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = NoOpWorker(appContext, workerParameters)
    }

    /** 작업자를 성공만 돌려주는 것으로 바꿔 WorkManager 초기화 */
    @Before
    fun setupScheduler() {
        val configuration = Configuration.Builder()
            .setWorkerFactory(noOpWorkerFactory)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        scheduler = AssistantTaskSyncScheduler(context)
    }

    private fun workInfos() = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(AssistantTaskSyncScheduler.PERIODIC_WORK_NAME).get()

    private fun syncNowWorkInfos() = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(AssistantTaskSyncScheduler.SYNC_NOW_WORK_NAME).get()

    /** 예약된 작업의 가속 실행 설정을 읽기 위한 WorkManager 내부 저장값 */
    private fun workSpec(id: UUID): WorkSpec =
        checkNotNull((WorkManager.getInstance(context) as WorkManagerImpl).workDatabase.workSpecDao().getWorkSpec(id.toString()))

    /**
     * 15분 주기와 네트워크 연결 조건으로 예약되는지 검증
     *
     * Given: 예약 없음
     * When: schedulePeriodic 호출
     * Then: 작업 하나, 반복 간격 15분, 네트워크 조건 CONNECTED
     */
    @Test
    fun test_01_schedulesFifteenMinutePeriodicWithNetwork() = runTest {
        /** Given / When **/
        scheduler.schedulePeriodic()

        /** Then **/
        val info = workInfos().single()
        assertEquals(TimeUnit.MINUTES.toMillis(15), info.periodicityInfo?.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }

    /**
     * 두 번 예약해도 기존 작업을 유지하는지 검증
     *
     * Given: 예약된 주기 작업
     * When: schedulePeriodic 다시 호출
     * Then: 같은 id의 작업 하나
     */
    @Test
    fun test_02_keepsExistingPeriodic() = runTest {
        /** Given **/
        scheduler.schedulePeriodic()
        val first = workInfos().single()

        /** When **/
        scheduler.schedulePeriodic()

        /** Then **/
        assertEquals(listOf(first.id), workInfos().map { it.id })
    }

    /**
     * 취소하면 작업이 취소 상태가 되는지 검증
     *
     * Given: 예약된 주기 작업
     * When: cancelPeriodic 호출
     * Then: 상태 CANCELLED
     */
    @Test
    fun test_03_cancelPeriodic() = runTest {
        /** Given **/
        scheduler.schedulePeriodic()

        /** When **/
        scheduler.cancelPeriodic()

        /** Then **/
        assertEquals(WorkInfo.State.CANCELLED, workInfos().single().state)
    }

    /**
     * 바로 동기화가 네트워크 연결 조건과 가속 실행으로 예약되는지 검증
     *
     * Given: 예약 없음, Robolectric API 31 이상
     * When: enqueueSyncNow 호출
     * Then: SYNC_NOW_WORK_NAME에 작업 하나, 상태 ENQUEUED, 네트워크 조건 CONNECTED, 가속 실행
     */
    @Test
    fun test_04_syncNowEnqueuesWithNetwork() = runTest {
        /** Given / When **/
        scheduler.enqueueSyncNow()

        /** Then **/
        val info = syncNowWorkInfos().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertTrue(workSpec(info.id).expedited)
    }

    /**
     * 끝나지 않은 바로 동기화가 있을 때 다시 불러도 그 작업을 취소하지 않고 뒤에 잇는지 검증
     *
     * Given: 네트워크 연결을 기다리는 바로 동기화 작업
     * When: enqueueSyncNow 다시 호출
     * Then: 처음 작업은 ENQUEUED 그대로, 새 작업은 그 뒤를 기다리는 BLOCKED
     */
    @Test
    fun test_05_syncNowAppendsWithoutCancellingUnfinished() = runTest {
        /** Given **/
        scheduler.enqueueSyncNow()
        val first = syncNowWorkInfos().single()

        /** When **/
        scheduler.enqueueSyncNow()

        /** Then **/
        val infos = syncNowWorkInfos().associateBy { it.id }
        assertEquals(2, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.getValue(first.id).state)
        assertEquals(WorkInfo.State.BLOCKED, infos.values.single { it.id != first.id }.state)
    }
}
