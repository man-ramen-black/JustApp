package com.black.app.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.black.app.ui.main.studypopup.StudyPopupGlobal
import com.black.core.broadcast.ReceiverRegistration
import com.black.core.util.Log

/**
 * 앱 설치 알림 서비스(ForegroundService)가 실행되는 동안 화면 켜짐·꺼짐을 받아 스터디 팝업에 전달
 *
 * 화면 켜짐·꺼짐은 매니페스트 리시버로 받을 수 없어 서비스 수명 동안 런타임 등록
 */
class ScreenReceiver : BroadcastReceiver() {
    /** ForegroundService 수명에 맞춘 리시버 등록·해제 */
    companion object {
        /** 해제 없이 재호출돼도 같은 신호를 두 번 처리하지 않도록 등록 여부를 보관하는 홀더 */
        private val registration = ReceiverRegistration(
            IntentFilter()
                .apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
            ScreenReceiver(),
        )

        fun register(context: Context) {
            registration.register(context)
        }

        fun unregister(context: Context) {
            registration.unregister(context)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("context = $context, intent = $intent")
        context ?: return
        intent ?: return

        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> StudyPopupGlobal.onScreenOn(context)
            Intent.ACTION_SCREEN_OFF -> StudyPopupGlobal.onScreenOff()
        }
    }
}
