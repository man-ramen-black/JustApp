package com.black.app.ui.maintab.main.keepscreenon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.black.core.util.Log

/**
 * adb로 화면 켜짐 유지를 켜고 끄는 debug 빌드 전용 Receiver
 *
 * 타일 클릭(`cmd statusbar click-tile`)은 토글이라 여러 세션이 각자 누르면 서로 끄고, 빠른 설정창을 펼쳐야 클릭이 전달되므로
 * 켤지 끌지를 액션으로 지정하는 진입점 제공. 이미 그 상태면 아무것도 바꾸지 않음
 *
 * shell만 가진 DUMP 권한을 요구해 다른 앱의 호출 차단, `am broadcast`는 순서 있는 브로드캐스트라
 * 처리 후 활성 여부를 결과(`result=1, data="active=true"`)로 반환
 *
 * `adb shell am broadcast -a com.black.app.action.ACTIVATE_KEEP_SCREEN_ON -p com.black.app`
 */
class KeepScreenOnReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ACTIVATE = "com.black.app.action.ACTIVATE_KEEP_SCREEN_ON"
        const val ACTION_DEACTIVATE = "com.black.app.action.DEACTIVATE_KEEP_SCREEN_ON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("action = ${intent.action}")

        // 액션별 켜기·끄기, 같은 상태 재요청은 KeepScreenOnGlobal이 무시
        when (intent.action) {
            ACTION_ACTIVATE -> KeepScreenOnGlobal.activate(context)
            ACTION_DEACTIVATE -> KeepScreenOnGlobal.deactivate(context)
            else -> return
        }

        // 처리 후 활성 여부를 am broadcast 결과로 반환(순서 없는 브로드캐스트는 결과를 받을 곳이 없음)
        if (isOrderedBroadcast) {
            val isActive = KeepScreenOnGlobal.isActive.value
            setResult(if (isActive) 1 else 0, "active=$isActive", null)
        }
    }
}
