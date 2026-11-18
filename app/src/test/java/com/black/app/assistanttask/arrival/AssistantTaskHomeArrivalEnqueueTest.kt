package com.black.app.assistanttask.arrival

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
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

/** [AssistantTaskHomeArrivalWorker.enqueue]의 고유 이름, 기존 작업 유지, 조건 없음 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssistantTaskHomeArrivalEnqueueTest : BaseTest() {

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
     * Then: assistant_task_home_arrival 작업 하나, 같은 id, 조건 NOT_REQUIRED
     */
    @Test
    fun test_01_enqueuesUniqueKeepWithoutConstraints() = runTest {
        /** Given / When **/
        AssistantTaskHomeArrivalWorker.enqueue(context)
        val first = WorkManager.getInstance(context).getWorkInfosForUniqueWork("assistant_task_home_arrival").get().single()
        AssistantTaskHomeArrivalWorker.enqueue(context)

        /** Then **/
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("assistant_task_home_arrival").get()
        assertEquals(listOf(first.id), infos.map { it.id })
        assertEquals(NetworkType.NOT_REQUIRED, first.constraints.requiredNetworkType)
    }
}
