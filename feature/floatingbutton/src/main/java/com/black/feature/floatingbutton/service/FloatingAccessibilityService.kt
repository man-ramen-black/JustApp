package com.black.feature.floatingbutton.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import com.black.core.util.Log


class FloatingAccessibilityService: AccessibilityService() {
    companion object {
        const val ACTION_REQUEST_BACK = "com.black.feature_floatingbutton.action.REQUEST_BACK"
    }

    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d()
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d()
        registerReceiver(receiver, IntentFilter(ACTION_REQUEST_BACK), Context.RECEIVER_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        unregisterReceiver(receiver)
        return super.onUnbind(intent)
    }
}