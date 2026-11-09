package com.black.feature.floatingbutton.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.black.core.util.Log


class FloatingAccessibilityService: AccessibilityService() {
    companion object {
        const val ACTION_REQUEST_BACK = "com.black.feature_floatingbutton.action.REQUEST_BACK"

        /** 앱 자신에게만 전달되는 뒤로 가기 요청 인텐트 */
        fun createRequestBackIntent(context: Context): Intent =
            Intent(ACTION_REQUEST_BACK).setPackage(context.packageName)
    }

    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d()
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    /** 연결 전에 onUnbind가 불리면 해제할 리시버가 없으므로 등록 여부 기록 */
    private var isReceiverRegistered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d()
        // 다른 앱이 브로드캐스트로 전역 뒤로 가기를 실행하지 못하도록 앱 내부 전용 등록
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(ACTION_REQUEST_BACK),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isReceiverRegistered = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (isReceiverRegistered) {
            unregisterReceiver(receiver)
            isReceiverRegistered = false
        }
        return super.onUnbind(intent)
    }
}
