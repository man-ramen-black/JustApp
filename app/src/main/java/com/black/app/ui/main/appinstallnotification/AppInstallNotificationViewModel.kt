package com.black.app.ui.main.appinstallnotification

import androidx.annotation.StringRes
import com.black.app.R
import com.black.app.service.ForegroundServiceController
import com.black.app.service.ForegroundServiceController.StartResult
import com.black.app.ui.common.selectapp.selectedAppUiItem
import com.black.app.ui.main.appinstallnotification.AppInstallNotificationScreen
import com.black.app.util.InstalledAppResolver
import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** [AppInstallNotificationScreen]의 상태·이벤트 관리 ViewModel */
@HiltViewModel
class AppInstallNotificationViewModel @Inject constructor(
    private val foregroundServiceController: ForegroundServiceController,
    private val notificationStatus: AppInstallNotificationStatus,
    installedAppResolver: InstalledAppResolver,
) : EventViewModel() {

    /** 앱 설치 알림 화면 일회성 이벤트 */
    sealed class AppInstallNotificationEvent : ViewModelEvent

    /** 배터리 최적화 설정 화면 이동 */
    data object EventOpenBatteryOptimizationSettings : AppInstallNotificationEvent()
    /** 앱 알림 설정 화면 이동 */
    data object EventOpenNotificationSettings : AppInstallNotificationEvent()
    /** 문자열 리소스 토스트 표시 */
    data class EventShowToast(@StringRes val messageResId: Int) : AppInstallNotificationEvent()

    private val mutableUiState = MutableStateFlow(
        AppInstallNotificationUiState(
            previewApp = installedAppResolver.selectedAppUiItem(notificationStatus.previewPackageName),
            notifierApp = installedAppResolver.selectedAppUiItem(notificationStatus.notifierPackageName),
        )
    )
    val uiState: StateFlow<AppInstallNotificationUiState> = mutableUiState.asStateFlow()

    init {
        refreshSystemStatus()
        // 스위치는 화면이 낙관적으로 바꾸지 않고 저장된 실행 설정을 따름(저장 실패 시 꺼진 채 표시)
        launch {
            foregroundServiceController.activated.collect { activated ->
                mutableUiState.update { it.copy(serviceActivated = activated) }
            }
        }
        // 화면이 떠 있는 동안의 시스템 종료·START_STICKY 재시작도 상태 행에 반영
        launch {
            foregroundServiceController.running.collect { running ->
                mutableUiState.update { it.copy(serviceRunning = running) }
            }
        }
    }

    /** 설정 앱에서 바뀔 수 있는 배터리 최적화·알림 상태 재조회 */
    private fun refreshSystemStatus() {
        mutableUiState.update {
            it.copy(
                batteryOptimizationIgnored = foregroundServiceController.isBatteryOptimizationIgnored(),
                notificationsEnabled = notificationStatus.canShowNotification(),
            )
        }
    }

    /** 설정 앱에서 바뀔 수 있는 상태 재조회, 실행 설정이 켜져 있으면 서비스 시작 */
    fun refreshStatus() {
        refreshSystemStatus()
        // 배터리 최적화 설정에서 예외를 허용하고 돌아온 경우의 서비스 시작 경로
        // 설정 확인은 컨트롤러가 저장값으로 하고, 실행 중에 다시 요청해도 무해
        launch {
            foregroundServiceController.startIfActivated()
        }
    }

    fun setEnabled(enabled: Boolean) {
        launch {
            if (!enabled) {
                // 저장에 실패하면 서비스를 멈추지 않으므로 스위치도 저장값(켜짐)을 따라 켜진 채 표시
                foregroundServiceController.deactivate()
                    .onFailure { sendEvent(EventShowToast(R.string.app_install_notification_save_failed)) }
                return@launch
            }
            when (foregroundServiceController.activate()) {
                // 스위치는 켜 둔 채 예외 허용으로 안내, 허용 후 화면 복귀 때 refreshStatus에서 시작
                StartResult.BatteryOptimizationRequired -> sendEvent(EventOpenBatteryOptimizationSettings)
                // 서비스는 돌아도 알림을 표시할 수 없으면 설치 알림이 보이지 않아 알림 설정으로 안내
                StartResult.Started -> if (!notificationStatus.canShowNotification()) {
                    sendEvent(EventOpenNotificationSettings)
                }
                // 시작 직전 다른 경로에서 설정이 꺼진 경우, 스위치는 저장값을 따라 꺼진 채 표시
                StartResult.NotActivated -> Unit
                // 스위치는 저장값을 따라 꺼진 채 표시
                StartResult.SaveFailed -> sendEvent(EventShowToast(R.string.app_install_notification_save_failed))
            }
        }
    }

    fun openBatteryOptimizationSettings() {
        sendEvent(EventOpenBatteryOptimizationSettings)
    }

    fun openNotificationSettings() {
        sendEvent(EventOpenNotificationSettings)
    }

    fun sendTestNotification(isUpdate: Boolean) {
        // 알림 권한 거부·앱 알림 끔·채널 차단이면 알림이 보이지 않고 안내도 없으므로 알림 설정으로 안내
        if (!notificationStatus.canShowNotification()) {
            sendEvent(EventOpenNotificationSettings)
            return
        }
        notificationStatus.showPreviewNotification(isUpdate)
    }
}
