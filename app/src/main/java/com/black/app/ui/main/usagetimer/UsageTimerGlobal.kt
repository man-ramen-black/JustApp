package com.black.app.ui.main.usagetimer

import android.content.Context
import android.os.SystemClock
import android.view.ContextThemeWrapper
import com.black.app.R
import com.black.app.model.UsageTimerRepository
import com.black.app.ui.main.usagetimer.view.UsageTimerView
import com.black.core.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UsageTimerAccessibility에서 UsageTimer 동작 구현
 * Created by jinhyuk.lee on 2022/04/29
 **/
object UsageTimerGlobal {
    private var usageTimerView : UsageTimerView? = null
    private var currentPackageName : String? = null

    /** 앱 전환 시 짧은 시간에 발생하는 창 이벤트 버스트로 인한 깜빡임 방지용 지연 숨김 시간(ms) */
    const val HIDE_DEBOUNCE_MILLIS = 500L

    /**
     * 숨김 후 같은 앱으로 복귀 시 세션과 닫기를 이어가는 유예 시간(ms)
     *
     * 권한 다이얼로그·공유 시트·알림창 등 일시적 창 전환을 흡수
     */
    const val SESSION_GRACE_MILLIS = 10_000L

    /** 세션 시작 시각(elapsedRealtime ms). 진행 중 세션 없으면 null */
    private var sessionStartElapsedMillis: Long? = null

    /** 세션 앱 패키지명. 유예 복귀 판정 기준 */
    private var sessionPackageName: String? = null

    /** 뷰가 숨겨진 시각(elapsedRealtime ms). 유예 판정 기준, 표시 중이면 null */
    private var sessionHiddenElapsedMillis: Long? = null

    /** 닫기 버튼으로 닫은 앱 패키지명. 같은 앱에 머물거나 유예 안에 돌아오는 동안 재표시 차단 기준, 없으면 null */
    private var dismissedPackageName: String? = null

    /** 닫은 앱을 떠나 다른 창이 활성으로 확정된 시각(elapsedRealtime ms). 닫은 앱에 머무는 중이면 null */
    private var dismissedLeftElapsedMillis: Long? = null

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var hideJob: Job? = null

    /** 앱 화면에서 띄운 미리보기 타이머. 선택 앱 세션과 별개로 하나만 유지 */
    private var previewView: UsageTimerView? = null

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
     * 활성 창 확정 시 닫은 앱을 떠난 시각 결정
     * - 닫은 앱 없음: null
     * - 활성 앱 불명(activePackage null): 현재 값 유지
     * - 닫은 앱이 그대로 활성: null(머무는 중)
     * - 다른 창이 활성: 처음 떠난 시각 유지, 없으면 지금
     *
     * @param activePackage 현재 활성 앱 패키지명(불명이면 null)
     * @param dismissedPackage 닫은 앱 패키지명(없으면 null)
     * @param dismissedLeftElapsedMillis 이미 기록된 떠난 시각(elapsedRealtime ms, 없으면 null)
     * @param nowElapsedMillis 현재 시각(elapsedRealtime ms)
     * @return 기록할 떠난 시각(머무는 중이거나 닫은 앱이 없으면 null)
     */
    fun resolveDismissedLeftAt(
        activePackage: String?,
        dismissedPackage: String?,
        dismissedLeftElapsedMillis: Long?,
        nowElapsedMillis: Long
    ) : Long? {
        dismissedPackage ?: return null
        activePackage ?: return dismissedLeftElapsedMillis
        if (activePackage == dismissedPackage) return null
        return dismissedLeftElapsedMillis ?: nowElapsedMillis
    }

    /**
     * 표시 요청 시 닫기를 유지할지 결정
     * - 닫은 앱이 아닌 앱의 요청: 해제
     * - 닫은 앱에 머무는 중이거나 떠난 뒤 유예 안에 복귀: 유지(공유 시트·알림창 같은 일시 창 전환 흡수)
     * - 유예를 넘겨 복귀: 해제
     *
     * @param requestPackageName 표시를 요청한 앱 패키지명
     * @param dismissedPackage 닫은 앱 패키지명(없으면 null)
     * @param dismissedLeftElapsedMillis 닫은 앱을 떠난 시각(elapsedRealtime ms, 머무는 중이면 null)
     * @param nowElapsedMillis 현재 시각(elapsedRealtime ms)
     * @return 닫기를 유지해 표시하지 않으면 true
     */
    fun isDismissedOnShow(
        requestPackageName: String,
        dismissedPackage: String?,
        dismissedLeftElapsedMillis: Long?,
        nowElapsedMillis: Long
    ) : Boolean {
        if (requestPackageName != dismissedPackage) return false
        dismissedLeftElapsedMillis ?: return true
        return nowElapsedMillis - dismissedLeftElapsedMillis <= SESSION_GRACE_MILLIS
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

        // 닫은 앱에 머물거나 유예 안에 돌아오면 재표시 차단, 그 밖에는 닫기 해제 후 표시 진행
        val now = SystemClock.elapsedRealtime()
        if (isDismissedOnShow(packageName, dismissedPackageName, dismissedLeftElapsedMillis, now)) {
            // 닫은 앱으로 돌아왔으므로 다시 머무는 상태
            dismissedLeftElapsedMillis = null
            Log.d("UsageTimer dismissed : $packageName")
            return
        }
        dismissedPackageName = null
        dismissedLeftElapsedMillis = null

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
        val view = UsageTimerView(ContextThemeWrapper(context, R.style.Theme_Black))
            .apply { baseElapsedRealtime = sessionStart }
        if (view.tryAttachView()) {
            usageTimerView = view
        } else {
            currentPackageName = null
        }
    }

    /**
     * 선택 앱이 아닌 창이 활성이 되면 지연 숨김 예약(이미 예약 중이면 유지, 타이머가 없어도 예약)
     *
     * 앱 전환 직후 표시가 재요청되는 깜빡임을 막기 위해 바로 떼지 않음.
     * 디바운스를 통과해 활성 창이 확정되면 타이머를 떼고 닫은 앱을 떠난 시각을 기록(유예 안에 돌아오면 닫기 유지)
     */
    fun scheduleHide(activePackage: String?) {
        if (hideJob?.isActive == true) return
        hideJob = mainScope.launch {
            delay(HIDE_DEBOUNCE_MILLIS)
            detachView()
            dismissedLeftElapsedMillis = resolveDismissedLeftAt(
                activePackage, dismissedPackageName, dismissedLeftElapsedMillis, SystemClock.elapsedRealtime()
            )
        }
    }

    /** 예약된 지연 숨김 취소(표시 요청 시 깜빡임 방지) */
    private fun cancelHide() {
        hideJob?.cancel()
        hideJob = null
    }

    /** 선택 앱 타이머 제거(앱 전환 숨김) */
    fun detachView() {
        usageTimerView?.detachView()
    }

    /** 선택 앱 타이머와 앱 화면에서 띄운 미리보기 타이머를 함께 제거(일시정지) */
    fun detachAllViews() {
        detachView()
        previewView?.detachView()
    }

    /**
     * 앱 화면에서 타이머 미리보기 표시(이미 떠 있으면 다시 붙이지 않음)
     *
     * TYPE_APPLICATION_OVERLAY 창은 Activity가 파괴돼도 걷히지 않아 Activity context로 만들면 Activity를 붙잡으므로 applicationContext로 생성
     */
    fun showPreview(context: Context) {
        if (previewView != null) {
            return
        }
        val view = UsageTimerView(ContextThemeWrapper(context.applicationContext, R.style.Theme_Black))
        if (view.tryAttachView()) {
            previewView = view
        }
    }

    /**
     * 닫기 버튼으로 타이머 제거
     * 같은 앱에 머무는 동안 재표시하지 않도록 닫은 앱 패키지 기록
     * (다른 창으로 떠난 뒤 유예를 넘겨 돌아오거나 다른 선택 앱이 표시되면 해제)
     * 앱 화면 SHOW로 띄운 타이머처럼 추적하지 않는 뷰는 닫기 기록 없이 그 뷰만 제거
     */
    fun closeByUser(view: UsageTimerView) {
        if (view === usageTimerView) {
            dismissedPackageName = currentPackageName
            dismissedLeftElapsedMillis = null
        }
        view.detachView()
    }

    /**
     * 표시 중인 타이머의 경과 시간 초기화
     *
     * 화면을 껐다 켜면 새 사용 세션으로 보고 경과 시간을 0부터 다시 시작하도록 [UsageTimerAccessibility]가 화면이 켜질 때 호출
     */
    fun resetTimer() {
        if (sessionStartElapsedMillis != null) {
            sessionStartElapsedMillis = SystemClock.elapsedRealtime()
        }
        usageTimerView?.restart()
    }

    /** 세션 상태 폐기. 다음 표시 요청은 새 세션으로 시작 */
    private fun clearSession() {
        sessionPackageName = null
        sessionStartElapsedMillis = null
        sessionHiddenElapsedMillis = null
    }

    /**
     * 뷰·세션·닫기·지연 숨김 상태를 모두 초기화
     *
     * 접근성 서비스가 꺼질 때 서비스 context를 가진 뷰와 세션이 전역 object에 남지 않도록 호출(테스트 격리에도 사용)
     */
    fun reset() {
        cancelHide()
        usageTimerView?.detachView()
        previewView?.detachView()
        usageTimerView = null
        previewView = null
        currentPackageName = null
        dismissedPackageName = null
        dismissedLeftElapsedMillis = null
        clearSession()
    }

    /**
     * 오버레이 뷰가 제거될 때 추적 상태 정리(close 버튼·외부 detach 공통 경로)
     * 유예 내 같은 앱 복귀 시 세션을 이어가도록 숨김 시각 기록
     * 추적하지 않는 뷰(앱 화면 SHOW 타이머)의 제거는 표시 중인 선택 앱 타이머 추적을 끊지 않도록 무시
     */
    fun onViewDetached(view: UsageTimerView) {
        if (view === previewView) {
            previewView = null
            return
        }
        if (view !== usageTimerView) return
        usageTimerView = null
        currentPackageName = null
        sessionHiddenElapsedMillis = SystemClock.elapsedRealtime()
    }

    suspend fun isUsageTimerPaused(context: Context) : Boolean {
        return usageTimerRepository(context).isPaused()
    }

    /** 접근성 서비스 context를 저장소가 붙잡지 않도록 applicationContext로 생성 */
    private fun usageTimerRepository(context: Context): UsageTimerRepository {
        return UsageTimerRepository(context.applicationContext)
    }
}
