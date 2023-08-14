package com.black.code.ui.example.studypopup

import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import com.black.code.R
import com.black.code.broadcast.ScreenReceiver
import com.black.code.ui.example.studypopup.popup.StudyPopupView

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