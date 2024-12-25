package com.black.core.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.annotation.CallSuper
import com.black.core.util.Log
import kotlin.random.Random

abstract class ForegroundService: Service() {
    companion object {
        const val EXTRA_STOP = "ForegroundService.EXTRA_STOP"

        fun stop(context: Context, cls: Class<out ForegroundService>) {
            val appContext = context.applicationContext
            appContext.startService(createStopIntent(context, cls))
        }

        fun createStopIntent(context: Context, cls: Class<out ForegroundService>): Intent {
            val appContext = context.applicationContext
            return Intent(appContext, cls)
                .apply { putExtra(EXTRA_STOP, true) }
        }
    }

    /** ex. [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE] */
    abstract val type: Int
    protected var isStopping = false
        private set

    @CallSuper
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("intent=$intent, flags=$flags, startId=$startId")
        isStopping = false

        if (intent?.getBooleanExtra(EXTRA_STOP, false) == true) {
            this.isStopping = true
            stopSelf()
        }

        // onStartCommand default return : START_STICKY
        // https://developer.android.com/reference/android/app/Service#onStartCommand(android.content.Intent,%20int,%20int)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    abstract fun onCreateNotification(): Notification

    @CallSuper
    override fun onCreate() {
        Log.d()
        super.onCreate()

        startForeground(
            Random(System.currentTimeMillis()).nextInt(1, Int.MAX_VALUE),
            onCreateNotification(),
            type)
    }

    @CallSuper
    override fun onDestroy() {
        Log.d()

        super.onDestroy()

        // android 12 포그라운드 서비스 예외사항 : 배터리 최적화 예외 시 포그라운드 서비스 내에서 재시작 가능
        // https://developer.android.com/about/versions/12/foreground-services?hl=ko#cases-fgs-background-starts-allowed
        // 무한 재실행
        // https://medium.com/@Lakshya_Punhani/background-services-running-forever-in-android-part-2-6e3a667d36fd
        if (!isStopping) {
            startService(Intent(this, this::class.java))
        }
    }
}