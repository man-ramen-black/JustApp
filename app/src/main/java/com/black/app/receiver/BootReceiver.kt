package com.black.app.receiver

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.black.core.util.Log
import com.black.core.util.NotificationUtil
import com.black.core.util.PermissionHelper

/**
 * 부팅 완료 시 실행 설정이 켜진 ForegroundService 시작과 진행 중이던 수면 루틴 세션 재개
 *
 * 앱 업데이트 뒤 재시작은 [AppUpdateReceiver] 담당
 */
class BootReceiver : ForegroundServiceStartReceiver(Intent.ACTION_BOOT_COMPLETED) {
    companion object {
        private const val NOTIFICATION_ID = 6
        private const val NOTIFICATION_CHANNEL_ID = "DeviceBoot"
    }

    /** 서비스 시작 요청 전 부팅 알림 표시 */
    @SuppressLint("MissingPermission") // 알림 권한 확인 뒤 호출
    override fun onExpectedActionReceived(context: Context) {
        // Android 13 이상 알림 권한 미허용 시 표시 불가, 서비스 시작은 이 함수와 별개로 진행
        if (!PermissionHelper.isNotificationPermissionGranted(context)) {
            Log.w("notification permission not granted")
            return
        }

        NotificationUtil.createNotificationChannel(context, NOTIFICATION_CHANNEL_ID, "Device boot", NotificationManagerCompat.IMPORTANCE_LOW)
        NotificationUtil.showNotification(
            context,
            NOTIFICATION_CHANNEL_ID,
            "Device booted",
            "Boot completed",
            NOTIFICATION_ID
        )
    }
}
