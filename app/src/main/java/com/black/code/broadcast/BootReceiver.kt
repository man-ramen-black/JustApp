package com.black.code.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.black.code.util.NotificationUtil
import com.black.code.contents.service.ForegroundService
import com.black.code.util.Log

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

        ForegroundService.start(context)
    }
}