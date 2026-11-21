package com.black.feature.usagetimer.ui

import com.black.core.system.RecentAppUsage
import com.black.core.ui.component.OverlayPositionInput
import com.black.core.ui.selectapp.SelectedAppUiItem
import com.black.feature.usagetimer.model.UsageTimerDefaults

/** UsageTimer 화면 상태 */
data class UsageTimerUiState(
    /** 저장된 일시정지 값(입력값, 남은 시간)을 읽었는지. 읽기 전에는 일시정지 영역을 그리지 않음 */
    val pauseStateLoaded: Boolean = false,
    /** 일시정지 잔여 시간(ms). 0이면 일시정지 중이 아님 */
    val pauseRemainTimeMillis: Long = 0L,
    /** 일시정지 시간 입력값(분 단위 문자열) */
    val pauseDurationInput: String = "",
    /** 사용자가 일시정지 시간 입력을 고쳤는지. 고친 뒤에만 입력 오류를 표시 */
    val pauseDurationEdited: Boolean = false,
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
    /**
     * 선택 앱별 최근 사용 구간
     *
     * null이면 아직 조회 전이거나 조회할 수 없음([recentUsageUnavailable]). 맵에 없는 앱은 조회 중(막 추가한 앱).
     */
    val recentUsage: RecentAppUsage? = null,
    /** 권한이 없거나 조회에 실패해 최근 사용 구간을 알 수 없는지 */
    val recentUsageUnavailable: Boolean = false,
    /** 구간 목록을 볼 앱의 패키지 이름. null이거나 선택 앱에 없으면 첫 번째 앱 */
    val usageSessionsPackageName: String? = null,
) {
    /** 입력 오류 표시 여부(사용자가 고친 값이 1 이상의 정수가 아님) */
    val showsPauseDurationError: Boolean
        get() = pauseDurationEdited && pauseDurationMinutes(pauseDurationInput) == null

    /** 구간 목록을 볼 앱 */
    val usageSessionsApp: SelectedAppUiItem?
        get() = selectedApps.firstOrNull { it.packageName == usageSessionsPackageName } ?: selectedApps.firstOrNull()
}

/** 일시정지 시간 입력이 1 이상의 정수면 그 값, 아니면 null */
internal fun pauseDurationMinutes(input: String): Int? = input.toIntOrNull()?.takeIf { it >= 1 }
