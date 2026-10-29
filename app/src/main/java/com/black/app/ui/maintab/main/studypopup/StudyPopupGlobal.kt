package com.black.app.ui.maintab.main.studypopup

import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import com.black.app.R
import com.black.app.broadcast.ScreenReceiver
import com.black.app.ui.maintab.main.studypopup.popup.StudyPopupView
import com.black.core.util.Log
import com.black.core.util.PermissionHelper

object StudyPopupGlobal : ScreenReceiver.Interface {
    private var studyPopupView : StudyPopupView? = null

    override fun onScreenOn(context: Context, intent: Intent) {
        // 다른 앱 위에 표시 권한 없이 오버레이 창을 추가하면 화면이 켜질 때마다 서비스 프로세스가 크래시
        if (!PermissionHelper.canDrawOverlays(context)) {
            Log.w("cannot draw overlays")
            return
        }
        studyPopupView = StudyPopupView(ContextThemeWrapper(context, R.style.Theme_Black)).also {
            it.attachView()
        }
    }

    override fun onScreenOff(context: Context, intent: Intent) {
        studyPopupView?.detachView()
        studyPopupView = null
    }
}