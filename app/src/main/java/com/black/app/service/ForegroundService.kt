package com.black.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.black.app.broadcast.NotificationActionReceiver
import com.black.app.broadcast.ScreenReceiver
import com.black.app.model.preferences.ForegroundServicePreference
import com.black.app.ui.MainActivity
import com.black.app.util.Log
import com.black.app.util.NotificationUtil
import com.black.app.util.PermissionHelper

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
            val appContext = context.applicationContext

            if (!ForegroundServicePreference(appContext).getForegroundServiceActivated()) {
                Log.d("ForegroundService not activated")
                return false
            }

            val isIgnored = PermissionHelper.isIgnoringBatteryOptimizations(appContext)
            if (!isIgnored) {
                Log.d("BatteryOptimizations not ignored")
                if (requestPermission) {
                    PermissionHelper.showIgnoreBatteryOptimizationSettings(appContext, true)
                }
                return false
            }

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

    private val interfaces : List<Interface> = listOf(
        ScreenReceiver.ServiceInterface,
        NotificationActionReceiver.ForegroundServiceInterface
    )

    private var isStopped = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("intent=$intent, flags=$flags, startId=$startId")
        isStopped = false
        interfaces.forEach {
            it.onForegroundServiceStartCommand(this, intent ?: return@forEach, flags, startId)
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
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_CANCEL_CURRENT
            }
            it.setContentIntent(PendingIntent.getActivity(this, NOTIFICATION_ID, Intent(this, MainActivity::class.java), flags))
            it.addAction(NotificationCompat.Action(0, "PauseTimer", NotificationActionReceiver.getNotificationActionPendingIntent(this, NotificationActionReceiver.ACTION_PAUSE_USAGE_TIMER)))
        }
        startForeground(NOTIFICATION_ID, notification)

        interfaces.forEach { it.onForegroundServiceCreate(this) }
    }

    override fun onDestroy() {
        Log.d()
        interfaces.forEach { it.onForegroundServiceDestroy(this) }

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
        fun onForegroundServiceStartCommand(context: Context, intent: Intent, flags: Int, startId: Int)
        fun onForegroundServiceCreate(context: Context)
        fun onForegroundServiceDestroy(context: Context)
    }
}