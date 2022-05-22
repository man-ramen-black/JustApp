package com.black.code.ui.example.usagetimer

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.black.code.broadcast.NotificationActionReceiver
import com.black.code.broadcast.ScreenReceiver
import com.black.code.model.preferences.ForegroundServicePreference
import com.black.code.service.ForegroundService
import com.black.code.util.Log
import java.lang.ref.WeakReference

/**
 * ForegroundService, ScreenReceiver에서 UsageTimer 동작 구현
 * Created by jinhyuk.lee on 2022/04/29
 **/
object UsageTimerManager : ForegroundService.Interface, ScreenReceiver.Interface, NotificationActionReceiver.Interface {
    private const val ACTION_ATTACH = "UsageTimer.ATTACH"
    private const val ACTION_DETACH = "UsageTimer.DETACH"

    private var usageTimerView : UsageTimerView? = null
    private var preference :  WeakReference<ForegroundServicePreference>? = null
    private var model :  WeakReference<UsageTimerModel>? = null

    fun detachViewInService(context: Context) {
        ForegroundService.start(context) {
            it.action = ACTION_DETACH
        }
    }

    override fun onForegroundServiceStartCommand(context: Context, intent: Intent, flags: Int, startId: Int) {
        when(intent.action) {
            ACTION_ATTACH -> {
                usageTimerView = UsageTimerView(context).also {
                    it.attachView()
                }
            }

            ACTION_DETACH -> {
                usageTimerView ?: run {
                    Log.w("usageTimerView not attached")
                }
                usageTimerView?.detachView()
                usageTimerView = null
            }
        }
    }

    override fun onForegroundServiceCreate(context: Context) {

    }

    override fun onForegroundServiceDestroy(context: Context) {
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

        ForegroundService.start(context) {
            it.action = ACTION_ATTACH
        }
    }

    override fun onScreenOff(context: Context, intent: Intent) {
        detachViewInService(context)
    }

    override fun onNotificationAction(context: Context, intent: Intent): Boolean {
        if (intent.action == NotificationActionReceiver.ACTION_PAUSE_USAGE_TIMER) {
            pauseUsageTimerInNotificationAction(context)
            return true
        }
        return false
    }

    private fun pauseUsageTimerInNotificationAction(context: Context) {
        val model = this.model?.get() ?: WeakReference(UsageTimerModel(context)).let {
            model = it
            it.get()!!
        }

        val pauseDuration = model.getPauseDuration()
        model.pause(pauseDuration)
        detachViewInService(context)

        Toast.makeText(context, "UsageTimer paused", Toast.LENGTH_SHORT)
            .show()
    }
}