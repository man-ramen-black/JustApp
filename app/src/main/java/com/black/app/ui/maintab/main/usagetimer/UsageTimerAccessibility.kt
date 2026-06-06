package com.black.app.ui.maintab.main.usagetimer

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.black.app.model.UsageTimerModel
import com.black.app.ui.maintab.main.usagetimer.UsageTimerGlobal.TimerAction
import com.black.core.util.Log

@SuppressLint("AccessibilityPolicy")
class UsageTimerAccessibility : AccessibilityService() {

    /**
     * 서비스 시작
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    /**
     * 포그라운드 앱 전환 이벤트 처리
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // 이벤트의 packageName·className은 현재 포그라운드를 신뢰성 있게 반영하지 못해(늦은 이벤트·비-Activity className)
        // 실제 활성 창(rootInActiveWindow)의 패키지로 판정
        val activePackage = rootInActiveWindow?.packageName?.toString()
        val selectedApps = UsageTimerModel(this).getSelectedApps()
        val isPaused = UsageTimerGlobal.isUsageTimerPaused(this)
        val action = UsageTimerGlobal.resolveTimerAction(activePackage, selectedApps, isPaused)
        Log.d("activePackage=$activePackage, selectedApps=$selectedApps, isPaused=$isPaused, action=$action")
        when (action) {
            TimerAction.SHOW -> activePackage?.let { UsageTimerGlobal.showForApp(this, it) }
            TimerAction.HIDE -> UsageTimerGlobal.hideIfShown()
            TimerAction.IGNORE -> {}
        }
    }

    /**
     * 서비스 중단됨
     */
    override fun onInterrupt() {
    }

    /**
     * 서비스 연결 해제
     */
    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }
}
