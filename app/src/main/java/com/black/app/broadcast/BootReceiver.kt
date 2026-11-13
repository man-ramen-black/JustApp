package com.black.app.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.black.app.service.ForegroundServiceController
import com.black.app.util.ComponentExtensions.launchAsync
import com.black.core.util.Log
import com.black.core.util.NotificationUtil

/**
 * 부팅 완료 시 실행 설정이 켜진 ForegroundService 시작
 *
 * 앱 업데이트 뒤 재시작은 [AppUpdateReceiver] 담당
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val NOTIFICATION_ID = 6
        private const val NOTIFICATION_CHANNEL_ID = "DeviceBoot"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("context = $context, intent = $intent")

        context ?: return
        // exported라 다른 앱의 명시적 브로드캐스트도 받으므로 action 확인
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.w("unexpected action : ${intent?.action}")
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

        // 부팅 직후 백그라운드라 설정 화면 이동 없이 시작만 시도
        val controller = ForegroundServiceController(context)
        launchAsync { controller.startIfActivated() }
    }
}