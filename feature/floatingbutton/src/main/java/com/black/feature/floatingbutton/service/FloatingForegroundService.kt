package com.black.feature.floatingbutton.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.black.core.service.BaseForegroundService
import com.black.core.service.ServiceActivity.Companion.toStartServiceActivityIntent
import com.black.core.util.NotificationUtil
import com.black.core.util.OverlayViewUtil
import com.black.core.util.PermissionHelper
import com.black.feature.floatingbutton.ui.floating.FloatingView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FloatingForegroundService: BaseForegroundService() {
    companion object {
        private const val NOTIFICATION_ID = 1225
        // 앱 설치 알림 서비스와 채널을 나눠 사용자가 한쪽 알림만 끌 수 있게 함
        private const val NOTIFICATION_CHANNEL_ID = "FloatingButton"
        private const val NOTIFICATION_CHANNEL_NAME = "플로팅 버튼"

        private val mutableRunning = MutableStateFlow(false)

        /** 서비스 실행 여부(매니페스트에 android:process가 없어 앱 프로세스 안에서 유효) */
        val running: StateFlow<Boolean> = mutableRunning.asStateFlow()
    }

    private var view: FloatingView? = null

    override val notificationId: Int = NOTIFICATION_ID

    override val type: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
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
            // 작은 아이콘은 createNotification이 매니페스트 NOTIFICATION_ICON(없으면 앱 아이콘)으로 지정
            it.setOngoing(true)
            val flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val stopIntent = createStopIntent(this, FloatingForegroundService::class.java)
                .toStartServiceActivityIntent()
            val pendingIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, stopIntent, flags)
            it.setContentIntent(pendingIntent)
        }
        return notification
    }

    override fun onCreate() {
        super.onCreate()
        mutableRunning.value = true
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
            // 버튼 없이 알림만 남은 채 실행되지 않도록 중지, 권한을 허용한 뒤 다시 켜면 버튼 표시
            // stopSelf로 멈추면 onDestroy가 재시작하므로 중지 인텐트 사용
            BaseForegroundService.stop(this, FloatingForegroundService::class.java)
            return result
        }

        if (view == null) {
            view = FloatingView(this)
                .also { it.attachView() }
        }

        return result
    }

    override fun onDestroy() {
        mutableRunning.value = false
        super.onDestroy()
        view?.let {
            it.detachView()
            view = null
        }
    }
}