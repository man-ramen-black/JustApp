package com.black.app.ui.maintab.main.appinstallnotification

import com.black.app.ui.common.selectapp.SelectedAppUiItem

/** 앱 설치 알림 화면 상태 */
data class AppInstallNotificationUiState(
    /** 알림 설정(ForegroundService 실행 설정) 켜짐 여부 */
    val enabled: Boolean = false,
    /** ForegroundService 실행 여부 */
    val serviceRunning: Boolean = false,
    /** 배터리 최적화 예외 여부 */
    val batteryOptimizationIgnored: Boolean = false,
    /** 설치 알림 표시 가능 여부(앱 알림 허용·설치 알림 채널 차단 반영) */
    val notificationsEnabled: Boolean = false,
    /** 미리보기 예시 앱(JustApp) 라벨·아이콘. 조회 전에는 null */
    val previewApp: SelectedAppUiItem? = null,
    /** 설치 알림을 게시하는 앱(JustApp) 라벨·아이콘. 대화 알림 배지에 표시, 조회 전에는 null */
    val notifierApp: SelectedAppUiItem? = null,
)
