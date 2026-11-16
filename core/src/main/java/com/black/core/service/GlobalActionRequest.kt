package com.black.core.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.black.core.broadcast.ReceiverRegistration
import com.black.core.util.Log

/**
 * 앱의 접근성 서비스에 전역 동작(뒤로 가기, 화면 잠금)을 요청하는 앱 내부 브로드캐스트
 *
 * 접근성 서비스는 앱 모듈에 하나만 두고 기능 모듈은 그 클래스를 모르므로, 요청은 이 인텐트로 보내고 서비스는 [createReceiver]로 받음.
 * 다른 앱이 전역 동작을 실행하지 못하도록 패키지를 지정해 보내고 앱 내부 전용으로 등록
 */
object GlobalActionRequest {
    private const val ACTION_BACK = "com.black.core.action.REQUEST_GLOBAL_BACK"
    private const val ACTION_LOCK_SCREEN = "com.black.core.action.REQUEST_GLOBAL_LOCK_SCREEN"

    /** 뒤로 가기 요청 인텐트 */
    fun createBackIntent(context: Context): Intent =
        Intent(ACTION_BACK).setPackage(context.packageName)

    /**
     * 화면 잠금 요청 인텐트
     *
     * 전원 버튼을 누른 것과 같아 생체 인증 잠금 해제가 그대로 유지됨.
     * DevicePolicyManager.lockNow는 다음 잠금 해제에 PIN·패턴·비밀번호 같은 강한 인증을 강제하므로 쓰지 않음.
     * 화면 잠금 전역 동작이 API 28부터 있어 그 미만에서는 리시버가 요청을 받아도 실행하지 않음
     */
    fun createLockScreenIntent(context: Context): Intent =
        Intent(ACTION_LOCK_SCREEN).setPackage(context.packageName)

    /** [service]가 연결된 동안 등록해 요청을 전역 동작으로 실행하는 리시버 */
    fun createReceiver(service: AccessibilityService): ReceiverRegistration {
        val filter = IntentFilter().apply {
            addAction(ACTION_BACK)
            addAction(ACTION_LOCK_SCREEN)
        }
        return ReceiverRegistration(filter) { _, intent ->
            Log.d(intent.action)
            // 요청 액션에 해당하는 전역 동작 실행, 해당 동작이 없으면 무시
            globalActionOf(intent.action)?.let { globalAction -> service.performGlobalAction(globalAction) }
        }
    }

    /** 요청 액션에 해당하는 전역 동작, 이 기기에서 실행할 동작이 없으면 null */
    private fun globalActionOf(action: String?): Int? = when (action) {
        ACTION_BACK -> AccessibilityService.GLOBAL_ACTION_BACK
        // API 28 미만에는 화면 잠금 전역 동작 없음
        ACTION_LOCK_SCREEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
        } else {
            null
        }
        else -> null
    }
}
