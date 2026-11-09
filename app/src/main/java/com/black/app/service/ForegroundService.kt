package com.black.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.black.app.broadcast.PackageInstallReceiver
import com.black.app.broadcast.ScreenReceiver
import com.black.app.ui.MainActivity
import com.black.core.util.Log
import com.black.core.util.NotificationUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForegroundService : Service() {
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

    private val interfaces : List<Interface> = listOf(
        ScreenReceiver.ServiceInterface,
        PackageInstallReceiver.ServiceInterface
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("intent=$intent, flags=$flags, startId=$startId")
        interfaces.forEach {
            it.onForegroundServiceStartCommand(this, intent ?: return@forEach, flags, startId)
        }

        // onStartCommand default return : START_STICKY
        // https://developer.android.com/reference/android/app/Service#onStartCommand(android.content.Intent,%20int,%20int)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d()
        super.onCreate()

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
        startForeground(NOTIFICATION_ID, notification)
        mutableRunning.value = true

        interfaces.forEach { it.onForegroundServiceCreate(this) }
    }

    override fun onDestroy() {
        Log.d()
        interfaces.forEach { it.onForegroundServiceDestroy(this) }
        mutableRunning.value = false

        super.onDestroy()

        // android 12 포그라운드 서비스 예외사항 : 배터리 최적화 예외 시 포그라운드 서비스 내에서 재시작 가능
        // https://developer.android.com/about/versions/12/foreground-services?hl=ko#cases-fgs-background-starts-allowed
        // 무한 재실행
        // https://medium.com/@Lakshya_Punhani/background-services-running-forever-in-android-part-2-6e3a667d36fd
        // 사용자가 끈 경우는 실행 설정을 false로 저장한 뒤 중지하므로 재시작하지 않음
        val controller = ForegroundServiceController(applicationContext)
        restartScope.launch { controller.startIfActivated() }
    }

    interface Interface {
        fun onForegroundServiceStartCommand(context: Context, intent: Intent, flags: Int, startId: Int)
        fun onForegroundServiceCreate(context: Context)
        fun onForegroundServiceDestroy(context: Context)
    }
}