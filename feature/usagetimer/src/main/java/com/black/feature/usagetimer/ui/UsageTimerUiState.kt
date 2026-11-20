package com.black.feature.usagetimer.ui

import com.black.core.ui.component.OverlayPositionInput
import com.black.core.ui.selectapp.SelectedAppUiItem
import com.black.feature.usagetimer.model.UsageTimerDefaults

/** UsageTimer 화면 상태 */
data class UsageTimerUiState(
    /** 일시정지 잔여 시간(ms). 0이면 카운트다운 영역 숨김 */
    val pauseRemainTimeMillis: Long = 0L,
    /** 일시정지 시간 입력값(분 단위 문자열) */
    val pauseDurationInput: String = "",
    /** 선택된 앱 표시 목록 */
    val selectedApps: List<SelectedAppUiItem> = emptyList(),
    /** 타이머 표시 위치 입력값 */
    val positionInput: OverlayPositionInput = OverlayPositionInput.from(UsageTimerDefaults.POSITION),
    /** 다른 앱 위에 표시 권한 안내 다이얼로그 표시 여부 */
    val showOverlayPermissionDialog: Boolean = false,
    /** 앱 접근성 서비스 켜짐 여부 */
    val accessibilityServiceEnabled: Boolean = false,
    /** 다른 앱 위에 표시 권한 허용 여부 */
    val overlayPermissionGranted: Boolean = false,
    /** 사용 정보 접근 권한 허용 여부 */
    val usageAccessGranted: Boolean = false,
    /** 선택 앱별 최근 사용 시간(ms). 권한이 없거나 조회하지 못하면 null */
    val recentUsageMillis: Map<String, Long>? = null,
)
