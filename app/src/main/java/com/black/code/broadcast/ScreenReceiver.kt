package com.black.code.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.black.code.service.ForegroundService
import com.black.code.ui.example.studypopup.StudyPopupGlobal
import com.black.code.ui.example.usagetimer.UsageTimerGlobal
import com.black.code.util.Log

class ScreenReceiver : BroadcastReceiver() {
    /**
     * ForegroundService에서 실행될 동작 정의
     */
    object ServiceInterface : ForegroundService.Interface {
        private var receiver : ScreenReceiver? = null

        override fun onForegroundServiceStartCommand(context: Context, intent: Intent, flags: Int, startId: Int) {
        }

        override fun onForegroundServiceCreate(context: Context) {
            receiver = register(context.applicationContext)
        }

        override fun onForegroundServiceDestroy(context: Context) {
            unregister(context.applicationContext, receiver)
        }

    }

    companion object {
        fun register(context: Context) : ScreenReceiver {
            return ScreenReceiver()
                .also {
                    val filter = IntentFilter()
                        .apply {
                            addAction(Intent.ACTION_SCREEN_ON)
                            addAction(Intent.ACTION_SCREEN_OFF)
                        }
                    context.registerReceiver(it, filter)
                }
        }

        fun unregister(context: Context, unlockReceiver: ScreenReceiver?) {
            unlockReceiver
                ?: run {
                    Log.w("receiver is null")
                    return
                }
            context.unregisterReceiver(unlockReceiver)
        }
    }

    private val interfaces : List<Interface> = listOf(
        UsageTimerGlobal,
        StudyPopupGlobal
    )

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("ScreenReceiver: context = $context, intent = $intent")
        context ?: return
        intent ?: return

        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                interfaces.forEach { it.onScreenOn(context, intent) }
            }
            Intent.ACTION_SCREEN_OFF -> {
                interfaces.forEach { it.onScreenOff(context, intent) }
            }
        }
    }

    interface Interface {
        fun onScreenOn(context: Context, intent: Intent)
        fun onScreenOff(context: Context, intent: Intent)
    }
}