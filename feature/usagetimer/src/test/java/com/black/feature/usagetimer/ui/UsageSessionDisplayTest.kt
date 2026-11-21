package com.black.feature.usagetimer.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.black.core.system.AppUsageSession
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/** [buildAppUsageDisplay]의 화면 숫자 일치(줄, 날짜 소계, 합계)와 날짜 묶기, [formatUsageMinutes] 서식 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageSessionDisplayTest : BaseTest() {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val originalTimeZone = TimeZone.getDefault()

    @Before
    fun setupTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    /** 2026-11-[day] [hour]:[minute]:[second] 서울 시각(밀리초) */
    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute, second).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()

    /** 조회 시각 2026-11-21 22:20 */
    private val queriedAt get() = at(21, 22, 20)

    /**
     * 줄 사용 시간이 보이는 시각의 차이인지 검증
     *
     * Given: 21:40:50~22:15:10(실제 34분 20초)
     * When: 표시 모델 변환
     * Then: "21:40" ~ "22:15", 35분
     */
    @Test
    fun test_01_rowMinutesFollowShownClock() = runTest {
        /** Given **/
        val sessions = listOf(AppUsageSession(at(21, 21, 40, 50), at(21, 22, 15, 10)))

        /** When **/
        val row = buildAppUsageDisplay(sessions, queriedAt).groups.single().rows.single()

        /** Then **/
        assertEquals("21:40", row.startText)
        assertEquals("22:15", row.endText)
        assertEquals(35L, row.minutes)
    }

    /**
     * 1분 50초 구간 3개의 줄 합과 합계가 같은지 검증
     *
     * Given: 10:00:05~10:01:55, 11:00:05~11:01:55, 12:00:05~12:01:55
     * When: 표시 모델 변환
     * Then: 줄마다 1분, 합계 3분
     */
    @Test
    fun test_02_totalEqualsSumOfRows() = runTest {
        /** Given **/
        val sessions = listOf(10, 11, 12).map { hour -> AppUsageSession(at(21, hour, 0, 5), at(21, hour, 1, 55)) }

        /** When **/
        val display = buildAppUsageDisplay(sessions, queriedAt)

        /** Then **/
        assertEquals(listOf(1L, 1L, 1L), display.groups.single().rows.map { it.minutes })
        assertEquals(3L, display.totalMinutes)
    }

    /**
     * 같은 분 안의 구간은 시각 하나와 0분인지 검증
     *
     * Given: 22:10:10~22:10:50
     * When: 표시 모델 변환
     * Then: 끝 시각 없음, 0분, 구간 있음
     */
    @Test
    fun test_03_sameMinuteShowsSingleClock() = runTest {
        /** Given **/
        val sessions = listOf(AppUsageSession(at(21, 22, 10, 10), at(21, 22, 10, 50)))

        /** When **/
        val display = buildAppUsageDisplay(sessions, queriedAt)

        /** Then **/
        val row = display.groups.single().rows.single()
        assertEquals("22:10", row.startText)
        assertNull(row.endText)
        assertEquals(0L, row.minutes)
        assertTrue(display.hasSessions)
    }

    /**
     * 시작한 날짜로 묶고 최신 순이며 날짜 소계의 합이 합계인지 검증
     *
     * Given: 어제 23:10~오늘 00:12, 오늘 08:05~08:20, 어제 22:50~22:51
     * When: 표시 모델 변환
     * Then: [오늘 15분: 08:05], [어제 63분: 23:10, 22:50], 합계 78분
     */
    @Test
    fun test_04_groupsByStartDayNewestFirst() = runTest {
        /** Given **/
        val sessions = listOf(
            AppUsageSession(at(20, 23, 10), at(21, 0, 12)),
            AppUsageSession(at(21, 8, 5), at(21, 8, 20)),
            AppUsageSession(at(20, 22, 50), at(20, 22, 51)),
        )

        /** When **/
        val display = buildAppUsageDisplay(sessions, queriedAt)

        /** Then **/
        assertEquals(listOf(UsageDay.TODAY, UsageDay.YESTERDAY), display.groups.map { it.day })
        assertEquals(listOf(15L, 63L), display.groups.map { it.totalMinutes })
        assertEquals(listOf("23:10", "22:50"), display.groups[1].rows.map { it.startText })
        assertEquals("00:12", display.groups[1].rows[0].endText)
        assertEquals(78L, display.totalMinutes)
    }

    /**
     * 기간 경계 잘림과 진행 중 표시가 줄에 그대로 전달되는지 검증
     *
     * Given: 잘린 구간 어제 22:20~22:46, 진행 중 구간 오늘 21:58~22:20
     * When: 표시 모델 변환
     * Then: 잘린 줄 startsBeforePeriod, 진행 중 줄 inProgress, 22분
     */
    @Test
    fun test_05_flagsPassThrough() = runTest {
        /** Given **/
        val sessions = listOf(
            AppUsageSession(at(20, 22, 20), at(20, 22, 46), startsBeforePeriod = true),
            AppUsageSession(at(21, 21, 58), at(21, 22, 20), inProgress = true),
        )

        /** When **/
        val display = buildAppUsageDisplay(sessions, queriedAt)

        /** Then **/
        val inProgressRow = display.groups[0].rows.single()
        val clippedRow = display.groups[1].rows.single()
        assertTrue(inProgressRow.inProgress)
        assertEquals(22L, inProgressRow.minutes)
        assertTrue(clippedRow.startsBeforePeriod)
    }

    /**
     * 최신 5줄만 남겨도 날짜 소계는 숨긴 줄을 포함하는지 검증
     *
     * Given: 오늘 4구간(각 10분), 어제 3구간(각 10분)
     * When: visibleGroups(5)
     * Then: 오늘 4줄, 어제 1줄, 어제 소계 30분 유지
     */
    @Test
    fun test_06_visibleGroupsKeepDayTotals() = runTest {
        /** Given **/
        val today = listOf(8, 10, 12, 14).map { hour -> AppUsageSession(at(21, hour, 0), at(21, hour, 10)) }
        val yesterday = listOf(23, 22, 21).map { hour -> AppUsageSession(at(20, hour, 30), at(20, hour, 40)) }
        val display = buildAppUsageDisplay(today + yesterday, queriedAt)

        /** When **/
        val visible = display.visibleGroups(5)

        /** Then **/
        assertEquals(listOf(4, 1), visible.map { it.rows.size })
        assertEquals(30L, visible[1].totalMinutes)
        assertEquals(7, display.rowCount)
    }

    /**
     * 분 단위 사용 시간 문구 서식 검증
     *
     * Given: 0분(구간 없음), 0분(구간 있음), 12분, 60분, 65분
     * When: formatUsageMinutes
     * Then: "0분", "1분 미만", "12분", "1시간 0분", "1시간 5분"
     */
    @Test
    fun test_07_formatUsageMinutes() = runTest {
        /** When / Then **/
        assertEquals("0분", formatUsageMinutes(resources, 0L, hasSessions = false))
        assertEquals("1분 미만", formatUsageMinutes(resources, 0L, hasSessions = true))
        assertEquals("12분", formatUsageMinutes(resources, 12L, hasSessions = true))
        assertEquals("1시간 0분", formatUsageMinutes(resources, 60L, hasSessions = true))
        assertEquals("1시간 5분", formatUsageMinutes(resources, 65L, hasSessions = true))
    }
}
