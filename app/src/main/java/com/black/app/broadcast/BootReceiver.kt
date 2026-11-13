package com.black.app.broadcast

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.black.core.util.NotificationUtil

/**
 * 부팅 완료 시 실행 설정이 켜진 ForegroundService 시작
 *
 * 앱 업데이트 뒤 재시작은 [AppUpdateReceiver] 담당
 */
class BootReceiver : ForegroundServiceStartReceiver(Intent.ACTION_BOOT_COMPLETED) {
    companion object {
        private const val NOTIFICATION_ID = 6
        private const val NOTIFICATION_CHANNEL_ID = "DeviceBoot"
    }

    /** 서비스 시작 요청 전 부팅 알림 표시 */
    override fun onExpectedActionReceived(context: Context) {
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
