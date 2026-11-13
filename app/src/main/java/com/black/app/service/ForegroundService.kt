package com.black.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationManagerCompat
import com.black.app.broadcast.PackageInstallReceiver
import com.black.app.broadcast.ScreenReceiver
import com.black.app.ui.MainActivity
import com.black.core.service.BaseForegroundService
import com.black.core.util.NotificationUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForegroundService : BaseForegroundService() {
    companion object {
        private const val NOTIFICATION_ID = 428
        private const val NOTIFICATION_CHANNEL_ID = "ForegroundService"
        private const val NOTIFICATION_CHANNEL_NAME = "Foreground Service"

        private val mutableRunning = MutableStateFlow(false)

        /** 서비스 실행 여부(매니페스트에 android:process가 없어 앱 프로세스 안에서 유효) */
        val running: StateFlow<Boolean> = mutableRunning.asStateFlow()

        /** onDestroy 뒤에도 재시작 확인을 이어 가도록 서비스 수명과 분리한 범위 */
        private val restartScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override val notificationId: Int = NOTIFICATION_ID

    /**
     * 매니페스트에 선언한 타입(specialUse)으로 시작
     *
     * 타입 인자 없는 startForeground가 내부에서 넘기는 값과 같음. 기반 클래스가 Android 10 이상에서만 읽어 그 미만은 상수 인라인만 일어남
     */
    @SuppressLint("InlinedApi")
    override val type: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST

    override fun onCreateNotification(): Notification {
        NotificationUtil.createNotificationChannel(applicationContext, NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManagerCompat.IMPORTANCE_LOW)
        val notification = NotificationUtil.createNotification(
            applicationContext,
            NOTIFICATION_CHANNEL_ID,
            "Foreground Service",
            "Service is running",
            null
        ) {
            it.setOngoing(true)
            // One UI 8.5 "그 외 알림"(백그라운드 활동) 분류 조건은 액션 버튼·커스텀 뷰 없는 FGS 알림, 분류된 알림은 Samsung 강제 자동 묶음에서도 제외
            // setColorized FGS 알림은 SystemUI가 상단 FGS 구역으로 먼저 분류하므로 액션·색 미지정
            val flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            it.setContentIntent(PendingIntent.getActivity(this, NOTIFICATION_ID, Intent(this, MainActivity::class.java), flags))
        }
        return notification
    }

    override fun onCreate() {
        super.onCreate()
        mutableRunning.value = true

        ScreenReceiver.register(this)
        PackageInstallReceiver.register(this)
    }

    override fun onDestroy() {
        ScreenReceiver.unregister(this)
        PackageInstallReceiver.unregister(this)
        mutableRunning.value = false

        super.onDestroy()
    }

    /**
     * 실행 설정이 켜져 있고 배터리 최적화 예외일 때만 재시작
     *
     * 중지 인텐트([EXTRA_STOP]) 여부는 보지 않고 실행 설정으로만 판단(앱의 끄기는 실행 설정 저장 뒤 stopService)
     */
    override fun restartIfNeeded() {
        // 사용자가 끈 경우는 실행 설정을 false로 저장한 뒤 중지하므로 재시작하지 않음
        val controller = ForegroundServiceController(applicationContext)
        restartScope.launch { controller.startIfActivated() }
    }
}
