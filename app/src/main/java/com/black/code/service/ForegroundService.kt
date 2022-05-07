package com.black.code.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.black.code.broadcast.ScreenReceiver
import com.black.code.ui.example.usagetimechecker.UsageTimeCheckerManager
import com.black.code.util.Log
import com.black.code.util.NotificationUtil
import com.black.code.util.PermissionHelper

class ForegroundService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 428
        private const val NOTIFICATION_CHANNEL_ID = "ForegroundService"
        private const val NOTIFICATION_CHANNEL_NAME = "Foreground Service"

        const val ACTION_STOP = "STOP"

        fun start(context: Context, onSetIntent: ((intent: Intent) -> Unit)? = null) : Boolean {
            return start(context, true, onSetIntent)
        }

        fun start(context: Context, requestPermission: Boolean, onSetIntent: ((intent: Intent) -> Unit)? = null) : Boolean {
            if (!PermissionHelper.isIgnoringBatteryOptimizations(context)) {
                if (requestPermission) {
                    PermissionHelper.showIgnoreBatteryOptimizationSettings(context)
                }
                return false
            }

            val appContext = context.applicationContext
            val intent = Intent(appContext, ForegroundService::class.java)
            onSetIntent?.invoke(intent)
            appContext.startService(intent)
            return true
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, ForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            appContext.startService(intent)
        }
    }

    private val interfaces = listOf(
        UsageTimeCheckerManager,
        ScreenReceiver.ServiceInterface
    )

    private var isStopped = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("intent=$intent, flags=$flags, startId=$startId")
        isStopped = false
        interfaces.forEach {
            it.onStartCommand(this, intent ?: return@forEach, flags, startId)
        }

        if (intent?.action == ACTION_STOP) {
            isStopped = true
            stopSelf()
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
        }
        startForeground(NOTIFICATION_ID, notification)

        interfaces.forEach { it.onCreate(this) }
    }

    override fun onDestroy() {
        Log.d()
        interfaces.forEach { it.onDestroy(this) }

        super.onDestroy()

        // android 12 포그라운드 서비스 예외사항 : 배터리 최적화 예외 시 포그라운드 서비스 내에서 재시작 가능
        // https://developer.android.com/about/versions/12/foreground-services?hl=ko#cases-fgs-background-starts-allowed
        // 무한 재실행
        // https://medium.com/@Lakshya_Punhani/background-services-running-forever-in-android-part-2-6e3a667d36fd
        if (!isStopped) {
            start(this)
        }
    }

    interface Interface {
        fun onStartCommand(context: Context, intent: Intent, flags: Int, startId: Int)
        fun onCreate(context: Context)
        fun onDestroy(context: Context)
    }
}