package com.black.app.assistanttask.arrival

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.black.app.assistanttask.AssistantTask
import com.black.app.assistanttask.AssistantTaskDue
import com.black.app.assistanttask.AssistantTaskKind
import com.black.app.assistanttask.AssistantTaskReminder
import com.black.app.assistanttask.sync.AssistantTaskSyncResult
import com.black.app.assistanttask.sync.AssistantTaskSyncStatus
import com.black.test.BaseTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.TimeZone

/** [AssistantTaskHomeArrivalWorker]의 동기화 제한 시간과 저장본 기준 알림 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssistantTaskHomeArrivalWorkerTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val shown = mutableListOf<List<AssistantTask>>()

    private val storedTask = AssistantTask(
        "page", "title", AssistantTaskKind.TASK, false, AssistantTaskDue.DateOnly("2026-11-17"),
        AssistantTaskReminder.NONE, "", 0L, "",
    )

    private fun worker(
        notify: (List<AssistantTask>) -> Boolean = { tasks -> shown.add(tasks) },
        sync: suspend () -> AssistantTaskSyncResult,
    ): AssistantTaskHomeArrivalWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = AssistantTaskHomeArrivalWorker(
                appContext,
                workerParameters,
                sync,
                { listOf(storedTask) },
                notify,
                // 2026-11-18 10:00 서울
                { 1_794_963_600_000L },
                { TimeZone.getTimeZone("Asia/Seoul") },
            )
        }
        return TestListenableWorkerBuilder<AssistantTaskHomeArrivalWorker>(context).setWorkerFactory(factory).build()
    }

    /** 노션 조회 단계에서 [status]로 끝난 동기화 결과 */
    private fun fetchFailure(status: AssistantTaskSyncStatus) = AssistantTaskSyncResult(status, failedOnFetch = true)

    /** 조회에 성공한 뒤 [status]로 끝난 동기화 결과 */
    private fun afterFetch(status: AssistantTaskSyncStatus) = AssistantTaskSyncResult(status, failedOnFetch = false)

    /**
     * 동기화가 끝나지 않으면 30초 뒤 저장본으로 알리는지 검증
     *
     * Given: 끝나지 않는 동기화
     * When: doWork
     * Then: 가상 시간 30초에 success, 알림 1회에 저장본 대상
     */
    @Test
    fun test_01_hangingSyncTimesOutAndNotifiesFromStore() = runTest {
        /** Given **/
        val worker = worker(sync = { awaitCancellation() })

        /** When **/
        val result = worker.doWork()

        /** Then **/
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(AssistantTaskHomeArrivalWorker.SYNC_TIMEOUT_MILLIS, currentTime)
        assertEquals(listOf(listOf(storedTask)), shown)
    }

    /**
     * 동기화가 예외로 끝나도 저장본으로 알리는지 검증
     *
     * Given: IllegalStateException을 던지는 동기화
     * When: doWork
     * Then: success, 알림 1회
     */
    @Test
    fun test_02_syncFailureStillNotifies() = runTest {
        /** Given **/
        val worker = worker(sync = { throw IllegalStateException() })

        /** When **/
        val result = worker.doWork()

        /** Then **/
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, shown.size)
    }

    /**
     * 작업 자체의 취소는 삼키지 않고 전파하는지 검증
     *
     * Given: CancellationException을 던지는 동기화
     * When: doWork
     * Then: CancellationException 전파, 알림 0회
     */
    @Test
    fun test_03_cancellationPropagates() = runTest {
        /** Given **/
        val worker = worker(sync = { throw CancellationException("cancel") })

        /** When / Then **/
        try {
            worker.doWork()
            fail()
        } catch (expected: CancellationException) {
            assertEquals(0, shown.size)
        }
    }

    /**
     * 알림 함수가 예외를 던져도 success로 끝내는지 검증
     *
     * Given: 예외를 던지는 알림 함수
     * When: doWork
     * Then: success
     */
    @Test
    fun test_04_notifyFailureStillSuccess() = runTest {
        /** Given **/
        val worker = worker(notify = { throw IllegalStateException() }, sync = { afterFetch(AssistantTaskSyncStatus.SUCCESS) })

        /** When **/
        val result = worker.doWork()

        /** Then **/
        assertEquals(ListenableWorker.Result.success(), result)
    }

    /**
     * 첫 동기화가 조회 단계 네트워크 오류면 잠시 뒤 다시 동기화하고 한 번만 알리는지 검증
     *
     * Given: 첫 번째는 조회 단계 NETWORK_ERROR, 두 번째는 SUCCESS를 돌려주는 동기화
     * When: doWork
     * Then: 동기화 2회, 가상 시간 5초, success, 알림 1회
     */
    @Test
    fun test_05_networkErrorRetriesSyncThenNotifiesOnce() = runTest {
        /** Given **/
        val results = ArrayDeque(listOf(fetchFailure(AssistantTaskSyncStatus.NETWORK_ERROR), afterFetch(AssistantTaskSyncStatus.SUCCESS)))
        var syncCount = 0
        val worker = worker(sync = {
            syncCount++
            results.removeFirst()
        })

        /** When **/
        val result = worker.doWork()

        /** Then **/
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(2, syncCount)
        assertEquals(AssistantTaskHomeArrivalWorker.NETWORK_RETRY_DELAY_MILLIS, currentTime)
        assertEquals(listOf(listOf(storedTask)), shown)
    }

    /**
     * 조회 단계 네트워크 오류가 계속되면 30초에서 다시 시도를 멈추고 저장본으로 알리는지 검증
     *
     * Given: 항상 조회 단계 NETWORK_ERROR를 돌려주는 동기화
     * When: doWork
     * Then: 0초부터 5초 간격으로 25초까지 동기화 6회, 가상 시간 30초, success, 알림 1회에 저장본 대상
     */
    @Test
    fun test_06_persistentNetworkErrorStopsAtTimeoutAndNotifiesFromStore() = runTest {
        /** Given **/
        var syncCount = 0
        val worker = worker(sync = {
            syncCount++
            fetchFailure(AssistantTaskSyncStatus.NETWORK_ERROR)
        })

        /** When **/
        val result = worker.doWork()

        /** Then **/
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(6, syncCount)
        assertEquals(AssistantTaskHomeArrivalWorker.SYNC_TIMEOUT_MILLIS, currentTime)
        assertEquals(listOf(listOf(storedTask)), shown)
    }

    /**
     * 보내기 단계 네트워크 오류면 다시 동기화하지 않고 저장본으로 알리는지 검증
     *
     * Given: 조회에 성공한 뒤 보내기에서 NETWORK_ERROR로 끝나는 동기화
     * When: doWork
     * Then: 동기화 1회, 가상 시간 0초, success, 알림 1회에 저장본 대상
     */
    @Test
    fun test_07_pushNetworkErrorDoesNotRetry() = runTest {
        /** Given **/
        var syncCount = 0
        val worker = worker(sync = {
            syncCount++
            afterFetch(AssistantTaskSyncStatus.NETWORK_ERROR)
        })

        /** When **/
        val result = worker.doWork()

        /** Then **/
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, syncCount)
        assertEquals(0L, currentTime)
        assertEquals(listOf(listOf(storedTask)), shown)
    }

    /**
     * 네트워크 오류가 아닌 실패는 조회 단계여도 다시 동기화하지 않는지 검증
     *
     * Given: NOT_CONFIGURED, 조회 단계 AUTH_ERROR·SERVER_ERROR·REQUEST_ERROR, 조회 뒤 STORAGE_ERROR를 돌려주는 동기화의 작업 하나씩
     * When: 작업마다 doWork
     * Then: 결과마다 동기화 1회, 가상 시간 0초, 모두 success, 알림은 결과 수만큼
     */
    @Test
    fun test_08_nonNetworkFailureDoesNotRetry() = runTest {
        /** Given **/
        val results = listOf(
            afterFetch(AssistantTaskSyncStatus.NOT_CONFIGURED),
            fetchFailure(AssistantTaskSyncStatus.AUTH_ERROR),
            fetchFailure(AssistantTaskSyncStatus.SERVER_ERROR),
            fetchFailure(AssistantTaskSyncStatus.REQUEST_ERROR),
            afterFetch(AssistantTaskSyncStatus.STORAGE_ERROR),
        )
        val syncCounts = mutableListOf<Int>()

        /** When **/
        val workResults = results.map { syncResult ->
            var syncCount = 0
            val workResult = worker(sync = {
                syncCount++
                syncResult
            }).doWork()
            syncCounts += syncCount
            workResult
        }

        /** Then **/
        assertEquals(List(results.size) { 1 }, syncCounts)
        assertEquals(0L, currentTime)
        assertEquals(List(results.size) { ListenableWorker.Result.success() }, workResults)
        assertEquals(results.size, shown.size)
    }
}
