package com.black.app.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.black.app.service.ForegroundServiceController
import com.black.app.util.ComponentExtensions.launchAsync
import com.black.core.util.Log

/**
 * [expectedAction]을 받으면 실행 설정이 켜진 ForegroundService를 시작하는 매니페스트 리시버
 *
 * 부팅·앱 업데이트처럼 서비스가 함께 종료된 뒤 다시 시작해야 하는 시점에 사용
 */
abstract class ForegroundServiceStartReceiver(
    private val expectedAction: String,
) : BroadcastReceiver() {
    final override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("context = $context, intent = $intent")
        context ?: run {
            Log.w("context is null")
            return
        }

        // exported라 다른 앱의 명시적 브로드캐스트도 받으므로 action 확인
        if (intent?.action != expectedAction) {
            Log.w("unexpected action : ${intent?.action}")
            return
        }

        onExpectedActionReceived(context)

        // 백그라운드 수신이라 설정 화면 이동 없이 시작만 시도
        val controller = ForegroundServiceController(context)
        launchAsync { controller.startIfActivated() }
    }

    /** 서비스 시작 요청 전 추가 처리 */
    protected open fun onExpectedActionReceived(context: Context) {}
}
