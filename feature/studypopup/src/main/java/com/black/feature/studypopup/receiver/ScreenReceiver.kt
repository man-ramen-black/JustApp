package com.black.feature.studypopup.receiver

import android.content.Context
import com.black.core.broadcast.ReceiverRegistration
import com.black.core.util.Log
import com.black.feature.studypopup.overlay.StudyPopupGlobal

/**
 * 앱 설치 알림 서비스(ForegroundService)가 실행되는 동안 화면 켜짐·꺼짐을 받아 스터디 팝업에 전달
 *
 * 화면 켜짐·꺼짐은 매니페스트 리시버로 받을 수 없어 서비스 수명 동안 런타임 등록
 */
object ScreenReceiver {

    /** 해제 없이 재호출돼도 같은 신호를 두 번 처리하지 않도록 등록 여부를 보관하는 홀더 */
    private val registration = ReceiverRegistration.screenState(
        onScreenOn = { context ->
            Log.d("screen on")
            StudyPopupGlobal.onScreenOn(context)
        },
        onScreenOff = {
            Log.d("screen off")
            StudyPopupGlobal.onScreenOff()
        },
    )

    /** ForegroundService 수명에 맞춘 리시버 등록 */
    fun register(context: Context) {
        registration.register(context)
    }

    /** ForegroundService 수명에 맞춘 리시버 해제 */
    fun unregister(context: Context) {
        registration.unregister(context)
    }
}
