package com.black.app.ui.main.assistanttask

import com.black.app.assistanttask.AssistantTask
import com.black.app.assistanttask.AssistantTaskDue
import com.black.app.assistanttask.AssistantTaskKind
import com.black.app.assistanttask.AssistantTaskReminder
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.TimeZone

/** [AssistantTaskSections]의 구획 분류와 구획 안 정렬 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssistantTaskSectionsTest : BaseTest() {

    private val zone = TimeZone.getTimeZone("Asia/Seoul")
    private val today = "2026-11-18"

    private fun task(id: String, due: AssistantTaskDue? = null, done: Boolean = false, lastEditedTime: Long = 0L) =
        AssistantTask(
            pageId = id,
            title = id,
            kind = AssistantTaskKind.TASK,
            done = done,
            due = due,
            reminder = AssistantTaskReminder.NONE,
            content = "",
            lastEditedTime = lastEditedTime,
            url = "https://www.notion.so/$id",
        )

    /**
     * 구획이 고정된 순서로 나뉘는지 검증
     *
     * Given: 어제·오늘·내일·기한 없음·완료 항목 각 1개(순서 섞음)
     * When: group 호출
     * Then: 구획 타입 순서 [OVERDUE, TODAY, UPCOMING, NO_DUE, DONE]
     */
    @Test
    fun test_01_groupsInFixedOrder() = runTest {
        /** Given **/
        val tasks = listOf(
            task("done", done = true),
            task("upcoming", AssistantTaskDue.DateOnly("2026-11-19")),
            task("none"),
            task("overdue", AssistantTaskDue.DateOnly("2026-11-17")),
            task("today", AssistantTaskDue.DateOnly("2026-11-18")),
        )

        /** When **/
        val sections = AssistantTaskSections.group(tasks, today, zone)

        /** Then **/
        assertEquals(
            listOf(
                AssistantTaskSectionType.OVERDUE,
                AssistantTaskSectionType.TODAY,
                AssistantTaskSectionType.UPCOMING,
                AssistantTaskSectionType.NO_DUE,
                AssistantTaskSectionType.DONE,
            ),
            sections.map { it.type },
        )
    }

    /**
     * 빈 구획이 빠지는지 검증
     *
     * Given: 기한 없음 항목만
     * When: group 호출
     * Then: [NO_DUE]
     */
    @Test
    fun test_02_omitsEmptySections() = runTest {
        /** Given / When **/
        val sections = AssistantTaskSections.group(listOf(task("none")), today, zone)

        /** Then **/
        assertEquals(listOf(AssistantTaskSectionType.NO_DUE), sections.map { it.type })
    }

    /**
     * 시각 기한이 기기 시간대의 날짜로 분류되는지 검증
     *
     * Given: UTC 2026-11-17T16:00Z(서울 11-18 01:00) 기한 항목
     * When: group 호출
     * Then: TODAY
     */
    @Test
    fun test_03_dateTimeUsesLocalDate() = runTest {
        /** Given **/
        val epochMillis = 1_794_931_200_000L // 2026-11-17T16:00:00Z
        val tasks = listOf(task("a", AssistantTaskDue.DateTime(epochMillis)))

        /** When **/
        val sections = AssistantTaskSections.group(tasks, today, zone)

        /** Then **/
        assertEquals(listOf(AssistantTaskSectionType.TODAY), sections.map { it.type })
    }

    /**
     * 같은 날 안에서 날짜만 있는 기한이 먼저, 그다음 시각 순으로 정렬되는지 검증
     *
     * Given: 같은 날 DateTime(10시)·DateOnly·DateTime(9시)
     * When: group 호출
     * Then: DateOnly, 9시, 10시
     */
    @Test
    fun test_04_ordersWithinSection() = runTest {
        /** Given **/
        val nineHour = 1_794_960_000_000L // 2026-11-18T00:00:00Z = 서울 09:00
        val tasks = listOf(
            task("ten", AssistantTaskDue.DateTime(nineHour + 3_600_000L)),
            task("dateOnly", AssistantTaskDue.DateOnly("2026-11-18")),
            task("nine", AssistantTaskDue.DateTime(nineHour)),
        )

        /** When **/
        val sections = AssistantTaskSections.group(tasks, today, zone)

        /** Then **/
        assertEquals(listOf("dateOnly", "nine", "ten"), sections.single().tasks.map { it.pageId })
    }

    /**
     * 완료 구획이 최근 수정 순으로 정렬되는지 검증
     *
     * Given: lastEditedTime이 다른 완료 항목 둘
     * When: group 호출
     * Then: lastEditedTime이 큰 항목이 먼저
     */
    @Test
    fun test_05_doneSortedByLastEditedDesc() = runTest {
        /** Given **/
        val tasks = listOf(
            task("old", done = true, lastEditedTime = 100L),
            task("new", done = true, lastEditedTime = 200L),
        )

        /** When **/
        val sections = AssistantTaskSections.group(tasks, today, zone)

        /** Then **/
        assertEquals(listOf("new", "old"), sections.single().tasks.map { it.pageId })
    }
}
