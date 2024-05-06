package com.black.app.broadcast

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.black.app.service.ForegroundService
import com.black.app.ui.main.usagetimer.UsageTimerGlobal
import com.black.core.util.Log

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PAUSE_USAGE_TIMER = "NotificationAction.PAUSE_USAGE_TIMER"

        fun getNotificationActionPendingIntent(context: Context, action: String) : PendingIntent {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_CANCEL_CURRENT
            }
            return PendingIntent.getBroadcast(context, action.hashCode(), Intent(action), flags)
        }
    }

    private val interfaces = listOf<Interface>(
        UsageTimerGlobal
    )

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("context = $context, intent = $intent")
        context ?: return
        intent ?: return

        interfaces.forEach {
            if (it.onNotificationAction(context, intent)) {
                return
            }
        }
    }

    object ForegroundServiceInterface : ForegroundService.Interface {
        private var receiver: BroadcastReceiver? = null

        override fun onForegroundServiceStartCommand(context: Context, intent: Intent, flags: Int, startId: Int) {
        }

        override fun onForegroundServiceCreate(context: Context) {
            receiver = NotificationActionReceiver()
            context.registerReceiver(receiver, IntentFilter(ACTION_PAUSE_USAGE_TIMER))
        }

        override fun onForegroundServiceDestroy(context: Context) {
            context.unregisterReceiver(receiver)
            receiver = null
        }
    }

    interface Interface {
        fun onNotificationAction(context: Context, intent: Intent) : Boolean
    }
}