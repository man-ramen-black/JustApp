package com.black.core.util

import com.black.test.BaseTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import android.util.Log as AndroidLog

/** [Log]의 긴 로그 분할과 예외 스택 트레이스 출력 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LogTest : BaseTest() {

    /**
     * 길이가 분할 단위의 배수여도 빈 조각을 더 만들지 않는지 검증
     *
     * Given: 분할 단위와 같은 길이, 두 배 길이의 메시지
     * When: splitLog 호출
     * Then: 조각 수가 각각 1, 2
     */
    @Test
    fun test_01_exactMultipleHasNoEmptyChunk() = runTest {
        /** Given **/
        val single = "a".repeat(Log.LOG_SPLIT_LENGTH)
        val double = "a".repeat(Log.LOG_SPLIT_LENGTH * 2)

        /** When / Then **/
        assertEquals(1, Log.splitLog(single).size)
        assertEquals(2, Log.splitLog(double).size)
    }

    /**
     * 분할 단위를 넘는 메시지와 빈 메시지 분할 검증
     *
     * Given: 분할 단위보다 1 긴 메시지, 빈 메시지
     * When: splitLog 호출
     * Then: 앞 조각은 분할 단위 길이, 마지막 조각은 1자 / 빈 메시지는 빈 조각 하나
     */
    @Test
    fun test_02_remainderAndEmpty() = runTest {
        /** Given **/
        val message = "a".repeat(Log.LOG_SPLIT_LENGTH + 1)

        /** When **/
        val chunks = Log.splitLog(message)

        /** Then **/
        assertEquals(listOf(Log.LOG_SPLIT_LENGTH, 1), chunks.map { it.length })
        assertEquals(listOf(""), Log.splitLog(""))
    }

    /**
     * Throwable 오버로드가 원인 예외까지 포함한 스택 트레이스 전체를 출력하는지 검증
     *
     * Given: 짧은 스택을 가진 예외와 원인 예외
     * When: Log.w(throwable) 호출
     * Then: android Log.w 메시지에 스택 트레이스 전체(호출 위치 줄, Caused by 줄) 포함
     */
    @Test
    fun test_03_throwablePrintsStackTrace() = runTest {
        /** Given **/
        val frame = StackTraceElement("com.example.Sample", "run", "Sample.kt", 10)
        val cause = IllegalArgumentException("cause").apply { stackTrace = arrayOf(frame) }
        val exception = IllegalStateException("failed", cause).apply { stackTrace = arrayOf(frame) }
        val messages = mutableListOf<String>()

        /** When **/
        Log.w(exception)

        /** Then **/
        verify { AndroidLog.w(any(), capture(messages)) }
        val message = messages.single()
        assertTrue(message.contains(exception.stackTraceToString()))
        assertTrue(message.contains("\tat com.example.Sample.run(Sample.kt:10)"))
        assertTrue(message.contains("Caused by: java.lang.IllegalArgumentException: cause"))
    }

    /**
     * Throwable 오버로드가 같은 단계의 android Log 함수로 출력하는지 검증
     *
     * Given: 짧은 스택을 가진 예외
     * When: v, i, d, w, e Throwable 오버로드 호출
     * Then: android Log의 같은 단계 함수가 스택 트레이스를 포함한 메시지로 한 번씩 호출
     */
    @Test
    fun test_04_throwableUsesSameLevel() = runTest {
        /** Given **/
        val exception = IllegalStateException("failed")
            .apply { stackTrace = arrayOf(StackTraceElement("com.example.Sample", "run", "Sample.kt", 10)) }
        val stackTrace = exception.stackTraceToString()

        /** When **/
        Log.v(exception)
        Log.i(exception)
        Log.d(exception)
        Log.w(exception)
        Log.e(exception)

        /** Then **/
        verify(exactly = 1) {
            AndroidLog.v(any(), match<String> { it.contains(stackTrace) })
            AndroidLog.i(any(), match<String> { it.contains(stackTrace) })
            AndroidLog.d(any(), match<String> { it.contains(stackTrace) })
            AndroidLog.w(any(), match<String> { it.contains(stackTrace) })
            AndroidLog.e(any(), match<String> { it.contains(stackTrace) })
        }
    }
}
