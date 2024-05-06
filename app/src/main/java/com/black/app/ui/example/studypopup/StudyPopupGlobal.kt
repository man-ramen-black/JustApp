package com.black.app.ui.example.studypopup

import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import com.black.app.R
import com.black.app.broadcast.ScreenReceiver
import com.black.app.ui.example.studypopup.popup.StudyPopupView

object StudyPopupGlobal : ScreenReceiver.Interface {
    private var studyPopupView : StudyPopupView? = null

    override fun onScreenOn(context: Context, intent: Intent) {
        studyPopupView = StudyPopupView(ContextThemeWrapper(context, R.style.Theme_Black)).also {
            it.attachView()
        }
    }

    override fun onScreenOff(context: Context, intent: Intent) {
        studyPopupView?.detachView()
        studyPopupView = null
    }
}