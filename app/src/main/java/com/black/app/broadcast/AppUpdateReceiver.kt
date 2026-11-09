package com.black.app.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.black.app.service.ForegroundServiceController
import com.black.app.util.ComponentExtensions.launchAsync
import com.black.core.util.Log

/**
 * 앱 업데이트(MY_PACKAGE_REPLACED) 후 ForegroundService 재시작
 *
 * 업데이트 설치가 앱 프로세스를 종료해 서비스도 함께 종료되고 START_STICKY 재생성도 일어나지 않으므로,
 * 실행 설정이 켜져 있으면 여기서 다시 시작
 **/
class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d()
        context ?: run {
            Log.w("context is null")
            return
        }

        // exported라 다른 앱의 명시적 브로드캐스트도 받으므로 action 확인
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.w("unexpected action : ${intent?.action}")
            return
        }

        // 백그라운드 수신이라 설정 화면 이동 없이 시작만 시도
        val controller = ForegroundServiceController(context)
        launchAsync { controller.startIfActivated() }
    }
}