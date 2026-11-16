package com.black.app.ui.main.sleeproutine

import com.black.app.sleeproutine.SleepRoutineSettings

/** 수면 루틴 화면 상태 */
data class SleepRoutineUiState(
    /** 저장된 설정 불러오기 완료 여부, 불러오기 전에는 기본값이 저장 설정을 덮어쓰지 않도록 입력·저장·시작 비활성 */
    val settingsLoaded: Boolean = false,
    /** YouTube URL 입력값 */
    val youtubeUrlInput: String = "",
    /** 정지 시간(분) 입력값 */
    val durationInput: String = SleepRoutineSettings.DEFAULT_DURATION_MINUTES.toString(),
    /** 시작할 때 미디어 볼륨을 맞출지 여부 */
    val volumeEnabled: Boolean = true,
    /** 맞출 미디어 볼륨 단계 */
    val volumeLevel: Int = SleepRoutineSettings.DEFAULT_VOLUME_LEVEL,
    /** 기기의 미디어 볼륨 최대 단계 */
    val maxVolumeLevel: Int = 15,
    /** YouTube URL 형식 오류 표시 여부 */
    val youtubeUrlError: Boolean = false,
    /** 정지 시간 범위 오류 표시 여부 */
    val durationError: Boolean = false,
    /** 수면 루틴 서비스 실행 여부 */
    val running: Boolean = false,
    /** 진행 중 세션 종료 시각(epoch ms), 진행 중이 아니면 null */
    val sessionEndTimeMillis: Long? = null,
    /** 방해 금지 접근 허용 여부 */
    val doNotDisturbAccessGranted: Boolean = false,
    /** 다른 앱 위에 표시 허용 여부 */
    val overlayGranted: Boolean = false,
    /** 알림 접근 허용 여부 */
    val notificationListenerEnabled: Boolean = false,
    /** 알림 표시 허용 여부 */
    val notificationsEnabled: Boolean = false,
    /** 접근성 서비스 켜짐 여부 */
    val accessibilityServiceEnabled: Boolean = false,
) {
    /**
     * 수면 루틴 진행 중 여부
     *
     * 앱 업데이트·강제 중지로 서비스가 멈추면 [running]은 false여도 세션과 켜진 방해 금지가 남을 수 있음.
     * 그 경우도 종료로 마무리하도록 세션이 남아 있으면 진행 중으로 판단
     */
    val inProgress: Boolean
        get() = running || sessionEndTimeMillis != null
}
