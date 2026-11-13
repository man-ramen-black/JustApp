package com.black.core.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.CallSuper
import com.black.core.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 사용자가 끄기 전까지 계속 실행하는 포그라운드 서비스의 기반
 *
 * stopService·시스템 종료로 멈추면 onDestroy에서 다시 시작하고, 끌 때는 [EXTRA_STOP]을 담은 중지 인텐트로만 멈춤.
 * 그래서 [stop]은 꺼진 서비스도 일단 시작한 뒤 바로 멈추게 함(중지 인텐트를 받을 서비스가 있어야 재시작하지 않음).
 * 재시작 조건이 다른 서비스는 [restartIfNeeded] 재정의
 */
abstract class BaseForegroundService: Service() {
    companion object {
        /** 이미 만든 바로가기 인텐트가 이 키를 담고 있어 값은 바꾸지 않음 */
        const val EXTRA_STOP = "ForegroundService.EXTRA_STOP"

        /** 서비스 클래스별 실행 여부(서비스 인스턴스가 없을 때도 구독할 수 있도록 companion에 보관) */
        private val runningStates = ConcurrentHashMap<Class<out BaseForegroundService>, MutableStateFlow<Boolean>>()

        /**
         * [cls] 서비스 실행 여부(onCreate의 포그라운드 시작 뒤 true, onDestroy에서 false)
         *
         * 앱 프로세스 메모리 값이라 서비스가 앱과 같은 프로세스에서 실행될 때만 유효(서비스 매니페스트에 android:process를 두면 바뀌지 않음)
         */
        fun running(cls: Class<out BaseForegroundService>): StateFlow<Boolean> = mutableRunning(cls).asStateFlow()

        private fun mutableRunning(cls: Class<out BaseForegroundService>): MutableStateFlow<Boolean> =
            runningStates.getOrPut(cls) { MutableStateFlow(false) }

        /** [cls] 서비스 시작(이미 실행 중이면 onStartCommand만 다시 호출) */
        fun start(context: Context, cls: Class<out BaseForegroundService>) {
            val appContext = context.applicationContext
            appContext.startService(Intent(appContext, cls))
        }

        fun stop(context: Context, cls: Class<out BaseForegroundService>) {
            val appContext = context.applicationContext
            appContext.startService(createStopIntent(context, cls))
        }

        fun createStopIntent(context: Context, cls: Class<out BaseForegroundService>): Intent {
            val appContext = context.applicationContext
            return Intent(appContext, cls)
                .apply { putExtra(EXTRA_STOP, true) }
        }
    }

    /** startForeground 타입 인자(Android 10 이상에서만 전달), ex. [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE] */
    abstract val type: Int

    /** 포그라운드 알림 ID(하위 서비스마다 고정값, 실행마다 바뀌면 알림 갱신·취소 대상을 찾을 수 없음) */
    protected abstract val notificationId: Int
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, onCreateNotification(), type)
        } else {
            startForeground(notificationId, onCreateNotification())
        }
        mutableRunning(this::class.java).value = true
    }

    @CallSuper
    override fun onDestroy() {
        Log.d()
        mutableRunning(this::class.java).value = false

        super.onDestroy()

        // android 12 포그라운드 서비스 예외사항 : 배터리 최적화 예외 시 포그라운드 서비스 내에서 재시작 가능
        // https://developer.android.com/about/versions/12/foreground-services?hl=ko#cases-fgs-background-starts-allowed
        // 무한 재실행
        // https://medium.com/@Lakshya_Punhani/background-services-running-forever-in-android-part-2-6e3a667d36fd
        restartIfNeeded()
    }

    /**
     * onDestroy 끝에서 부르는 재시작 처리(기본: [EXTRA_STOP] 중지 인텐트로 멈춘 경우가 아니면 바로 재시작)
     *
     * 저장된 실행 설정처럼 재시작 조건을 따로 확인하는 서비스가 재정의.
     * 서비스는 이미 종료된 뒤라 비동기 확인은 서비스 수명과 분리한 범위에서 실행
     */
    protected open fun restartIfNeeded() {
        if (!isStopping) {
            startService(Intent(this, this::class.java))
        }
    }
}