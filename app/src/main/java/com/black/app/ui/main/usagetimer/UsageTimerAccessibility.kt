package com.black.app.ui.main.usagetimer

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.black.app.model.UsageTimerRepository
import com.black.app.ui.main.usagetimer.UsageTimerGlobal.TimerAction
import com.black.core.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@SuppressLint("AccessibilityPolicy")
class UsageTimerAccessibility : AccessibilityService() {

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val repository by lazy { UsageTimerRepository(this) }

    /** 이벤트 발생 순서 보장을 위한 직전 처리 작업 */
    private var handleJob: Job? = null

    /** 화면을 껐다 켜면 타이머를 0부터 다시 세도록 서비스 수명 동안 받는 화면 켜짐 신호(앱 설치 알림 서비스와 무관) */
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                UsageTimerGlobal.resetTimer()
            }
        }
    }
    private var isScreenOnReceiverRegistered = false

    /** 서비스 시작 */
    override fun onServiceConnected() {
        super.onServiceConnected()
        // 시스템 보호 브로드캐스트라 NOT_EXPORTED여도 수신하고 다른 앱은 보낼 수 없음
        ContextCompat.registerReceiver(
            this,
            screenOnReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isScreenOnReceiverRegistered = true
    }

    /** 포그라운드 앱 전환 이벤트 처리 */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // 이벤트의 packageName·className은 현재 포그라운드를 신뢰성 있게 반영하지 못해(늦은 이벤트·비-Activity className)
        // 실제 활성 창(rootInActiveWindow)의 패키지로 판정
        val activePackage = rootInActiveWindow?.packageName?.toString()
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
        val isPaused = UsageTimerGlobal.isUsageTimerPaused(this)
        val action = UsageTimerGlobal.resolveTimerAction(activePackage, selectedApps, isPaused)
        Log.d("activePackage=$activePackage, selectedApps=$selectedApps, isPaused=$isPaused, action=$action")
        when (action) {
            TimerAction.SHOW -> activePackage?.let { UsageTimerGlobal.showForApp(this, it) }
            TimerAction.HIDE -> UsageTimerGlobal.scheduleHide(activePackage)
            TimerAction.IGNORE -> {}
        }
    }

    /** 서비스 중단됨 */
    override fun onInterrupt() {
    }

    /** 서비스 연결 해제 */
    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    /** 서비스 종료 시 처리 중 작업, 화면 켜짐 수신, 떠 있는 타이머와 세션 정리 */
    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        if (isScreenOnReceiverRegistered) {
            unregisterReceiver(screenOnReceiver)
            isScreenOnReceiverRegistered = false
        }
        UsageTimerGlobal.reset()
    }
}
