package com.black.feature.floatingbutton.ui

import com.black.core.ui.component.OverlayPositionInput
import com.black.feature.floatingbutton.data.datastore.FloatingDataStore

/** 플로팅 버튼 설정 화면 상태 */
data class FloatingSettingUiState(
    /** 플로팅 버튼 서비스 실행 여부 */
    val running: Boolean = false,
    /** 다른 앱 위에 표시 권한 허용 여부 */
    val overlayPermissionGranted: Boolean = false,
    /** 뒤로 가기 버튼이 쓰는 접근성 서비스 사용 여부 */
    val accessibilityServiceEnabled: Boolean = false,
    /** 다른 앱 위에 표시 권한 안내 다이얼로그 표시 여부 */
    val showOverlayPermissionDialog: Boolean = false,
    /** 플로팅 버튼 표시 위치 입력 */
    val positionInput: OverlayPositionInput = OverlayPositionInput.from(FloatingDataStore.DEFAULT_POSITION),
)
