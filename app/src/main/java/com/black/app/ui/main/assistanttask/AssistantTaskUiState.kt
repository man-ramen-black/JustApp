package com.black.app.ui.main.assistanttask

import com.black.app.assistanttask.AssistantTaskReminder
import com.black.app.assistanttask.sync.AssistantTaskSyncState

/** 비서 할 일 화면 상태 */
data class AssistantTaskUiState(
    /** 빈 구획을 뺀 구획 */
    val taskSections: List<AssistantTaskSection> = emptyList(),
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
    val lastSync: AssistantTaskSyncState? = null,
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
    /** 새 Todo 기한 날짜(`yyyy-MM-dd`), 미설정이면 null */
    val newDueDate: String? = null,
    /**
     * 새 Todo 기한 시각이며, 미설정이면 null
     *
     * 자정부터의 분(0~1439)이고, [newDueDate]가 있을 때만 값을 가짐.
     */
    val newDueMinutes: Int? = null,
    /** 새 Todo 알림 방식 */
    val newReminder: AssistantTaskReminder = AssistantTaskReminder.NONE,
)
