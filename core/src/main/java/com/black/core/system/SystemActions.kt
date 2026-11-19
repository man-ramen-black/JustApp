package com.black.core.system

import android.content.Context
import android.content.Intent
import android.os.Build
import com.black.core.service.GlobalActionRequest
import com.black.core.util.AccessibilityServiceStatus
import com.black.core.util.Util

/**
 * 홈 화면 이동과 접근성 서비스 전역 동작(뒤로 가기, 화면 잠금) 요청
 *
 * 전역 동작은 앱 모듈의 접근성 서비스가 [GlobalActionRequest] 브로드캐스트로 받아 실행하므로 기능 모듈은 서비스 클래스 없이 요청만 보냄.
 * 요청은 브로드캐스트라 서비스의 실행 결과를 받지 못하므로, 요청 함수는 요청을 보낼 수 있는 상태면 true
 */
object SystemActions {
    fun createHomeIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /**
     * 홈 화면 실행
     *
     * Activity가 아닌 컨텍스트면 FLAG_ACTIVITY_NEW_TASK는 [Util.startActivitySafely]가 추가.
     * 서비스에서 띄우면 Android 10 이상은 다른 앱 위에 표시 권한(백그라운드 실행 제한 예외)이 있어야 실제로 뜨고,
     * 제한으로 막힌 실행은 예외 없이 무시되어 true로 반환됨
     *
     * @return 홈 화면을 실행할 앱이 없으면 false
     */
    fun goHome(context: Context): Boolean = Util.startActivitySafely(context, createHomeIntent())

    /**
     * 접근성 서비스에 뒤로 가기 요청
     *
     * @return 요청을 받을 접근성 서비스가 꺼져 있어 보내지 않았으면 false
     */
    fun requestBack(context: Context): Boolean {
        // 요청을 받을 접근성 서비스가 꺼진 경우
        if (!AccessibilityServiceStatus(context).isAccessibilityServiceEnabled()) {
            return false
        }
        // 뒤로 가기 요청 전송
        context.sendBroadcast(GlobalActionRequest.createBackIntent(context))
        return true
    }

    /**
     * 접근성 서비스에 화면 잠금 요청
     *
     * @return 화면 잠금 전역 동작이 없는 API 28 미만이거나 접근성 서비스가 꺼져 있어 보내지 않았으면 false
     */
    fun requestLockScreen(context: Context): Boolean {
        // 화면 잠금 전역 동작이 없는 API 28 미만
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false
        }
        // 요청을 받을 접근성 서비스가 꺼진 경우
        if (!AccessibilityServiceStatus(context).isAccessibilityServiceEnabled()) {
            return false
        }
        // 화면 잠금 요청 전송
        context.sendBroadcast(GlobalActionRequest.createLockScreenIntent(context))
        return true
    }
}
