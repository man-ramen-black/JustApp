package com.black.app.ui.main.usagetimer

import com.black.app.ui.common.selectapp.SelectedAppUiItem

/** UsageTimer 화면 상태 */
data class UsageTimerUiState(
    /** 일시정지 잔여 시간(ms). 0이면 카운트다운 영역 숨김 */
    val pauseRemainTimeMillis: Long = 0L,
    /** 일시정지 시간 입력값(분 단위 문자열) */
    val pauseDurationInput: String = "",
    /** 선택된 앱 표시 목록 */
    val selectedApps: List<SelectedAppUiItem> = emptyList(),
    /** 다른 앱 위에 표시 권한 안내 다이얼로그 표시 여부 */
    val showOverlayPermissionDialog: Boolean = false,
)
