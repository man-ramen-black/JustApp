package com.black.feature.usagetimer.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [formatUsageDuration]의 사용 시간 문구 서식 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageDurationFormatTest : BaseTest() {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    /**
     * 사용 시간 0이 "0분"으로 표시되는지 검증
     *
     * Given:
     * - 사용 시간 0
     * When:
     * - 서식 변환
     * Then:
     * - "0분"
     */
    @Test
    fun test_01_zeroIsZeroMinutes() = runTest {
        /** Given **/
        val usageMillis = 0L

        /** When **/
        val result = formatUsageDuration(resources, usageMillis)

        /** Then **/
        assertEquals("0분", result)
    }

    /**
     * 0보다 크고 1분 미만이면 "1분 미만"으로 표시되는지 검증
     *
     * Given:
     * - 사용 시간 1밀리초와 59,999밀리초
     * When:
     * - 서식 변환
     * Then:
     * - 둘 다 "1분 미만"
     */
    @Test
    fun test_02_underMinute() = runTest {
        /** Given **/
        val lowerBound = 1L
        val upperBound = 59_999L

        /** When **/
        val lowerResult = formatUsageDuration(resources, lowerBound)
        val upperResult = formatUsageDuration(resources, upperBound)

        /** Then **/
        assertEquals("1분 미만", lowerResult)
        assertEquals("1분 미만", upperResult)
    }

    /**
     * 1시간 미만이면 분 단위로 내림해 표시되는지 검증
     *
     * Given:
     * - 사용 시간 60,000밀리초와 3,599,999밀리초
     * When:
     * - 서식 변환
     * Then:
     * - "1분"과 "59분"
     */
    @Test
    fun test_03_minutesFloored() = runTest {
        /** Given **/
        val oneMinute = 60_000L
        val justUnderHour = 3_599_999L

        /** When **/
        val oneMinuteResult = formatUsageDuration(resources, oneMinute)
        val justUnderHourResult = formatUsageDuration(resources, justUnderHour)

        /** Then **/
        assertEquals("1분", oneMinuteResult)
        assertEquals("59분", justUnderHourResult)
    }

    /**
     * 1시간 이상이면 시간과 분으로 표시되는지 검증
     *
     * Given:
     * - 사용 시간 1시간, 1시간 5분, 25시간
     * When:
     * - 서식 변환
     * Then:
     * - "1시간 0분", "1시간 5분", "25시간 0분"
     */
    @Test
    fun test_04_hoursAndMinutes() = runTest {
        /** Given **/
        val oneHour = 3_600_000L
        val oneHourFiveMinutes = 3_900_000L
        val twentyFiveHours = 90_000_000L

        /** When **/
        val oneHourResult = formatUsageDuration(resources, oneHour)
        val oneHourFiveResult = formatUsageDuration(resources, oneHourFiveMinutes)
        val twentyFiveResult = formatUsageDuration(resources, twentyFiveHours)

        /** Then **/
        assertEquals("1시간 0분", oneHourResult)
        assertEquals("1시간 5분", oneHourFiveResult)
        assertEquals("25시간 0분", twentyFiveResult)
    }
}
