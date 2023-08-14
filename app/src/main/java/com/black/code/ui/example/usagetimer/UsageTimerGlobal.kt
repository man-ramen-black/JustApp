package com.black.code.ui.example.usagetimer

import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.widget.Toast
import com.black.code.R
import com.black.code.broadcast.NotificationActionReceiver
import com.black.code.broadcast.ScreenReceiver
import com.black.code.model.UsageTimerModel
import com.black.code.model.preferences.ForegroundServicePreference
import com.black.code.ui.example.usagetimer.view.UsageTimerView
import com.black.code.util.Log
import java.lang.ref.WeakReference

/**
 * ForegroundService, ScreenReceiver에서 UsageTimer 동작 구현
 * Created by jinhyuk.lee on 2022/04/29
 **/
object UsageTimerGlobal : ScreenReceiver.Interface, NotificationActionReceiver.Interface {
    private var usageTimerView : UsageTimerView? = null
    private var preference :  WeakReference<ForegroundServicePreference>? = null
    private var model :  WeakReference<UsageTimerModel>? = null

    fun detachView() {
        usageTimerView ?: run {
            Log.w("usageTimerView not attached")
        }
        usageTimerView?.detachView()
        usageTimerView = null
    }

    private fun isUsageTimerPaused(context: Context) : Boolean {
        if (preference?.get() == null) {
            preference = WeakReference(ForegroundServicePreference(context))
        }

        val endTime = preference!!.get()!!.getUsageTimerPauseEndTime()
        if (endTime == 0L) {
            return false
        }

        return System.currentTimeMillis() < endTime
    }

    override fun onScreenOn(context: Context, intent: Intent) {
        if (isUsageTimerPaused(context)) {
            Log.d("UsageTimer paused")
            return
        }

        usageTimerView = UsageTimerView(ContextThemeWrapper(context, R.style.Theme_Black)).also {
            it.attachView()
        }
    }

    override fun onScreenOff(context: Context, intent: Intent) {
        detachView()
    }

    override fun onNotificationAction(context: Context, intent: Intent): Boolean {
        if (intent.action == NotificationActionReceiver.ACTION_PAUSE_USAGE_TIMER) {
            pauseUsageTimerInNotificationAction(context)
            return true
        }
        return false
    }

    private fun pauseUsageTimerInNotificationAction(context: Context) {
        val model = model?.get() ?: WeakReference(UsageTimerModel(context)).also { model = it }.get()!!

        val pauseDuration = model.getPauseDuration()
        model.pause(pauseDuration)
        detachView()

        Toast.makeText(context, "UsageTimer paused", Toast.LENGTH_SHORT)
            .show()
    }
}