package com.black.feature.todo.worker

import androidx.work.ListenableWorker
import com.black.feature.todo.model.TodoSyncStatus
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [TodoSyncWorker]의 동기화 결과별 작업 결과와 네트워크·서버 오류 재시도 상한 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TodoSyncWorkerTest : BaseTest() {

    /**
     * 성공과 네트워크·서버 외 실패는 재시도 없이 성공으로 끝내는지 검증
     *
     * Given: SUCCESS, NOT_CONFIGURED, AUTH_ERROR, REQUEST_ERROR
     * When: 실행 횟수 0으로 toWorkResult 호출
     * Then: 모두 success
     */
    @Test
    fun test_01_successAndNonRetryErrorsAreSuccess() = runTest {
        /** Given **/
        val statuses = listOf(
            TodoSyncStatus.SUCCESS,
            TodoSyncStatus.NOT_CONFIGURED,
            TodoSyncStatus.AUTH_ERROR,
            TodoSyncStatus.REQUEST_ERROR,
        )

        /** When **/
        val results = statuses.map { TodoSyncWorker.toWorkResult(it, 0) }

        /** Then **/
        assertEquals(List(statuses.size) { ListenableWorker.Result.success() }, results)
    }

    /**
     * 네트워크 오류는 상한 실행 전까지 재시도하고 상한 실행에서 실패로 끝내는지 검증
     *
     * Given: NETWORK_ERROR
     * When: runAttemptCount 0·1·2로 toWorkResult 호출
     * Then: retry, retry, failure
     */
    @Test
    fun test_02_networkErrorRetriesThenFails() = runTest {
        /** Given **/
        val status = TodoSyncStatus.NETWORK_ERROR

        /** When **/
        val results = (0 until TodoSyncWorker.MAX_ATTEMPTS).map { TodoSyncWorker.toWorkResult(status, it) }

        /** Then **/
        val expected = listOf(
            ListenableWorker.Result.retry(),
            ListenableWorker.Result.retry(),
            ListenableWorker.Result.failure(),
        )
        assertEquals(expected, results)
    }

    /**
     * 노션 서버 오류도 네트워크 오류처럼 상한 실행 전까지 재시도하는지 검증
     *
     * Given: SERVER_ERROR
     * When: runAttemptCount 0·1·2로 toWorkResult 호출
     * Then: retry, retry, failure
     */
    @Test
    fun test_03_serverErrorRetriesThenFails() = runTest {
        /** Given **/
        val status = TodoSyncStatus.SERVER_ERROR

        /** When **/
        val results = (0 until TodoSyncWorker.MAX_ATTEMPTS).map { TodoSyncWorker.toWorkResult(status, it) }

        /** Then **/
        val expected = listOf(
            ListenableWorker.Result.retry(),
            ListenableWorker.Result.retry(),
            ListenableWorker.Result.failure(),
        )
        assertEquals(expected, results)
    }
}
