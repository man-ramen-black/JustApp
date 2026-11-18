package com.black.app.assistanttask

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.TimeZone

/** [AssistantTaskDates]의 날짜 판정 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssistantTaskDatesTest : BaseTest() {

    private val zone = TimeZone.getTimeZone("Asia/Seoul")

    /**
     * 날짜와 자정부터의 분을 시간대 기준 epoch 밀리초로 바꾸는지 검증
     *
     * Given: 2026-11-20, 570분(09:30), 서울 시간대
     * When: dateTimeMillis 호출
     * Then: 1795134600000
     */
    @Test
    fun test_01_dateTimeMillisConvertsInZone() = runTest {
        /** Given / When **/
        val millis = AssistantTaskDates.dateTimeMillis("2026-11-20", 570, zone)

        /** Then **/
        assertEquals(1795134600000L, millis)
    }

    /**
     * 존재하지 않는 날짜면 null인지 검증
     *
     * Given: 13월 날짜
     * When: dateTimeMillis 호출
     * Then: null
     */
    @Test
    fun test_02_dateTimeMillisInvalidDateIsNull() = runTest {
        /** Given / When **/
        val millis = AssistantTaskDates.dateTimeMillis("2026-13-01", 0, zone)

        /** Then **/
        assertNull(millis)
    }

    /**
     * 서머타임이 시작되는 날에도 고른 벽시계 시각으로 바꾸는지 검증
     *
     * Given: 2026-03-08(뉴욕 서머타임 시작일, 02:00→03:00), 210분(03:30), America/New_York 시간대
     * When: dateTimeMillis 호출
     * Then: 03:30 EDT인 2026-03-08T07:30:00Z = 1772955000000
     */
    @Test
    fun test_03_dateTimeMillisOnDaylightSavingStart() = runTest {
        /** Given / When **/
        val millis = AssistantTaskDates.dateTimeMillis("2026-03-08", 210, TimeZone.getTimeZone("America/New_York"))

        /** Then **/
        assertEquals(1772955000000L, millis)
    }

    /**
     * 시각 기한이 시간대 기준 날짜로, 날짜 기한은 그대로 나오는지 검증
     *
     * Given: UTC 2026-11-17T16:00Z(서울 11-18 01:00) 시각 기한과 2026-11-19 날짜 기한
     * When: localDateOf 호출
     * Then: 2026-11-18, 2026-11-19
     */
    @Test
    fun test_04_localDateOfUsesZoneForDateTimeOnly() = runTest {
        /** Given / When **/
        val dateTime = AssistantTaskDates.localDateOf(AssistantTaskDue.DateTime(1_794_931_200_000L), zone)
        val dateOnly = AssistantTaskDates.localDateOf(AssistantTaskDue.DateOnly("2026-11-19"), zone)

        /** Then **/
        assertEquals("2026-11-18", dateTime)
        assertEquals("2026-11-19", dateOnly)
    }

    /**
     * epoch 밀리초를 시간대 기준 날짜로 바꾸는지 검증
     *
     * Given: UTC 2026-11-17T16:00Z
     * When: formatDate 호출(서울, UTC)
     * Then: 서울은 2026-11-18, UTC는 2026-11-17
     */
    @Test
    fun test_05_formatDateFollowsZone() = runTest {
        /** Given / When / Then **/
        assertEquals("2026-11-18", AssistantTaskDates.formatDate(1_794_931_200_000L, zone))
        assertEquals("2026-11-17", AssistantTaskDates.formatDate(1_794_931_200_000L, TimeZone.getTimeZone("UTC")))
    }

    /**
     * 기한 비교가 날짜, 날짜만 있는 기한 먼저, 시각 순인지 검증
     *
     * Given: 같은 날 18:00, 09:00, 날짜만, 그리고 다음 날 날짜만
     * When: dueComparator로 정렬
     * Then: 같은 날 날짜만, 09:00, 18:00, 다음 날
     */
    @Test
    fun test_06_dueComparatorOrdersByDateThenKindThenTime() = runTest {
        /** Given **/
        val late = AssistantTaskDue.DateTime(AssistantTaskDates.dateTimeMillis("2026-11-18", 18 * 60, zone)!!)
        val early = AssistantTaskDue.DateTime(AssistantTaskDates.dateTimeMillis("2026-11-18", 9 * 60, zone)!!)
        val dateOnly = AssistantTaskDue.DateOnly("2026-11-18")
        val next = AssistantTaskDue.DateOnly("2026-11-19")

        /** When **/
        val sorted = listOf(next, late, early, dateOnly).sortedWith(AssistantTaskDates.dueComparator(zone))

        /** Then **/
        assertEquals(listOf(dateOnly, early, late, next), sorted)
    }

    /**
     * Todo를 기한 비교 순서로 정렬하는지 검증
     *
     * Given: 다음 날 날짜만, 같은 날 18:00, 09:00, 날짜만 기한의 Todo
     * When: sortedByDue 호출
     * Then: 같은 날 날짜만, 09:00, 18:00, 다음 날 순서의 page ID
     */
    @Test
    fun test_07_sortedByDueOrdersTasksByDueComparator() = runTest {
        /** Given **/
        fun task(pageId: String, due: AssistantTaskDue) =
            AssistantTask(pageId, pageId, AssistantTaskKind.TASK, false, due, AssistantTaskReminder.NONE, "", 0L, "")
        val tasks = listOf(
            task("next", AssistantTaskDue.DateOnly("2026-11-19")),
            task("late", AssistantTaskDue.DateTime(AssistantTaskDates.dateTimeMillis("2026-11-18", 18 * 60, zone)!!)),
            task("early", AssistantTaskDue.DateTime(AssistantTaskDates.dateTimeMillis("2026-11-18", 9 * 60, zone)!!)),
            task("dateOnly", AssistantTaskDue.DateOnly("2026-11-18")),
        )

        /** When **/
        val sorted = AssistantTaskDates.sortedByDue(tasks, zone)

        /** Then **/
        assertEquals(listOf("dateOnly", "early", "late", "next"), sorted.map { it.pageId })
    }

    /**
     * 기한 없는 Todo가 섞이면 전제 조건 위반으로 예외를 던지는지 검증
     *
     * Given: 날짜만 기한의 Todo와 기한 없는 Todo
     * When: sortedByDue 호출
     * Then: IllegalArgumentException
     */
    @Test
    fun test_08_sortedByDueRejectsTaskWithoutDue() = runTest {
        /** Given **/
        val tasks = listOf(
            AssistantTask("due", "due", AssistantTaskKind.TASK, false, AssistantTaskDue.DateOnly("2026-11-18"), AssistantTaskReminder.NONE, "", 0L, ""),
            AssistantTask("noDue", "noDue", AssistantTaskKind.TASK, false, null, AssistantTaskReminder.NONE, "", 0L, ""),
        )

        /** When / Then **/
        assertThrows(IllegalArgumentException::class.java) { AssistantTaskDates.sortedByDue(tasks, zone) }
    }
}
