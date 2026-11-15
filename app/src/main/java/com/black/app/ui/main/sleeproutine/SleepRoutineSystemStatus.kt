package com.black.app.ui.main.sleeproutine

import android.content.Context
import com.black.app.sleeproutine.MediaVolumeController
import com.black.app.sleeproutine.SleepRoutineService
import com.black.app.usagelog.collect.UsageLogListenerService
import com.black.core.service.BaseForegroundService
import com.black.core.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * [SleepRoutineScreen]이 보여 주는 권한·볼륨 범위 조회와 수면 루틴 시작·종료
 *
 * ViewModel에 주입하기 위한 얇은 래퍼
 */
class SleepRoutineSystemStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 수면 루틴 서비스 실행 여부 */
    val running: StateFlow<Boolean> = SleepRoutineService.running

    fun hasDoNotDisturbAccess(): Boolean = PermissionHelper.isNotificationPolicyAccessGranted(context)

    /**
     * 다른 앱 위에 표시 허용 여부
     *
     * 서비스에서 YouTube·홈 화면을 띄울 때 백그라운드 실행 제한 예외로 필요
     */
    fun canDrawOverlays(): Boolean = PermissionHelper.canDrawOverlays(context)

    /**
     * 알림 접근 허용 여부
     *
     * YouTube 재생 상태 확인과 재생·일시정지에 필요
     */
    fun isNotificationListenerEnabled(): Boolean =
        PermissionHelper.isNotificationListenerEnabled(context, UsageLogListenerService::class.java)

    fun canShowNotification(): Boolean = PermissionHelper.areNotificationsEnabled(context)

    fun maxVolumeLevel(): Int = MediaVolumeController(context).maxLevel()

    fun startRoutine() {
        BaseForegroundService.start(context, SleepRoutineService::class.java)
    }

    fun stopRoutine() {
        BaseForegroundService.stop(context, SleepRoutineService::class.java)
    }
}
