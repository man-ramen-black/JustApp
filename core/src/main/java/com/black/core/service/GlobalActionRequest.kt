package com.black.core.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.black.core.broadcast.ReceiverRegistration
import com.black.core.util.Log

/**
 * 앱의 접근성 서비스에 전역 동작(뒤로 가기)을 요청하는 앱 내부 브로드캐스트
 *
 * 접근성 서비스는 앱 모듈에 하나만 두고 기능 모듈은 그 클래스를 모르므로, 요청은 이 인텐트로 보내고 서비스는 [createReceiver]로 받음.
 * 다른 앱이 전역 동작을 실행하지 못하도록 패키지를 지정해 보내고 앱 내부 전용으로 등록
 */
object GlobalActionRequest {
    private const val ACTION_BACK = "com.black.core.action.REQUEST_GLOBAL_BACK"

    /** 뒤로 가기 요청 인텐트 */
    fun createBackIntent(context: Context): Intent =
        Intent(ACTION_BACK).setPackage(context.packageName)

    /** [service]가 연결된 동안 등록해 요청을 전역 동작으로 실행하는 리시버 */
    fun createReceiver(service: AccessibilityService): ReceiverRegistration =
        ReceiverRegistration(IntentFilter(ACTION_BACK)) { _, intent ->
            Log.d(intent.action)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
}
