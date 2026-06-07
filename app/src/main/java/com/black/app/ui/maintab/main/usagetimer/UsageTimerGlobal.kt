package com.black.app.ui.maintab.main.usagetimer

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.widget.Toast
import com.black.app.R
import com.black.app.broadcast.NotificationActionReceiver
import com.black.app.broadcast.ScreenReceiver
import com.black.app.model.UsageTimerRepository
import com.black.app.ui.maintab.main.usagetimer.view.UsageTimerView
import com.black.core.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * UsageTimerAccessibility, NotificationAction에서 UsageTimer 동작 구현
 * Created by jinhyuk.lee on 2022/04/29
 **/
object UsageTimerGlobal : NotificationActionReceiver.Interface, ScreenReceiver.Interface {
    private var usageTimerView : UsageTimerView? = null
    private var currentPackageName : String? = null
    private var repository :  WeakReference<UsageTimerRepository>? = null

    /** 앱 전환 시 짧은 시간에 발생하는 창 이벤트 버스트로 인한 깜빡임 방지용 지연 숨김 시간(ms) */
    const val HIDE_DEBOUNCE_MILLIS = 500L

    /** 숨김 후 같은 앱으로 복귀 시 세션을 이어가는 유예 시간(ms). 권한 다이얼로그·공유 시트 등 일시적 창 전환 흡수 */
    const val SESSION_GRACE_MILLIS = 10_000L

    /** 세션 시작 시각(elapsedRealtime ms). 진행 중 세션 없으면 null */
    private var sessionStartElapsedMillis: Long? = null

    /** 세션 앱 패키지명. 유예 복귀 판정 기준 */
    private var sessionPackageName: String? = null

    /** 뷰가 숨겨진 시각(elapsedRealtime ms). 유예 판정 기준, 표시 중이면 null */
    private var sessionHiddenElapsedMillis: Long? = null

    /** 닫기 버튼으로 닫은 앱 패키지명. 같은 앱에 머무는 동안 재표시 차단 기준, 없으면 null */
    private var dismissedPackageName: String? = null

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var hideJob: Job? = null

    /**
     * 타이머 동작 종류
     * SHOW 표시, HIDE 숨김, IGNORE 현재 상태 유지
     */
    enum class TimerAction { SHOW, HIDE, IGNORE }

    /**
     * 현재 활성 앱 기준 타이머 동작 결정
     * - 선택 앱: 표시(일시정지 중이면 숨김)
     * - 다른 앱: 숨김
     * - 활성 앱 불명(activePackage null): 현재 상태 유지
     */
    fun resolveTimerAction(
        activePackage: String?,
        selectedApps: List<String>,
        isPaused: Boolean
    ) : TimerAction {
        activePackage ?: return TimerAction.IGNORE
        if (selectedApps.contains(activePackage)) {
            return if (isPaused) TimerAction.HIDE else TimerAction.SHOW
        }
        return TimerAction.HIDE
    }

    /**
     * 표시 요청 시 사용할 세션 시작 시각 결정
     * - 같은 앱 표시 중이거나 숨김 후 유예 내 복귀: 기존 세션 시작 시각 유지
     * - 첫 표시·다른 앱·유예 초과: 현재 시각으로 새 세션 시작
     *
     * @param requestPackageName 표시를 요청한 앱 패키지명
     * @param sessionPackageName 진행 중인 세션의 앱 패키지명(세션 없으면 null)
     * @param sessionStartElapsedMillis 진행 중인 세션의 시작 시각(elapsedRealtime ms, 세션 없으면 null)
     * @param sessionHiddenElapsedMillis 세션이 숨겨진 시각(elapsedRealtime ms, 표시 중이면 null)
     * @param nowElapsedMillis 현재 시각(elapsedRealtime ms)
     * @return 적용할 세션 시작 시각(elapsedRealtime ms)
     */
    fun resolveSessionStart(
        requestPackageName: String,
        sessionPackageName: String?,
        sessionStartElapsedMillis: Long?,
        sessionHiddenElapsedMillis: Long?,
        nowElapsedMillis: Long
    ) : Long {
        if (requestPackageName != sessionPackageName) return nowElapsedMillis
        sessionStartElapsedMillis ?: return nowElapsedMillis
        if (sessionHiddenElapsedMillis == null) return sessionStartElapsedMillis
        return if (nowElapsedMillis - sessionHiddenElapsedMillis <= SESSION_GRACE_MILLIS) {
            sessionStartElapsedMillis
        } else {
            nowElapsedMillis
        }
    }

    /**
     * 활성 앱 확정 시 유지할 닫은 앱 패키지 결정
     * - 닫은 앱이 그대로 활성: 닫기 유지(같은 앱 내 창 전환에 재표시하지 않음)
     * - 다른 앱이 활성으로 확정: 닫기 해제(이후 닫은 앱 복귀 시 재표시)
     * - 활성 앱 불명(activePackage null): 현재 상태 유지
     *
     * @param activePackage 현재 활성 앱 패키지명(불명이면 null)
     * @param dismissedPackage 닫은 앱 패키지명(없으면 null)
     * @return 유지할 닫은 앱 패키지명(해제 시 null)
     */
    fun resolveDismissedPackage(activePackage: String?, dismissedPackage: String?) : String? {
        dismissedPackage ?: return null
        activePackage ?: return dismissedPackage
        return if (activePackage == dismissedPackage) dismissedPackage else null
    }

    /**
     * 선택 앱이 포그라운드가 될 때 타이머 표시
     * 세션 시작 시각은 UsageTimerGlobal이 보유, 뷰는 시작 시각을 주입받아 렌더링만 담당
     */
    suspend fun showForApp(context: Context, packageName: String) {
        if (isUsageTimerPaused(context)) {
            Log.d("UsageTimer paused")
            return
        }

        // 표시 요청 시 예약된 지연 숨김 취소(앱 전환 버스트로 인한 깜빡임 방지)
        cancelHide()

        // 닫은 앱이 그대로 활성이면 재표시 차단, 다른 앱이면 닫기 해제 후 표시 진행
        dismissedPackageName = resolveDismissedPackage(packageName, dismissedPackageName)
        if (dismissedPackageName != null) {
            Log.d("UsageTimer dismissed : $packageName")
            return
        }

        val now = SystemClock.elapsedRealtime()
        val sessionStart = resolveSessionStart(
            packageName, sessionPackageName, sessionStartElapsedMillis, sessionHiddenElapsedMillis, now
        )
        sessionPackageName = packageName
        sessionStartElapsedMillis = sessionStart
        sessionHiddenElapsedMillis = null

        // 표시 중이면 뷰 재생성 없이 세션 기준점만 반영(깜빡임 방지)
        usageTimerView?.let {
            if (currentPackageName != packageName) {
                currentPackageName = packageName
                it.startFrom(sessionStart)
            }
            return
        }

        currentPackageName = packageName
        Log.d("showForApp attach : $packageName, sessionStart : $sessionStart")
        try {
            usageTimerView = UsageTimerView(ContextThemeWrapper(context, R.style.Theme_Black)).also {
                it.baseElapsedRealtime = sessionStart
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
     * 앱 전환 직후 표시가 재요청되는 깜빡임을 막기 위해 지연 숨김 예약(이미 예약 중이면 유지)
     * 디바운스를 통과해 다른 앱이 활성으로 확정되면 닫기 해제(닫은 앱 복귀 시 재표시 허용)
     */
    fun hideIfShown(activePackage: String?) {
        if (hideJob?.isActive == true) return
        hideJob = mainScope.launch {
            delay(HIDE_DEBOUNCE_MILLIS)
            detachView()
            dismissedPackageName = resolveDismissedPackage(activePackage, dismissedPackageName)
        }
    }

    /**
     * 예약된 지연 숨김 취소(표시 요청 시 깜빡임 방지)
     */
    private fun cancelHide() {
        hideJob?.cancel()
        hideJob = null
    }

    fun detachView() {
        usageTimerView?.detachView()
    }

    /**
     * 닫기 버튼으로 타이머 제거
     * 같은 앱에 머무는 동안 재표시하지 않도록 닫은 앱 패키지 기록(다른 앱 전환 확정 시 해제)
     */
    fun closeByUser() {
        dismissedPackageName = currentPackageName
        detachView()
    }

    /**
     * 표시 중인 타이머의 경과 시간 초기화
     */
    fun resetTimer() {
        if (sessionStartElapsedMillis != null) {
            sessionStartElapsedMillis = SystemClock.elapsedRealtime()
        }
        usageTimerView?.restart()
    }

    /**
     * 세션 상태 폐기. 다음 표시 요청은 새 세션으로 시작
     */
    private fun clearSession() {
        sessionPackageName = null
        sessionStartElapsedMillis = null
        sessionHiddenElapsedMillis = null
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
     * 유예 내 같은 앱 복귀 시 세션을 이어가도록 숨김 시각 기록
     */
    fun onViewDetached() {
        usageTimerView = null
        currentPackageName = null
        sessionHiddenElapsedMillis = SystemClock.elapsedRealtime()
    }

    suspend fun isUsageTimerPaused(context: Context) : Boolean {
        val endTime = usageTimerRepository(context).getPauseEndTime()
        if (endTime == 0L) {
            return false
        }

        return System.currentTimeMillis() < endTime
    }

    override fun onNotificationAction(context: Context, intent: Intent): Boolean {
        if (intent.action == NotificationActionReceiver.ACTION_PAUSE_USAGE_TIMER) {
            mainScope.launch { pauseUsageTimerInNotificationAction(context) }
            return true
        }
        return false
    }

    private suspend fun pauseUsageTimerInNotificationAction(context: Context) {
        val repository = usageTimerRepository(context)

        val pauseDuration = repository.getPauseDuration()
        repository.pause(pauseDuration)
        detachView()
        clearSession()

        Toast.makeText(context, "UsageTimer paused", Toast.LENGTH_SHORT)
            .show()
    }

    /** 약참조로 캐시된 UsageTimerRepository 반환(없으면 생성 후 캐시) */
    private fun usageTimerRepository(context: Context): UsageTimerRepository {
        return repository?.get() ?: UsageTimerRepository(context).also { repository = WeakReference(it) }
    }
}
