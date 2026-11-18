package com.black.app.ui.main.assistanttask

import com.black.app.assistanttask.AssistantTask
import com.black.app.assistanttask.AssistantTaskDates
import java.util.TimeZone

/**
 * 비서 할 일 화면의 구획 종류
 *
 * 선언 순서가 화면에 보이는 순서.
 */
enum class AssistantTaskSectionType { OVERDUE, TODAY, UPCOMING, NO_DUE, DONE }

/**
 * 구획 하나와 그 안의 항목
 *
 * [tasks]는 [AssistantTaskSections.group]의 구획별 정렬 규칙대로 정렬된 상태.
 */
data class AssistantTaskSection(val type: AssistantTaskSectionType, val tasks: List<AssistantTask>)

/** 저장된 항목을 기한 기준 구획으로 나누는 함수 모음 */
object AssistantTaskSections {

    /**
     * 항목을 구획으로 나눔
     *
     * 빈 구획은 빼고 [AssistantTaskSectionType] 선언 순서로 반환함.
     * 완료 항목은 DONE, 미완료는 기한의 [zone] 기준 날짜를 [today]와 비교해 나눔.
     * 기한이 있는 구획은 날짜 오름차순이며 같은 날은 날짜만 있는 기한이 먼저, 그다음 시각 순.
     * 기한 없음과 완료 구획은 `lastEditedTime` 내림차순.
     */
    fun group(tasks: List<AssistantTask>, today: String, zone: TimeZone): List<AssistantTaskSection> {
        val doneTasks = tasks.filter { it.done }.sortedByDescending { it.lastEditedTime }
        val openTasks = tasks.filterNot { it.done }
        val noDueTasks = openTasks.filter { it.due == null }.sortedByDescending { it.lastEditedTime }
        val dueTasks = AssistantTaskDates.sortedByDue(openTasks.filter { it.due != null }, zone)
        // yyyy-MM-dd 형식은 문자열 비교가 날짜 비교와 같음
        val overdueTasks = dueTasks.filter { AssistantTaskDates.localDateOf(requireNotNull(it.due), zone) < today }
        val todayTasks = dueTasks.filter { AssistantTaskDates.localDateOf(requireNotNull(it.due), zone) == today }
        val upcomingTasks = dueTasks.filter { AssistantTaskDates.localDateOf(requireNotNull(it.due), zone) > today }
        return listOf(
            AssistantTaskSection(AssistantTaskSectionType.OVERDUE, overdueTasks),
            AssistantTaskSection(AssistantTaskSectionType.TODAY, todayTasks),
            AssistantTaskSection(AssistantTaskSectionType.UPCOMING, upcomingTasks),
            AssistantTaskSection(AssistantTaskSectionType.NO_DUE, noDueTasks),
            AssistantTaskSection(AssistantTaskSectionType.DONE, doneTasks),
        ).filter { it.tasks.isNotEmpty() }
    }
}
