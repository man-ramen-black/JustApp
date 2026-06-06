package com.black.app.ui.maintab.main.usagetimer

import com.black.app.ui.common.selectapp.SelectedAppUiItem

/** UsageTimer 화면 상태 */
data class UsageTimerUiState(
    /** 일시정지 잔여 시간(ms). 0이면 카운트다운 영역 숨김 */
    val pauseRemainTimeMillis: Long = 0L,
    /** 일시정지 시간 입력값(분 단위 문자열) */
    val pauseDurationInput: String = "",
    /** 선택된 앱 표시 목록 */
    val selectedApps: List<SelectedAppUiItem> = emptyList(),
)

/** UsageTimer 화면 일회성 이벤트 */
sealed interface UsageTimerEvent {
    /** 사용 시간 타이머 오버레이 표시 */
    data object ShowTimerView : UsageTimerEvent
    /** 토스트 메시지 표시 */
    data class ShowToast(val message: String) : UsageTimerEvent
    /** 서비스에 떠 있는 타이머 오버레이 제거 */
    data object DetachTimerView : UsageTimerEvent
    /** 접근성 설정 화면 이동 */
    data object OpenAccessibilitySettings : UsageTimerEvent
    /** 앱 선택 다이얼로그 표시(현재 선택 목록 전달) */
    data class OpenSelectApp(val checkedPackageNames: List<String>) : UsageTimerEvent
}
