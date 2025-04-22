package com.black.feature.floatingbutton.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.black.core.service.ForegroundService
import com.black.core.service.ServiceActivity.Companion.toStartServiceActivityIntent
import com.black.core.util.NotificationUtil
import com.black.core.util.OverlayViewUtil
import com.black.core.util.PermissionHelper
import com.black.feature.floatingbutton.ui.floating.FloatingView

class FloatingForegroundService: ForegroundService() {
    companion object {
        private const val NOTIFICATION_ID = 1225
        private const val NOTIFICATION_CHANNEL_ID = "ForegroundService"
        private const val NOTIFICATION_CHANNEL_NAME = "Foreground Service"
    }

    private var view: FloatingView? = null

    override val type: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    } else {
        0
    }

    override fun onCreateNotification(): Notification {
        NotificationUtil.createNotificationChannel(applicationContext, NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManagerCompat.IMPORTANCE_LOW)
        val notification = NotificationUtil.createNotification(
            applicationContext,
            NOTIFICATION_CHANNEL_ID,
            "Floating Button",
            "터치 시 종료",
        ) {
            it.setOngoing(true)
            val flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val stopIntent = createStopIntent(this, FloatingForegroundService::class.java)
                .toStartServiceActivityIntent()
            val pendingIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, stopIntent, flags)
            it.setContentIntent(pendingIntent)
        }
        return notification
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (isStopping) {
            return result
        }

        if (!PermissionHelper.canDrawOverlays(this)) {
            Toast.makeText(this, "다른 앱 위에 그리기 권한 허용이 필요합니다.", Toast.LENGTH_SHORT)
                .show()
            PermissionHelper.showDrawOverlaysSetting(this)
            return result
        }

        if (!PermissionHelper.isAccessibilityServiceEnabled(this, FloatingAccessibilityService::class.java)) {
            Toast.makeText(this, "접근성 설정이 필요합니다.", Toast.LENGTH_SHORT)
                .show()
            PermissionHelper.openAccessibilitySetting(this)
            return result
        }

        if (view == null) {
            view = FloatingView(this)
                .also { OverlayViewUtil.attachView(it) }
        }

        return result
    }

    override fun onDestroy() {
        super.onDestroy()
        view?.let {
            OverlayViewUtil.detachView(it)
            view = null
        }
    }
}