package com.black.core.util

import com.black.test.BaseTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** [runSuspendCatching]의 성공·실패 결과, 이 코루틴의 취소만 다시 던짐, 시간 초과와 NonCancellable 안의 취소 예외는 실패, Error 전파 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CoroutineUtilTest : BaseTest() {

    /**
     * 블록이 suspend 호출을 거쳐 값을 돌려주면 성공 결과인지 검증
     *
     * Given: 1초 기다린 뒤 42를 돌려주는 블록
     * When: runSuspendCatching 호출
     * Then: 성공 결과 42
     */
    @Test
    fun test_01_returnsSuccessValue() = runTest {
        /** Given / When **/
        val result = runSuspendCatching {
            delay(1.seconds)
            42
        }

        /** Then **/
        assertEquals(42, result.getOrNull())
    }

    /**
     * 블록의 Exception은 던지지 않고 그 예외를 담은 실패 결과인지 검증
     *
     * Given: IllegalStateException을 던지는 블록
     * When: runSuspendCatching 호출
     * Then: 같은 예외를 담은 실패 결과
     */
    @Test
    fun test_02_exceptionIsFailure() = runTest {
        /** Given **/
        val failure = IllegalStateException("failed")

        /** When **/
        val result = runSuspendCatching { throw failure }

        /** Then **/
        assertSame(failure, result.exceptionOrNull())
    }

    /**
     * 블록 실행 중 이 코루틴이 취소되면 실패 결과로 바꾸지 않고 취소 예외를 다시 던지는지 검증
     *
     * Given: 자식 코루틴에서 취소될 때까지 기다리는 블록을 runSuspendCatching으로 실행
     * When: 그 자식 코루틴 취소
     * Then: 결과 없이 CancellationException이 던져짐, 자식 코루틴은 취소로 끝남
     */
    @Test
    fun test_03_rethrowsWhenThisCoroutineIsCancelled() = runTest {
        /** Given **/
        var result: Result<Unit>? = null
        var thrown: Throwable? = null
        val job = launch {
            try {
                result = runSuspendCatching { awaitCancellation() }
            } catch (cancellation: CancellationException) {
                thrown = cancellation
                throw cancellation
            }
        }
        // 블록이 취소 대기에 들어갈 때까지 실행
        runCurrent()

        /** When **/
        job.cancel()
        job.join()

        /** Then **/
        assertNull(result)
        assertTrue(thrown is CancellationException)
        assertTrue(job.isCancelled)
    }

    /**
     * 블록 안쪽 withTimeout의 시간 초과는 바깥 코루틴의 취소가 아니라 실패 결과인지 검증
     *
     * Given: 10ms 제한 안에서 1초를 기다리는 블록
     * When: runSuspendCatching 호출
     * Then: TimeoutCancellationException을 담은 실패 결과, 부른 코루틴은 살아 있음
     */
    @Test
    fun test_04_innerTimeoutIsFailure() = runTest {
        /** Given / When **/
        val result = runSuspendCatching {
            withTimeout(10.milliseconds) { delay(1.seconds) }
        }

        /** Then **/
        assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
        assertTrue(currentCoroutineContext().isActive)
    }

    /**
     * 취소된 코루틴이라도 NonCancellable 블록 안에서 난 취소 예외는 실패 결과인지 검증
     *
     * Given: 취소될 때까지 기다리다 정리 단계에서 NonCancellable로 감싼 블록이 CancellationException을 던지는 자식 코루틴
     * When: 그 자식 코루틴 취소
     * Then: 정리 단계의 결과는 CancellationException을 담은 실패 결과
     */
    @Test
    fun test_05_cancellationInsideNonCancellableIsFailure() = runTest {
        /** Given **/
        val writeCancelled = CancellationException("write cancelled")
        var result: Result<Unit>? = null
        val job = launch {
            try {
                awaitCancellation()
            } finally {
                result = withContext(NonCancellable) {
                    runSuspendCatching { throw writeCancelled }
                }
            }
        }
        // 취소 대기에 들어갈 때까지 실행
        runCurrent()

        /** When **/
        job.cancel()
        job.join()

        /** Then **/
        assertSame(writeCancelled, result?.exceptionOrNull())
    }

    /**
     * Error는 실패 결과로 담지 않고 그대로 던지는지 검증
     *
     * Given: OutOfMemoryError를 던지는 블록
     * When: runSuspendCatching 호출
     * Then: 결과 없이 같은 OutOfMemoryError가 던져짐
     */
    @Test
    fun test_06_errorIsNotCaught() = runTest {
        /** Given **/
        val error = OutOfMemoryError("test")
        var result: Result<Unit>? = null

        /** When **/
        val thrown = try {
            result = runSuspendCatching { throw error }
            null
        } catch (caught: OutOfMemoryError) {
            caught
        }

        /** Then **/
        assertNull(result)
        assertSame(error, thrown)
    }
}
