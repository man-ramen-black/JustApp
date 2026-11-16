package com.black.app.sleeproutine

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.text.format.DateFormat
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.black.app.R
import com.black.app.model.SleepRoutineRepository
import com.black.app.ui.MainActivity
import com.black.core.service.BaseForegroundService
import com.black.core.service.ServiceActivityTarget
import com.black.core.util.Log
import com.black.core.util.NotificationUtil
import com.black.core.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * 수면 루틴 진행 포그라운드 서비스
 *
 * 바로가기가 ServiceActivity를 거쳐 시작하므로 [ServiceActivityTarget] 구현.
 * 진행 중에는 종료 시각까지 남은 시간 알림과 부분 wake lock을 유지(CPU가 잠들면 코루틴 대기가 멈춤).
 * 시스템이 프로세스를 정리하면 START_STICKY로 빈 인텐트와 함께 다시 시작되어 저장된 세션의 남은 대기와 마무리만 진행.
 * START_STICKY 재생성이 없는 앱 업데이트·재부팅 뒤에는 [SleepRoutineSessionRestorer]가 [createResumeIntent]로 같은 재개를 요청.
 *
 * 방해 금지가 켜진 채 남지 않도록 포그라운드 시작 거부, 중지 인텐트 없는 종료(stopService 등)에서도 마무리
 */
class SleepRoutineService : BaseForegroundService(), ServiceActivityTarget {
    companion object {
        private const val NOTIFICATION_ID = 1115
        private const val NOTIFICATION_CHANNEL_ID = "SleepRoutine"
        private const val WAKE_LOCK_TAG = "JustApp:SleepRoutine"

        /** 종료 시각 뒤 마무리까지 wake lock을 더 쥐는 여유 */
        private const val WAKE_LOCK_MARGIN_MILLIS = 60_000L

        /** 새로 시작하지 않고 저장된 세션의 남은 대기와 마무리만 진행하라는 표시 */
        @VisibleForTesting
        internal const val EXTRA_RESUME = "SleepRoutineService.EXTRA_RESUME"

        /**
         * 서비스 실행 여부
         *
         * 매니페스트에 android:process가 없어 앱 프로세스 안에서 유효.
         */
        val running: StateFlow<Boolean> = BaseForegroundService.running(SleepRoutineService::class.java)

        /** 서비스 종료 뒤에도 마무리와 세션 삭제를 마치도록 서비스 수명과 분리한 범위 */
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * 저장된 세션 재개 인텐트
         *
         * 서비스가 비공개이고 ServiceActivity는 중지 표시만 전달하므로 외부 앱은 재개 표시를 보낼 수 없음
         */
        fun createResumeIntent(context: Context): Intent {
            val appContext = context.applicationContext
            return Intent(appContext, SleepRoutineService::class.java)
                .putExtra(EXTRA_RESUME, true)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { SleepRoutineRepository(applicationContext) }
    private val runner by lazy {
        SleepRoutineRunner(SleepRoutineSystemActions(applicationContext), ::showFailureToast)
    }

    /** 시작·재개한 루틴 코루틴 */
    @VisibleForTesting
    internal var routineJob: Job? = null
        private set

    /** 마지막으로 시작한 정리 코루틴(마무리와 세션 삭제) */
    @VisibleForTesting
    internal var cleanupJob: Job? = null
        private set

    /**
     * 이 인스턴스에서 루틴 시작·재개 여부
     *
     * 중지 요청 때 바로 마무리할지 판단에 사용.
     */
    @VisibleForTesting
    internal var routineStarted = false
        private set

    /** onCreate의 포그라운드 시작 거부 여부 */
    private var foregroundStartFailed = false
    private var wakeLock: PowerManager.WakeLock? = null

    override val notificationId: Int = NOTIFICATION_ID

    override val type: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else {
        0
    }

    override fun onCreateNotification(): Notification = createNotification(endTimeMillis = null)

    /**
     * 포그라운드 시작 거부 대비
     *
     * Android 12 이상에서 백그라운드 START_STICKY 재생성 때 startForeground가 ForegroundServiceStartNotAllowedException(IllegalStateException 하위)을 던짐.
     * 그대로 두면 크래시 뒤 재생성이 반복되므로 표시만 남기고 [onStartCommand]에서 바로 마무리
     */
    override fun onCreate() {
        try {
            super.onCreate()
        } catch (exception: IllegalStateException) {
            Log.w(exception)
            foregroundStartFailed = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (foregroundStartFailed) {
            finishWithoutForeground()
            return result
        }
        if (isStopping) {
            stopRoutine()
            return result
        }
        if (routineJob?.isActive == true) {
            // 같은 바로가기를 다시 눌러 YouTube가 처음부터 다시 열리지 않도록 무시
            showToast(R.string.sleep_routine_already_running)
            return result
        }
        routineJob = scope.launch {
            // START_STICKY 재생성은 인텐트가 null, 앱 업데이트·재부팅 뒤 재개는 재개 표시
            if (intent == null || intent.getBooleanExtra(EXTRA_RESUME, false)) resumeRoutine() else startRoutine()
            stopSelf()
        }
        return result
    }

    private suspend fun startRoutine() {
        val savedEndTimeMillis = repository.getSessionEndTime()
        if (savedEndTimeMillis != null && savedEndTimeMillis > System.currentTimeMillis()) {
            // 프로세스 정리 뒤 START_STICKY 재생성 전에 바로가기를 누른 경우, 앱을 다시 실행하지 않고 남은 대기를 이어 감
            showToast(R.string.sleep_routine_already_running)
            resumeSession(savedEndTimeMillis)
            return
        }
        val settings = repository.getSettings()
        if (!SleepRoutineSettings.isValidYoutubeUrl(settings.youtubeUrl)) {
            showToast(R.string.sleep_routine_url_required)
            return
        }
        // Int 곱셈은 넘칠 수 있어 Long으로 계산
        val endTimeMillis = System.currentTimeMillis() + settings.durationMinutes * 60_000L
        // 저장에 실패해도 루틴은 진행(프로세스가 정리되면 재개할 세션이 없음)
        repository.startSession(endTimeMillis)
            .onFailure { Log.w("startSession failed : $it") }
        beginProgress(endTimeMillis)
        runner.start(
            youtubeUrl = settings.youtubeUrl,
            volumeLevel = settings.volumeLevel.takeIf { settings.volumeEnabled },
            endTimeMillis = endTimeMillis,
        )
        repository.clearSession()
    }

    private suspend fun resumeRoutine() {
        val endTimeMillis = repository.getSessionEndTime() ?: return
        resumeSession(endTimeMillis)
    }

    /** 앱 실행 단계 없이 [endTimeMillis]까지 기다린 뒤 마무리 */
    private suspend fun resumeSession(endTimeMillis: Long) {
        beginProgress(endTimeMillis)
        runner.resume(endTimeMillis)
        repository.clearSession()
    }

    private fun beginProgress(endTimeMillis: Long) {
        routineStarted = true
        acquireWakeLock(endTimeMillis)
        updateNotification(endTimeMillis)
    }

    /**
     * 알림·화면의 종료 요청과 중지 인텐트 없는 종료 처리
     *
     * 남은 대기를 취소하고 바로 마무리.
     * 프로세스 정리 뒤 START_STICKY 재시작 전에 요청이 오면 이 인스턴스가 시작한 루틴이 없으므로, 저장된 세션이 있을 때 마무리
     */
    private fun stopRoutine() {
        routineJob?.cancel()
        val finishedHere = routineStarted
        if (finishedHere) runner.finish()
        cleanupJob = cleanupScope.launch {
            if (!finishedHere) finishSavedSession()
            repository.clearSession()
        }
    }

    /**
     * 포그라운드 시작이 거부된 인스턴스의 처리
     *
     * 포그라운드가 아닌 서비스는 wake lock을 쥐어도 백그라운드에서 곧 시스템이 멈추므로, 남은 시간을 기다리지 않고 저장된 세션 기준으로 바로 마무리.
     * 정리를 마칠 때까지 서비스를 유지해 프로세스가 먼저 정리되지 않게 하고, 마친 뒤 stopSelf로 재생성 반복을 막음
     */
    private fun finishWithoutForeground() {
        if (cleanupJob?.isActive == true) return
        cleanupJob = cleanupScope.launch {
            finishSavedSession()
            repository.clearSession()
            withContext(Dispatchers.Main) { stopSelf() }
        }
    }

    /** 저장된 세션이 있으면 마무리 */
    private suspend fun finishSavedSession() {
        if (repository.getSessionEndTime() == null) return
        // 기기 동작은 루틴 코루틴과 같은 메인 스레드에서 실행
        withContext(Dispatchers.Main) { runner.finish() }
    }

    override fun onDestroy() {
        if (routineStarted && routineJob?.isActive == true) {
            // stopService처럼 중지 인텐트 없이 멈추면 루틴 코루틴이 취소만 되고 방해 금지가 켜진 채 남음
            stopRoutine()
        }
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    /**
     * 기본 재시작 끔
     *
     * 멈추면 바로 다시 시작하는 기본 동작은 끝난 루틴을 다시 시작함.
     * 프로세스 정리 뒤 재개는 START_STICKY 재시작이 담당
     */
    override fun restartIfNeeded() = Unit

    private fun acquireWakeLock(endTimeMillis: Long) {
        releaseWakeLock()
        val timeoutMillis = (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L) + WAKE_LOCK_MARGIN_MILLIS
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(timeoutMillis) }
    }

    private fun releaseWakeLock() {
        // 제한 시간이 지나 이미 풀린 wake lock을 release하면 예외
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createNotification(endTimeMillis: Long?): Notification {
        NotificationUtil.createNotificationChannel(
            this,
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.sleep_routine_notification_channel),
            NotificationManagerCompat.IMPORTANCE_LOW,
        )
        val title = getString(
            if (endTimeMillis == null) R.string.sleep_routine_notification_preparing else R.string.sleep_routine_notification_title
        )
        val message = endTimeMillis
            ?.let { getString(R.string.sleep_routine_notification_text, DateFormat.getTimeFormat(this).format(Date(it))) }
            .orEmpty()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, Intent(this, MainActivity::class.java), flags)
        // 포그라운드 서비스가 실행 중이라 백그라운드에서도 startService 허용
        val stopIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            createStopIntent(this, SleepRoutineService::class.java),
            flags,
        )
        return NotificationUtil.createNotification(this, NOTIFICATION_CHANNEL_ID, title, message) { builder ->
            builder.setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(contentIntent)
                .addAction(0, getString(R.string.sleep_routine_notification_stop), stopIntent)
            if (endTimeMillis != null) {
                // 남은 시간을 시스템이 그려 주기적으로 알림을 갱신하지 않음
                builder.setWhen(endTimeMillis)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
            }
        }
    }

    @SuppressLint("MissingPermission") // 알림 권한 확인 뒤 호출
    private fun updateNotification(endTimeMillis: Long) {
        if (!PermissionHelper.isNotificationPermissionGranted(this)) return
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, createNotification(endTimeMillis))
    }

    private fun showFailureToast(failure: SleepRoutineFailure) {
        showToast(
            when (failure) {
                SleepRoutineFailure.DO_NOT_DISTURB -> R.string.sleep_routine_failed_do_not_disturb
                SleepRoutineFailure.SLEEP_TRACKING -> R.string.sleep_routine_failed_sleep_tracking
                SleepRoutineFailure.YOUTUBE -> R.string.sleep_routine_failed_youtube
                SleepRoutineFailure.SCREEN_OFF -> R.string.sleep_routine_failed_screen_off
            }
        )
    }

    private fun showToast(@StringRes messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_LONG).show()
    }
}
