package com.black.app.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.black.app.ui.main.studypopup.StudyPopupGlobal
import com.black.core.util.Log

/**
 * 앱 설치 알림 서비스(ForegroundService)가 실행되는 동안 화면 켜짐·꺼짐을 받아 스터디 팝업에 전달
 *
 * 화면 켜짐·꺼짐은 매니페스트 리시버로 받을 수 없어 서비스 수명 동안 런타임 등록
 */
class ScreenReceiver : BroadcastReceiver() {
    /** ForegroundService 수명에 맞춘 리시버 등록·해제 */
    companion object {
        private var receiver: ScreenReceiver? = null

        fun register(context: Context) {
            // 해제 없이 재호출 시 중복 등록으로 같은 신호를 두 번 처리
            if (receiver != null) {
                Log.w("already registered")
                return
            }

            val filter = IntentFilter()
                .apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
            // 시스템이 보내는 브로드캐스트라 NOT_EXPORTED여도 수신, 보호 브로드캐스트라 다른 앱은 송신 불가
            receiver = ScreenReceiver().also {
                ContextCompat.registerReceiver(
                    context.applicationContext,
                    it,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
        }

        fun unregister(context: Context) {
            // 등록 없이 호출되면 해제할 리시버 없음
            receiver?.let { context.applicationContext.unregisterReceiver(it) }
            receiver = null
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("ScreenReceiver: context = $context, intent = $intent")
        context ?: return
        intent ?: return

        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> StudyPopupGlobal.onScreenOn(context)
            Intent.ACTION_SCREEN_OFF -> StudyPopupGlobal.onScreenOff()
        }
    }
}
