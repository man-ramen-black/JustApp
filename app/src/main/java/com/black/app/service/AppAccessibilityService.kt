package com.black.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.black.app.ui.main.usagetimer.UsageTimerWindowHandler
import com.black.core.broadcast.ReceiverRegistration
import com.black.core.service.GlobalActionRequest
import com.black.core.util.PermissionHelper

/**
 * JustApp의 유일한 접근성 서비스
 *
 * 기능마다 서비스를 두면 접근성 설정에서 기능 수만큼 켜야 하므로 하나로 합침.
 * 사용 타이머의 창 전환 처리는 [UsageTimerWindowHandler], 플로팅 버튼 뒤로 가기 같은 전역 동작 요청은 [GlobalActionRequest]로 받음.
 * 기능 모듈은 이 클래스를 참조할 수 없어 켜짐 확인·설정 이동에 [PermissionHelper.isAccessibilityServiceEnabled] 등 클래스를 받지 않는 함수를 사용하므로,
 * 매니페스트에 다른 접근성 서비스를 추가하지 않음
 */
@SuppressLint("AccessibilityPolicy")
class AppAccessibilityService : AccessibilityService() {

    private val usageTimerHandler = UsageTimerWindowHandler(this)

    /** 연결 전에 onUnbind가 불려도 [ReceiverRegistration]이 등록 여부를 기록해 해제를 건너뜀 */
    private val globalActionReceiver = GlobalActionRequest.createReceiver(this)

    override fun onServiceConnected() {
        super.onServiceConnected()
        usageTimerHandler.start()
        globalActionReceiver.register(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        usageTimerHandler.handleEvent(event)
    }

    override fun onInterrupt() {
    }

    override fun onUnbind(intent: Intent?): Boolean {
        globalActionReceiver.unregister(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        usageTimerHandler.stop()
    }
}
