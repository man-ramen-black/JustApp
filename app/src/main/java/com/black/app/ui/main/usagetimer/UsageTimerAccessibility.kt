package com.black.app.ui.main.usagetimer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.black.core.util.Log

class UsageTimerAccessibility : AccessibilityService() {

    /**
     * 서비스 시작
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    /**
     * 이벤트 처리
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val message = "packageName : ${event.packageName}\nclassName : ${event.className}"
        Log.d(message)
    }

    /**
     * 서비스 중단됨
     */
    override fun onInterrupt() {
    }

    /**
     * 서비스 종료
     */
    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }
}