package com.black.feature.usagetimer.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.black.core.util.Log
import com.black.feature.usagetimer.data.UsageTimerRepository
import com.black.feature.usagetimer.overlay.UsageTimerGlobal
import com.black.feature.usagetimer.overlay.UsageTimerGlobal.TimerAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 앱 접근성 서비스가 받은 창 전환으로 사용 타이머를 띄우고 숨기는 처리
 *
 * 앱 모듈의 `AppAccessibilityService`가 연결·이벤트·종료 시점에 [start]·[handleEvent]·[stop]으로 호출.
 *
 * @param service 활성 창 조회와 타이머 표시에 쓰는 접근성 서비스
 */
class UsageTimerWindowHandler(private val service: AccessibilityService) {

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val repository by lazy { UsageTimerRepository(service) }

    /** 이벤트 발생 순서 보장을 위한 직전 처리 작업 */
    private var handleJob: Job? = null

    /** 화면을 껐다 켜면 타이머를 0부터 다시 세도록 서비스 수명 동안 받는 화면 켜짐 신호(앱 설치 알림 서비스와 무관) */
    private val screenOnReceiver = UsageTimerGlobal.createScreenOnReceiver()

    /** 서비스 연결 시 화면 켜짐 수신 시작 */
    fun start() {
        screenOnReceiver.register(service)
    }

    /** 포그라운드 앱 전환 이벤트 처리 */
    fun handleEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        // 이벤트의 packageName·className은 현재 포그라운드를 신뢰성 있게 반영하지 못해(늦은 이벤트·비-Activity className)
        // 실제 활성 창(rootInActiveWindow)의 패키지로 판정
        val activePackage = service.rootInActiveWindow?.packageName?.toString()
        // 저장소 조회 중 다음 이벤트가 추월하지 않도록 직전 작업 완료 후 순차 처리
        val previousJob = handleJob
        handleJob = mainScope.launch {
            previousJob?.join()
            handleActivePackage(activePackage)
        }
    }

    /** 활성 앱 기준 타이머 표시·숨김 처리 */
    private suspend fun handleActivePackage(activePackage: String?) {
        val selectedApps = repository.getSelectedApps()
        val isPaused = UsageTimerGlobal.isUsageTimerPaused(service)
        val action = UsageTimerGlobal.resolveTimerAction(activePackage, selectedApps, isPaused)
        Log.d("activePackage=$activePackage, selectedApps=$selectedApps, isPaused=$isPaused, action=$action")
        when (action) {
            TimerAction.SHOW -> activePackage?.let { UsageTimerGlobal.showForApp(service, it) }
            TimerAction.HIDE -> UsageTimerGlobal.scheduleHide(activePackage)
            TimerAction.IGNORE -> {}
        }
    }

    /** 서비스 종료 시 처리 중 작업, 화면 켜짐 수신, 떠 있는 타이머와 세션 정리 */
    fun stop() {
        mainScope.cancel()
        screenOnReceiver.unregister(service)
        UsageTimerGlobal.reset()
    }
}
