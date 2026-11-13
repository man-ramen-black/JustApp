package com.black.app.usagelog.work

import androidx.work.ListenableWorker
import com.black.app.usagelog.upload.SendReason
import com.black.app.usagelog.upload.UploadResult
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [UsageLogSendWorker]의 전송 결과별 작업 결과와 정기 전송 재시도 상한 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogSendWorkerTest : BaseTest() {

    /**
     * 정기 전송이 재시도 대상으로 실패하면 상한 실행 전까지 재시도하고 상한 실행에서 실패로 끝내는지 검증
     *
     * Given: Failed(true), PERIODIC
     * When: runAttemptCount 0(첫 실행)부터 상한까지 toWorkResult 호출
     * Then: 상한 - 1번째 실행까지 retry, 상한번째 실행부터 failure
     */
    @Test
    fun test_01_periodicRetryableFailureStopsAtMaxAttempts() = runTest {
        /** Given **/
        val maxAttempts = UsageLogSendWorker.MAX_PERIODIC_ATTEMPTS

        /** When **/
        val results = (0..maxAttempts).map { runAttemptCount ->
            UsageLogSendWorker.toWorkResult(UploadResult.Failed(true), SendReason.PERIODIC, runAttemptCount)
        }

        /** Then **/
        val expected = List(maxAttempts - 1) { ListenableWorker.Result.retry() } +
            List(2) { ListenableWorker.Result.failure() }
        assertEquals(expected, results)
    }

    /**
     * 즉시 전송은 재시도 대상 실패면 실행 횟수와 관계없이 재시도하는지 검증
     *
     * Given: Failed(true), HOME_ARRIVE·HOME_LEAVE·MANUAL
     * When: 실행 횟수를 정기 전송 상한보다 크게 해 toWorkResult 호출
     * Then: 모두 retry
     */
    @Test
    fun test_02_immediateRetryableFailureKeepsRetrying() = runTest {
        /** Given **/
        val reasons = listOf(SendReason.HOME_ARRIVE, SendReason.HOME_LEAVE, SendReason.MANUAL)

        /** When **/
        val results = reasons.map { reason ->
            UsageLogSendWorker.toWorkResult(UploadResult.Failed(true), reason, UsageLogSendWorker.MAX_PERIODIC_ATTEMPTS + 5)
        }

        /** Then **/
        assertEquals(List(reasons.size) { ListenableWorker.Result.retry() }, results)
    }

    /**
     * 재시도할 가치가 없는 실패는 첫 실행에서 바로 실패로 끝내는지 검증
     *
     * Given: Failed(false)
     * When: 실행 횟수 0으로 PERIODIC과 MANUAL의 toWorkResult 호출
     * Then: 둘 다 failure
     */
    @Test
    fun test_03_nonRetryableFailureFailsImmediately() = runTest {
        /** Given **/
        val failed = UploadResult.Failed(false)

        /** When **/
        val periodic = UsageLogSendWorker.toWorkResult(failed, SendReason.PERIODIC, 0)
        val manual = UsageLogSendWorker.toWorkResult(failed, SendReason.MANUAL, 0)

        /** Then **/
        assertEquals(ListenableWorker.Result.failure(), periodic)
        assertEquals(ListenableWorker.Result.failure(), manual)
    }

    /**
     * 설정 없음, 보낼 기록 없음, 전송 완료는 성공으로 끝내는지 검증
     *
     * Given: NotConfigured, NothingToSend, Sent(3)
     * When: 실행 횟수를 정기 전송 상한으로 해 PERIODIC의 toWorkResult 호출
     * Then: 모두 success
     */
    @Test
    fun test_04_nonFailureResultsSucceed() = runTest {
        /** Given **/
        val uploadResults = listOf(UploadResult.NotConfigured, UploadResult.NothingToSend, UploadResult.Sent(3))

        /** When **/
        val results = uploadResults.map {
            UsageLogSendWorker.toWorkResult(it, SendReason.PERIODIC, UsageLogSendWorker.MAX_PERIODIC_ATTEMPTS)
        }

        /** Then **/
        assertEquals(List(uploadResults.size) { ListenableWorker.Result.success() }, results)
    }
}
