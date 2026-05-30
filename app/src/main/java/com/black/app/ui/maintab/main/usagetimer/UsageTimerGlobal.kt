package com.black.app.ui.maintab.main.usagetimer

import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.widget.Toast
import com.black.app.R
import com.black.app.broadcast.NotificationActionReceiver
import com.black.app.broadcast.ScreenReceiver
import com.black.app.model.UsageTimerModel
import com.black.app.model.preferences.ForegroundServicePreference
import com.black.app.ui.maintab.main.usagetimer.view.UsageTimerView
import com.black.core.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.lang.ref.WeakReference

/**
 * UsageTimerAccessibility, NotificationAction에서 UsageTimer 동작 구현
 * Created by jinhyuk.lee on 2022/04/29
 **/
object UsageTimerGlobal : NotificationActionReceiver.Interface, ScreenReceiver.Interface {
    private var usageTimerView : UsageTimerView? = null
    private var currentPackageName : String? = null
    private var preference :  WeakReference<ForegroundServicePreference>? = null
    private var model :  WeakReference<UsageTimerModel>? = null

    /** 앱 전환 시 짧은 시간에 발생하는 창 이벤트 버스트로 인한 깜빡임 방지용 지연 숨김 시간(ms) */
    const val HIDE_DEBOUNCE_MILLIS = 500L

    private val hideDebouncer = UsageTimerHideDebouncer(
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
        hideDelayMillis = HIDE_DEBOUNCE_MILLIS,
        onHide = { detachView() },
    )

    /**
     * 포그라운드 창 이벤트에 대한 타이머 동작 종류
     * SHOW 표시, HIDE 숨김, IGNORE 현재 상태 유지
     */
    enum class ForegroundAction { SHOW, HIDE, IGNORE }

    /**
     * 포그라운드 창 변경 이벤트 기준 타이머 동작 판정
     *
     * 선택 앱이면 표시(일시정지 중에는 숨김), 선택 목록에 없을 때는 실제 다른 앱(Activity)
     * 전환만 숨김 처리하고 IME·다이얼로그·오버레이 등 일시적 창은 현재 상태 유지(IGNORE)
     */
    fun resolveForegroundAction(
        isActivityWindow: Boolean,
        currentPackage: String?,
        selectedApps: List<String>,
        isPaused: Boolean
    ) : ForegroundAction {
        if (currentPackage == null) return ForegroundAction.IGNORE
        if (selectedApps.contains(currentPackage)) {
            return if (isPaused) ForegroundAction.HIDE else ForegroundAction.SHOW
        }
        return if (isActivityWindow) ForegroundAction.HIDE else ForegroundAction.IGNORE
    }

    /**
     * 선택 앱이 포그라운드가 될 때 타이머 표시
     */
    fun showForApp(context: Context, packageName: String) {
        if (isUsageTimerPaused(context)) {
            Log.d("UsageTimer paused")
            return
        }

        // 표시 요청 시 예약된 지연 숨김 취소(앱 전환 버스트로 인한 깜빡임 방지)
        hideDebouncer.cancel()

        // 같은 앱으로 이미 표시 중이면 재생성하지 않음(깜빡임 방지)
        if (usageTimerView != null && currentPackageName == packageName) {
            return
        }

        detachView()
        currentPackageName = packageName
        Log.d("showForApp attach : $packageName")
        try {
            usageTimerView = UsageTimerView(ContextThemeWrapper(context, R.style.Theme_Black)).also {
                it.attachView()
            }
        } catch (error: Exception) {
            Log.w("UsageTimerView attach 실패 : ${error.message}")
            usageTimerView = null
            currentPackageName = null
        }
    }

    /**
     * 선택 앱이 백그라운드로 갈 때 타이머 제거
     * 앱 전환 직후 표시가 재요청되는 깜빡임을 막기 위해 지연 숨김 예약
     */
    fun hideIfShown() {
        hideDebouncer.schedule()
    }

    fun detachView() {
        usageTimerView?.detachView()
    }

    /**
     * 표시 중인 타이머의 경과 시간 초기화
     */
    fun resetTimer() {
        usageTimerView?.restart()
    }

    /**
     * 화면이 켜질 때 타이머 초기화
     * 화면을 껐다 켜면 새 사용 세션으로 보고 경과 시간을 0부터 다시 시작
     */
    override fun onScreenOn(context: Context, intent: Intent) {
        resetTimer()
    }

    override fun onScreenOff(context: Context, intent: Intent) {
    }

    /**
     * 오버레이 뷰가 제거될 때 추적 상태 정리(close 버튼·외부 detach 공통 경로)
     */
    fun onViewDetached() {
        usageTimerView = null
        currentPackageName = null
    }

    fun isUsageTimerPaused(context: Context) : Boolean {
        val currentPreference = preference?.get() ?: ForegroundServicePreference(context).also {
            preference = WeakReference(it)
        }

        val endTime = currentPreference.getUsageTimerPauseEndTime()
        if (endTime == 0L) {
            return false
        }

        return System.currentTimeMillis() < endTime
    }

    override fun onNotificationAction(context: Context, intent: Intent): Boolean {
        if (intent.action == NotificationActionReceiver.ACTION_PAUSE_USAGE_TIMER) {
            pauseUsageTimerInNotificationAction(context)
            return true
        }
        return false
    }

    private fun pauseUsageTimerInNotificationAction(context: Context) {
        val model = model?.get() ?: WeakReference(UsageTimerModel(context)).also { model = it }.get()!!

        val pauseDuration = model.getPauseDuration()
        model.pause(pauseDuration)
        detachView()

        Toast.makeText(context, "UsageTimer paused", Toast.LENGTH_SHORT)
            .show()
    }
}
