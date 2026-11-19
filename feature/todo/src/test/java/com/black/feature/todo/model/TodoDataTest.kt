package com.black.feature.todo.model

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** [TodoDue.localDate], [TodoDue.comparator], [sortedByDue]의 기한 날짜 판정과 정렬 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TodoDataTest : BaseTest() {

    private val zone = ZoneId.of("Asia/Seoul")

    /** [zone] 기준 2026-11-18 [hour]시의 시각 기한 */
    private fun dateTimeDue(hour: Int) =
        TodoDue.DateTime(ZonedDateTime.of(LocalDate.of(2026, 11, 18), LocalTime.of(hour, 0), zone).toInstant())

    private fun task(pageId: String, due: TodoDue?) =
        Todo(pageId, pageId, false, due, TodoReminder.NONE, "", Instant.EPOCH, "")

    /**
     * 시각 기한이 시간대 기준 날짜로, 날짜 기한은 그대로 나오는지 검증
     *
     * Given: UTC 2026-11-17T16:00Z(서울 11-18 01:00) 시각 기한과 2026-11-19 날짜 기한
     * When: 서울 기준 localDate 호출
     * Then: 2026-11-18, 2026-11-19
     */
    @Test
    fun test_01_localDateUsesZoneForDateTimeOnly() = runTest {
        /** Given / When **/
        val dateTime = TodoDue.DateTime(Instant.parse("2026-11-17T16:00:00Z")).localDate(zone)
        val date = TodoDue.Date(LocalDate.of(2026, 11, 19)).localDate(zone)

        /** Then **/
        assertEquals(LocalDate.of(2026, 11, 18), dateTime)
        assertEquals(LocalDate.of(2026, 11, 19), date)
    }

    /**
     * 같은 시각 기한이 시간대마다 다른 날짜가 되고 날짜 기한은 시간대와 무관한지 검증
     *
     * Given: UTC 2026-11-17T16:00Z 시각 기한과 2026-11-19 날짜 기한
     * When: 서울, UTC, 뉴욕 기준 localDate 호출
     * Then: 시각 기한은 서울 11-18, UTC 11-17, 뉴욕 11-17이고 날짜 기한은 모두 11-19
     */
    @Test
    fun test_02_localDateFollowsZone() = runTest {
        /** Given **/
        val dateTime = TodoDue.DateTime(Instant.parse("2026-11-17T16:00:00Z"))
        val date = TodoDue.Date(LocalDate.of(2026, 11, 19))
        val zones = listOf(zone, ZoneOffset.UTC, ZoneId.of("America/New_York"))

        /** When **/
        val dateTimeDates = zones.map { dateTime.localDate(it) }
        val dateDates = zones.map { date.localDate(it) }

        /** Then **/
        assertEquals(listOf(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 17)), dateTimeDates)
        assertEquals(List(3) { LocalDate.of(2026, 11, 19) }, dateDates)
    }

    /**
     * 기한 비교가 날짜, 날짜만 있는 기한 먼저, 시각 순인지 검증
     *
     * Given: 같은 날 18:00, 09:00, 날짜만, 그리고 다음 날 날짜만
     * When: comparator로 정렬
     * Then: 같은 날 날짜만, 09:00, 18:00, 다음 날
     */
    @Test
    fun test_03_comparatorOrdersByDateThenKindThenTime() = runTest {
        /** Given **/
        val late = dateTimeDue(18)
        val early = dateTimeDue(9)
        val date = TodoDue.Date(LocalDate.of(2026, 11, 18))
        val next = TodoDue.Date(LocalDate.of(2026, 11, 19))

        /** When **/
        val sorted = listOf(next, late, early, date).sortedWith(TodoDue.comparator(zone))

        /** Then **/
        assertEquals(listOf(date, early, late, next), sorted)
    }

    /**
     * Todo를 기한 비교 순서로 정렬하는지 검증
     *
     * Given: 다음 날 날짜만, 같은 날 18:00, 09:00, 날짜만 기한의 Todo
     * When: sortedByDue 호출
     * Then: 같은 날 날짜만, 09:00, 18:00, 다음 날 순서의 page ID
     */
    @Test
    fun test_04_sortedByDueOrdersTasksByComparator() = runTest {
        /** Given **/
        val tasks = listOf(
            task("next", TodoDue.Date(LocalDate.of(2026, 11, 19))),
            task("late", dateTimeDue(18)),
            task("early", dateTimeDue(9)),
            task("date", TodoDue.Date(LocalDate.of(2026, 11, 18))),
        )

        /** When **/
        val sorted = tasks.sortedByDue(zone)

        /** Then **/
        assertEquals(listOf("date", "early", "late", "next"), sorted.map { it.pageId })
    }

    /**
     * 기한 없는 Todo가 섞이면 전제 조건 위반으로 예외를 던지는지 검증
     *
     * Given: 날짜만 기한의 Todo와 기한 없는 Todo
     * When: sortedByDue 호출
     * Then: IllegalArgumentException
     */
    @Test
    fun test_05_sortedByDueRejectsTaskWithoutDue() = runTest {
        /** Given **/
        val tasks = listOf(
            task("due", TodoDue.Date(LocalDate.of(2026, 11, 18))),
            task("noDue", null),
        )

        /** When / Then **/
        assertThrows(IllegalArgumentException::class.java) { tasks.sortedByDue(zone) }
    }
}
