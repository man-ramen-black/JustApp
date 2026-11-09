package com.black.feature.floatingbutton.ui

import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import com.black.feature.floatingbutton.service.FloatingButtonController
import com.black.feature.floatingbutton.ui.FloatingSettingScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** [FloatingSettingScreen]의 상태·이벤트 관리 ViewModel */
@HiltViewModel
class FloatingSettingViewModel @Inject constructor(
    private val controller: FloatingButtonController,
) : EventViewModel() {

    /** 플로팅 버튼 설정 화면 일회성 이벤트 */
    sealed class FloatingSettingEvent : ViewModelEvent

    /** 이 앱의 다른 앱 위에 표시 권한 설정 화면 이동 */
    data object EventOpenOverlaySettings : FloatingSettingEvent()
    /** 플로팅 버튼 접근성 서비스 설정 화면 이동 */
    data object EventOpenAccessibilitySettings : FloatingSettingEvent()

    private val mutableUiState = MutableStateFlow(FloatingSettingUiState())
    val uiState: StateFlow<FloatingSettingUiState> = mutableUiState.asStateFlow()

    init {
        // 스위치는 낙관적으로 바꾸지 않고 서비스 실행 여부를 따름(알림·바로가기로 켜고 끈 경우도 반영)
        launch {
            controller.running.collect { running ->
                mutableUiState.update { it.copy(running = running) }
            }
        }
        refreshStatus()
    }

    /** 설정 앱에서 바뀔 수 있는 권한·접근성 서비스 상태 재조회 */
    fun refreshStatus() {
        mutableUiState.update {
            it.copy(
                overlayPermissionGranted = controller.canDrawOverlays(),
                accessibilityServiceEnabled = controller.isAccessibilityServiceEnabled(),
            )
        }
    }

    /** 플로팅 버튼 서비스 시작·중지 */
    fun setRunning(running: Boolean) {
        if (!running) {
            controller.stop()
            return
        }
        // 권한 없이 시작하면 버튼을 띄우지 못하므로 시작하지 않고 권한부터 안내
        if (!controller.canDrawOverlays()) {
            showOverlayPermissionDialog()
            return
        }
        controller.start()
    }

    fun showOverlayPermissionDialog() {
        mutableUiState.update { it.copy(showOverlayPermissionDialog = true) }
    }

    fun dismissOverlayPermissionDialog() {
        mutableUiState.update { it.copy(showOverlayPermissionDialog = false) }
    }

    /** 권한 안내 다이얼로그를 닫고 이 앱의 권한 스위치가 있는 설정 화면으로 이동 */
    fun openOverlaySettings() {
        dismissOverlayPermissionDialog()
        sendEvent(EventOpenOverlaySettings)
    }

    fun openAccessibilitySettings() {
        sendEvent(EventOpenAccessibilitySettings)
    }
}
