package com.black.code.contents.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.black.code.broadcast.UsageTimeCheckerReceiver
import com.black.code.contents.notification.NotificationUtil
import com.black.code.contents.usagetimechecker.UsageTimeCheckerView
import com.black.code.util.Log

class ForegroundService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 428
        private const val NOTIFICATION_CHANNEL_ID = "ForegroundService"
        private const val NOTIFICATION_CHANNEL_NAME = "Foreground Service"

        const val ACTION_ATTACH_USAGE_TIME_CHECKER = "ATTACH_USAGE_TIME_CHECKER"
        const val ACTION_DETACH_USAGE_TIME_CHECKER = "DETACH_USAGE_TIME_CHECKER"
        const val ACTION_STOP = "STOP"

        fun start(context: Context, onSetIntent: ((intent: Intent) -> Unit)? = null) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, ForegroundService::class.java)
            onSetIntent?.invoke(intent)
            appContext.startService(intent)
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, ForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            appContext.startService(intent)
        }
    }

    private var usageTimeCheckerReceiver : UsageTimeCheckerReceiver? = null
    private var usageTimeCheckerView : UsageTimeCheckerView? = null
    private var isStopEnabled = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("intent=$intent, flags=$flags, startId=$startId")

        when(intent?.action) {
            ACTION_ATTACH_USAGE_TIME_CHECKER -> {
                usageTimeCheckerView = UsageTimeCheckerView(this).also {
                    it.attachView()
                }
            }

            ACTION_DETACH_USAGE_TIME_CHECKER -> {
                usageTimeCheckerView ?: run {
                    Log.w("usageTimeCheckerView not attached")
                    return super.onStartCommand(intent, flags, startId)
                }
                usageTimeCheckerView?.detachView()
            }

            ACTION_STOP -> {
                isStopEnabled = true
                stopSelf()
            }
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

        usageTimeCheckerReceiver = UsageTimeCheckerReceiver.register(applicationContext)
    }

    override fun onDestroy() {
        Log.d()
        UsageTimeCheckerReceiver.unregister(applicationContext, usageTimeCheckerReceiver)
        super.onDestroy()

        // android 12 포그라운드 서비스 예외사항 : 배터리 최적화 예외 시 포그라운드 서비스 내에서 재시작 가능
        // https://developer.android.com/about/versions/12/foreground-services?hl=ko#cases-fgs-background-starts-allowed
        // 무한 재실행
        // https://medium.com/@Lakshya_Punhani/background-services-running-forever-in-android-part-2-6e3a667d36fd
        if (!isStopEnabled) {
            start(this)
        }
    }
}