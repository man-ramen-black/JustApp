package com.black.app.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.black.app.service.ForegroundServiceController
import com.black.app.util.ComponentExtensions.launchAsync
import com.black.core.util.Log
import com.black.core.util.NotificationUtil

// 앱 업데이트 시 Service 자동 시작하려면 ACTION_MY_PACKAGE_REPLACED
// https://developer.android.com/reference/android/content/Intent#ACTION_MY_PACKAGE_REPLACED
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val NOTIFICATION_ID = 6
        private const val NOTIFICATION_CHANNEL_ID = "DeviceBoot"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("BootReceiver: context = $context, intent = $intent")

        context ?: return

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