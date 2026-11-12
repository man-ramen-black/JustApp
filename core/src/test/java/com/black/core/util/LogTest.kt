package com.black.core.util

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [Log]의 긴 로그 분할 검증 */
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
}
