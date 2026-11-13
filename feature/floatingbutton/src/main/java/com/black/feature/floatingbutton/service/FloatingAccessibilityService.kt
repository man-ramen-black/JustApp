package com.black.feature.floatingbutton.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import com.black.core.broadcast.ReceiverRegistration
import com.black.core.util.Log


class FloatingAccessibilityService: AccessibilityService() {
    companion object {
        const val ACTION_REQUEST_BACK = "com.black.feature_floatingbutton.action.REQUEST_BACK"

        /** 앱 자신에게만 전달되는 뒤로 가기 요청 인텐트 */
        fun createRequestBackIntent(context: Context): Intent =
            Intent(ACTION_REQUEST_BACK).setPackage(context.packageName)
    }

    /** 연결 전에 onUnbind가 불리면 해제할 리시버가 없으므로 등록 여부는 [ReceiverRegistration]이 기록 */
    private val receiver = ReceiverRegistration(IntentFilter(ACTION_REQUEST_BACK)) { _, _ ->
        Log.d()
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d()
        // 다른 앱이 브로드캐스트로 전역 뒤로 가기를 실행하지 못하도록 앱 내부 전용 등록
        receiver.register(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        receiver.unregister(this)
        return super.onUnbind(intent)
    }
}
