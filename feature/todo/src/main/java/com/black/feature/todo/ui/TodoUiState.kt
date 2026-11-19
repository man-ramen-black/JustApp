package com.black.feature.todo.ui

import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoReminder
import com.black.feature.todo.model.TodoSyncState
import com.black.feature.todo.model.sortedByDue
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Todo 화면 상태 */
data class TodoUiState(
    /** 빈 구획을 뺀 구획 */
    val taskSections: List<TodoSection> = emptyList(),
    /** 완료 구획 펼침 여부 */
    val doneExpanded: Boolean = false,
    /** 노션 동기화 진행 표시 여부 */
    val isRefreshing: Boolean = false,
    /** 토큰과 데이터 소스 ID가 모두 저장돼 있는지 여부 */
    val configured: Boolean = false,
    /**
     * 저장된 연결을 처음 읽었는지 여부
     *
     * 읽기 전에는 [configured]가 기본값 false라 미설정 화면을 이 값이 true일 때만 보임.
     */
    val connectionLoaded: Boolean = false,
    /**
     * 마지막 동기화 시도의 시각과 결과
     *
     * 시도한 적 없거나 연결을 바꿔 비웠으면 null.
     */
    val lastSync: TodoSyncState? = null,
    /**
     * Todo 알림 켜짐 여부
     *
     * 앱 알림 허용에 더해 집 도착·시각 알림 채널이 모두 켜져 있어야 true.
     * 화면이 다시 보일 때 확인하며, 확인 전에는 허용 필요 표시가 깜빡이지 않도록 true.
     */
    val todoNotificationsEnabled: Boolean = true,
    /**
     * 정확한 알람 허용 여부
     *
     * 허용되지 않으면 시각 알림을 부정확한 알람으로 예약해, 대체로 1시간 안에서 기한보다 늦게 울릴 수 있음.
     * 화면이 다시 보일 때 확인하며, 확인 전에는 허용 필요 표시가 깜빡이지 않도록 true.
     */
    val exactAlarmPermissionGranted: Boolean = true,
    /** 연결 설정 구획 펼침 여부 */
    val settingsExpanded: Boolean = false,
    /** 통합 토큰 입력 칸 값 */
    val tokenInput: String = "",
    /** data source ID 입력 칸 값 */
    val dataSourceIdInput: String = "",
    /** 빠른 추가 구획 펼침 여부 */
    val addExpanded: Boolean = false,
    /** 새 Todo 이름 입력 칸 값 */
    val newTitleInput: String = "",
    /** 새 Todo 기한 날짜, 미설정이면 null */
    val newDueDate: LocalDate? = null,
    /**
     * 새 Todo 기한 시각이며, 미설정이면 null
     *
     * [newDueDate]가 있을 때만 값을 가짐.
     */
    val newDueTime: LocalTime? = null,
    /** 새 Todo 알림 방식 */
    val newReminder: TodoReminder = TodoReminder.NONE,
)

/**
 * Todo 화면의 구획 종류
 *
 * 선언 순서가 화면에 보이는 순서.
 */
enum class TodoSectionType { OVERDUE, TODAY, UPCOMING, NO_DUE, DONE }

/**
 * 구획 하나와 그 안의 항목
 *
 * [tasks]는 [TodoSections.group]의 구획별 정렬 규칙대로 정렬된 상태.
 */
data class TodoSection(val type: TodoSectionType, val tasks: List<Todo>)

/** 저장된 항목을 기한 기준 구획으로 나누는 함수 모음 */
object TodoSections {

    /**
     * 항목을 구획으로 나눔
     *
     * 빈 구획은 빼고 [TodoSectionType] 선언 순서로 반환함.
     * 완료 항목은 DONE, 미완료는 기한의 [zone] 기준 날짜를 [today]와 비교해 나눔.
     * 기한이 있는 구획은 날짜 오름차순이며 같은 날은 날짜만 있는 기한이 먼저, 그다음 시각 순.
     * 기한 없음과 완료 구획은 `lastEditedTime` 내림차순.
     */
    fun group(tasks: List<Todo>, today: LocalDate, zone: ZoneId): List<TodoSection> {
        val doneTasks = tasks.filter { it.done }.sortedByDescending { it.lastEditedTime }
        val openTasks = tasks.filterNot { it.done }
        val noDueTasks = openTasks.filter { it.due == null }.sortedByDescending { it.lastEditedTime }
        val dueTasks = openTasks.filter { it.due != null }.sortedByDue(zone)
        // 시각 기한은 기기 시간대 기준 날짜로 비교
        val overdueTasks = dueTasks.filter { requireNotNull(it.due).localDate(zone) < today }
        val todayTasks = dueTasks.filter { requireNotNull(it.due).localDate(zone) == today }
        val upcomingTasks = dueTasks.filter { requireNotNull(it.due).localDate(zone) > today }
        return listOf(
            TodoSection(TodoSectionType.OVERDUE, overdueTasks),
            TodoSection(TodoSectionType.TODAY, todayTasks),
            TodoSection(TodoSectionType.UPCOMING, upcomingTasks),
            TodoSection(TodoSectionType.NO_DUE, noDueTasks),
            TodoSection(TodoSectionType.DONE, doneTasks),
        ).filter { it.tasks.isNotEmpty() }
    }
}
