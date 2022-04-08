package com.black.code.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.black.code.service.ForegroundService
import com.black.code.util.Log

class UsageTimeCheckerReceiver : BroadcastReceiver() {
    companion object {
        fun register(context: Context) : UsageTimeCheckerReceiver {
            return UsageTimeCheckerReceiver()
                .also {
                    val filter = IntentFilter()
                        .apply {
                            addAction(Intent.ACTION_SCREEN_ON)
                            addAction(Intent.ACTION_SCREEN_OFF)
                        }
                    context.registerReceiver(it, filter)
                }
        }

        fun unregister(context: Context, unlockReceiver: UsageTimeCheckerReceiver?) {
            unlockReceiver
                ?: run {
                    Log.w("receiver is null")
                    return
                }
            context.unregisterReceiver(unlockReceiver)
        }
    }


    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("UsageTimeCheckerReceiver: context = $context, intent = $intent")
        context ?: return
        intent ?: return

        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> ForegroundService.start(context) {
                it.action = ForegroundService.ACTION_ATTACH_USAGE_TIME_CHECKER
            }
            Intent.ACTION_SCREEN_OFF -> ForegroundService.start(context) {
                it.action = ForegroundService.ACTION_DETACH_USAGE_TIME_CHECKER
            }
        }
    }
}