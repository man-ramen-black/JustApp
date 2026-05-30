package com.black.app.ui.maintab.main.usagetimer

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import com.black.app.model.UsageTimerModel
import com.black.app.ui.maintab.main.usagetimer.UsageTimerGlobal.ForegroundAction
import com.black.core.util.Log

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

        val packageName = event.packageName?.toString()
        val isActivityWindow = isActivityWindow(packageName, event.className?.toString())

        val selectedApps = UsageTimerModel(this).getSelectedApps()
        val isPaused = UsageTimerGlobal.isUsageTimerPaused(this)
        val action = UsageTimerGlobal.resolveForegroundAction(isActivityWindow, packageName, selectedApps, isPaused)
        Log.d("packageName=$packageName, isActivityWindow=$isActivityWindow, selectedApps=$selectedApps, isPaused=$isPaused, action=$action")
        when (action) {
            ForegroundAction.SHOW -> packageName?.let { UsageTimerGlobal.showForApp(this, it) }
            ForegroundAction.HIDE -> UsageTimerGlobal.hideIfShown()
            ForegroundAction.IGNORE -> {}
        }
    }

    /**
     * 이벤트 창이 실제 Activity인지 여부(IME·다이얼로그·오버레이 등 비-Activity 창 구분)
     */
    private fun isActivityWindow(packageName: String?, className: String?) : Boolean {
        if (packageName == null || className == null) return false
        return try {
            packageManager.getActivityInfo(ComponentName(packageName, className), 0)
            true
        } catch (notFound: PackageManager.NameNotFoundException) {
            false
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
