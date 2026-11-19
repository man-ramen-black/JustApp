package com.black.feature.todo.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [TodoHomeArrivalWorker.enqueue]의 고유 이름, 기존 작업 유지, 조건 없음, 옛 이름 작업 취소 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TodoHomeArrivalEnqueueTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** 대기 상태로 남도록 재시도만 돌려주는 작업자 */
    private class RetryWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result = Result.retry()
    }

    /** 작업자를 재시도만 돌려주는 것으로 바꿔 WorkManager 초기화 */
    @Before
    fun setupWorkManager() {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                RetryWorker(appContext, workerParameters)
        }
        val configuration = Configuration.Builder().setWorkerFactory(factory).setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    }

    /**
     * 고유 이름으로 예약하고 다시 예약해도 대기 중인 작업을 유지하며 네트워크 조건이 없는지 검증
     *
     * Given: 예약 없음
     * When: enqueue 두 번
     * Then: todo_home_arrival 작업 하나, 같은 id, 조건 NOT_REQUIRED
     */
    @Test
    fun test_01_enqueuesUniqueKeepWithoutConstraints() = runTest {
        /** Given / When **/
        TodoHomeArrivalWorker.enqueue(context)
        val first = WorkManager.getInstance(context).getWorkInfosForUniqueWork(TodoHomeArrivalWorker.WORK_NAME).get().single()
        TodoHomeArrivalWorker.enqueue(context)

        /** Then **/
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(TodoHomeArrivalWorker.WORK_NAME).get()
        assertEquals(listOf(first.id), infos.map { it.id })
        assertEquals(NetworkType.NOT_REQUIRED, first.constraints.requiredNetworkType)
    }

    /**
     * 새 이름으로 예약할 때 모듈 분리 전 이름의 도착 작업을 취소하는지 검증
     *
     * Given: 옛 이름의 도착 작업이 네트워크 조건으로 대기 중
     * When: enqueue
     * Then: 옛 작업 CANCELLED, todo_home_arrival 작업 하나
     */
    @Test
    fun test_02_cancelsLegacyWork() = runTest {
        /** Given **/
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TodoHomeArrivalWorker.LEGACY_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RetryWorker>().setConstraints(constraints).build(),
        )

        /** When **/
        TodoHomeArrivalWorker.enqueue(context)

        /** Then **/
        val legacy = WorkManager.getInstance(context).getWorkInfosForUniqueWork(TodoHomeArrivalWorker.LEGACY_WORK_NAME).get()
        assertEquals(listOf(WorkInfo.State.CANCELLED), legacy.map { it.state })
        assertEquals(1, WorkManager.getInstance(context).getWorkInfosForUniqueWork(TodoHomeArrivalWorker.WORK_NAME).get().size)
    }
}
