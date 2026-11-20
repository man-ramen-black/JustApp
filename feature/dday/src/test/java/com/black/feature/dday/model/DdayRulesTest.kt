package com.black.feature.dday.model

import com.black.core.system.CalendarEventInstance
import com.black.test.BaseTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** [ddayLabel], [eventStartDate], [formatEventDate], [selectUpcoming], [queryRange], [DdayStyle]의 남은 날짜 규칙 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DdayRulesTest : BaseTest() {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 20)

    /** [zone] 기준 [date] [hour]시 시작의 epoch 밀리초 */
    private fun millis(date: LocalDate, hour: Int, zone: ZoneId = seoul) =
        ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant().toEpochMilli()

    /** [date] UTC 자정 epoch 밀리초, 종일 일정의 저장 방식 */
    private fun utcMidnight(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun timed(eventId: Long, date: LocalDate, hour: Int = 9, calendarId: Long = 1, title: String? = "t$eventId") =
        CalendarEventInstance(eventId, calendarId, title, millis(date, hour), millis(date, hour + 1), false)

    private fun allDay(eventId: Long, date: LocalDate, title: String? = "t$eventId") =
        CalendarEventInstance(eventId, 1, title, utcMidnight(date), utcMidnight(date.plusDays(1)), true)

    /**
     * 당일 일정의 남은 날짜가 D-Day인지 검증
     *
     * Given: 시작일과 오늘이 같음
     * When: ddayLabel 호출
     * Then: "D-Day"
     */
    @Test
    fun test_01_labelIsDdayOnSameDay() {
        /** Given / When **/
        val label = ddayLabel(today, today)

        /** Then **/
        assertEquals("D-Day", label)
    }

    /**
     * 남은 날짜가 일수로 세어지는지 검증
     *
     * Given: 9일, 365일 뒤 시작일
     * When: ddayLabel 호출
     * Then: "D-9", "D-365"
     */
    @Test
    fun test_02_labelCountsRemainingDays() {
        /** Given / When **/
        val nineDays = ddayLabel(LocalDate.of(2026, 11, 29), today)
        val yearLater = ddayLabel(LocalDate.of(2027, 11, 20), today)

        /** Then **/
        assertEquals("D-9", nineDays)
        assertEquals("D-365", yearLater)
    }

    /**
     * 종일 일정이 양의 오프셋 시간대에서도 UTC 날짜를 쓰는지 검증
     *
     * Given: 2022-09-04 00:00 UTC 종일 일정
     * When: 서울 기준 eventStartDate 호출
     * Then: 2022-09-04
     */
    @Test
    fun test_03_allDayStartUsesUtcDateInPositiveZone() {
        /** Given / When **/
        val date = eventStartDate(utcMidnight(LocalDate.of(2022, 9, 4)), true, seoul)

        /** Then **/
        assertEquals(LocalDate.of(2022, 9, 4), date)
    }

    /**
     * 종일 일정이 음의 오프셋 시간대에서도 하루 앞당겨지지 않는지 검증
     *
     * Given: 2022-09-04 00:00 UTC 종일 일정
     * When: 뉴욕 기준 eventStartDate 호출
     * Then: 2022-09-04
     */
    @Test
    fun test_04_allDayStartUsesUtcDateInNegativeZone() {
        /** Given / When **/
        val date = eventStartDate(utcMidnight(LocalDate.of(2022, 9, 4)), true, ZoneId.of("America/New_York"))

        /** Then **/
        assertEquals(LocalDate.of(2022, 9, 4), date)
    }

    /**
     * 시각 일정이 지정한 시간대의 날짜를 쓰는지 검증
     *
     * Given: 2022-09-08 23:30 UTC 시각 일정
     * When: 서울 기준 eventStartDate 호출
     * Then: 2022-09-09
     */
    @Test
    fun test_05_timedStartUsesLocalZone() {
        /** Given **/
        val begin = Instant.parse("2022-09-08T23:30:00Z").toEpochMilli()

        /** When **/
        val date = eventStartDate(begin, false, seoul)

        /** Then **/
        assertEquals(LocalDate.of(2022, 9, 9), date)
    }

    /**
     * 오늘 이미 시작한 일정이 시각과 무관하게 남는지 검증
     *
     * Given: 오늘 09:00(서울) 시작 일정
     * When: selectUpcoming 호출
     * Then: 일정이 남고 라벨은 "D-Day"
     */
    @Test
    fun test_06_passedTimeTodayStillIncluded() {
        /** Given **/
        val instance = timed(1, today, hour = 9)

        /** When **/
        val result = selectUpcoming(listOf(instance), emptySet(), today, seoul)

        /** Then **/
        assertEquals(listOf(instance), result)
        assertEquals("D-Day", ddayLabel(eventStartDate(result[0].begin, result[0].allDay, seoul), today))
    }

    /**
     * 오늘 이전에 시작한 일정이 빠지는지 검증
     *
     * Given: 어제 시작해 내일 끝나는 일정
     * When: selectUpcoming 호출
     * Then: 빈 목록
     */
    @Test
    fun test_07_startedBeforeTodayExcluded() {
        /** Given **/
        val instance = CalendarEventInstance(
            1, 1, "t", millis(today.minusDays(1), 9), millis(today.plusDays(1), 9), false,
        )

        /** When **/
        val result = selectUpcoming(listOf(instance), emptySet(), today, seoul)

        /** Then **/
        assertEquals(emptyList<CalendarEventInstance>(), result)
    }

    /**
     * 조회 기간 365일 경계를 검증
     *
     * Given: 365일 뒤와 366일 뒤 일정
     * When: selectUpcoming 호출
     * Then: 365일 뒤 일정만 남음
     */
    @Test
    fun test_08_lookaheadBoundary() {
        /** Given **/
        val inside = timed(1, today.plusDays(365))
        val outside = timed(2, today.plusDays(366))

        /** When **/
        val result = selectUpcoming(listOf(inside, outside), emptySet(), today, seoul)

        /** Then **/
        assertEquals(listOf(inside), result)
    }

    /**
     * 반복 일정이 가장 가까운 회차 하나만 남는지 검증
     *
     * Given: 같은 eventId의 3회차
     * When: selectUpcoming 호출
     * Then: 가장 이른 회차 하나
     */
    @Test
    fun test_09_recurringKeepsNearestInstance() {
        /** Given **/
        val third = timed(1, today.plusDays(14))
        val first = timed(1, today.plusDays(2))
        val second = timed(1, today.plusDays(7))

        /** When **/
        val result = selectUpcoming(listOf(third, first, second), emptySet(), today, seoul)

        /** Then **/
        assertEquals(listOf(first), result)
    }

    /**
     * 제외한 캘린더의 일정이 빠지는지 검증
     *
     * Given: 캘린더 1, 2의 일정과 제외 목록 {2}
     * When: selectUpcoming 호출
     * Then: 캘린더 1의 일정만 남음
     */
    @Test
    fun test_10_excludedCalendarFiltered() {
        /** Given **/
        val kept = timed(1, today.plusDays(1), calendarId = 1)
        val dropped = timed(2, today.plusDays(1), calendarId = 2)

        /** When **/
        val result = selectUpcoming(listOf(kept, dropped), setOf(2L), today, seoul)

        /** Then **/
        assertEquals(listOf(kept), result)
    }

    /**
     * 정렬 순서를 검증
     *
     * Given: 시작일, 종일 여부, begin, 제목이 섞인 일정(제목 null 포함)
     * When: selectUpcoming 호출
     * Then: 시작일, 종일 먼저, begin, 제목(null은 빈 문자열) 순
     */
    @Test
    fun test_11_sortOrder() {
        /** Given **/
        val later = timed(1, today.plusDays(3), hour = 8)
        val timedNine = timed(2, today.plusDays(1), hour = 9, title = "b")
        val timedEightB = timed(3, today.plusDays(1), hour = 8, title = "b")
        val timedEightA = timed(4, today.plusDays(1), hour = 8, title = "a")
        val timedEightNull = timed(5, today.plusDays(1), hour = 8, title = null)
        val allDayItem = allDay(6, today.plusDays(1), title = "z")

        /** When **/
        val result = selectUpcoming(
            listOf(later, timedNine, timedEightB, timedEightA, timedEightNull, allDayItem), emptySet(), today, seoul,
        )

        /** Then **/
        assertEquals(listOf(allDayItem, timedEightNull, timedEightA, timedEightB, timedNine, later), result)
    }

    /**
     * 종일 일정 표기가 날짜만인지 검증
     *
     * Given: 2022-09-04 종일 일정
     * When: formatEventDate 호출
     * Then: "9/4 Sun"
     */
    @Test
    fun test_12_formatAllDayDateOnly() {
        /** Given / When **/
        val text = formatEventDate(utcMidnight(LocalDate.of(2022, 9, 4)), true, seoul)

        /** Then **/
        assertEquals("9/4 Sun", text)
    }

    /**
     * 시각 일정 표기가 오전·오후를 붙이는지 검증
     *
     * Given: 2022-09-09 15:00, 00:00(서울) 일정
     * When: formatEventDate 호출
     * Then: "9/9 Fri PM 3:00", "9/9 Fri AM 12:00"
     */
    @Test
    fun test_13_formatTimedWithMeridiem() {
        /** Given **/
        val date = LocalDate.of(2022, 9, 9)

        /** When **/
        val afternoon = formatEventDate(millis(date, 15), false, seoul)
        val midnight = formatEventDate(millis(date, 0), false, seoul)

        /** Then **/
        assertEquals("9/9 Fri PM 3:00", afternoon)
        assertEquals("9/9 Fri AM 12:00", midnight)
    }

    /**
     * 조회 범위가 앞뒤 하루 여유를 두는지 검증
     *
     * Given: 오늘 2026-11-20, 서울
     * When: queryRange 호출
     * Then: 오늘 0시의 하루 전부터 오늘 + 366일 0시의 하루 뒤까지
     */
    @Test
    fun test_14_queryRangeCoversMargin() {
        /** Given / When **/
        val range = queryRange(today, seoul)

        /** Then **/
        assertEquals(today.minusDays(1).atStartOfDay(seoul).toInstant().toEpochMilli(), range.first)
        assertEquals(today.plusDays(367).atStartOfDay(seoul).toInstant().toEpochMilli(), range.last)
    }

    /**
     * 스타일 값이 범위로 맞춰지는지 검증
     *
     * Given: 기본 스타일
     * When: 범위 밖 값으로 with 호출
     * Then: 최댓값 48, 최솟값 0
     */
    @Test
    fun test_15_styleWithClampsToRange() {
        /** Given **/
        val style = DdayStyle()

        /** When **/
        val high = style.with(DdayStyleField.LABEL_TEXT_SIZE, 100)
        val low = style.with(DdayStyleField.ITEM_GAP, -3)

        /** Then **/
        assertEquals(48, high[DdayStyleField.LABEL_TEXT_SIZE])
        assertEquals(0, low[DdayStyleField.ITEM_GAP])
    }

    /**
     * 일정이 최대 개수보다 많으면 가까운 일정부터 최대 개수만 남는지 검증
     *
     * Given: 오늘부터 하루씩 시작하는 일정 DDAY_MAX_ITEMS + 10개를 역순으로 넣음
     * When: selectUpcoming 호출
     * Then: DDAY_MAX_ITEMS개이고 첫 일정은 오늘, 마지막 일정은 DDAY_MAX_ITEMS - 1일 뒤
     */
    @Test
    fun test_16_selectUpcomingKeepsNearestMaxItems() {
        /** Given **/
        val instances = (0 until DDAY_MAX_ITEMS + 10).map { offset ->
            timed(eventId = offset.toLong(), date = today.plusDays(offset.toLong()))
        }.reversed()

        /** When **/
        val selected = selectUpcoming(instances, emptySet(), today, seoul)

        /** Then **/
        assertEquals(DDAY_MAX_ITEMS, selected.size)
        assertEquals(0L, selected.first().eventId)
        assertEquals((DDAY_MAX_ITEMS - 1).toLong(), selected.last().eventId)
    }

    /**
     * 위젯에 보이는 일정 수가 기본 3개이고 1부터 DDAY_MAX_ITEMS 사이로 맞춰지는지 검증
     *
     * Given: 기본 스타일
     * When: 일정 개수를 0과 DDAY_MAX_ITEMS + 1로 with 호출
     * Then: 기본값 3, 최솟값 1, 최댓값 DDAY_MAX_ITEMS
     */
    @Test
    fun test_17_itemCountDefaultsToThreeAndClamps() {
        /** Given **/
        val style = DdayStyle()

        /** When **/
        val low = style.with(DdayStyleField.ITEM_COUNT, 0)
        val high = style.with(DdayStyleField.ITEM_COUNT, DDAY_MAX_ITEMS + 1)

        /** Then **/
        assertEquals(3, style.itemCount)
        assertEquals(1, low.itemCount)
        assertEquals(DDAY_MAX_ITEMS, high.itemCount)
    }
}
