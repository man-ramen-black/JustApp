package com.black.code.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.black.code.service.ForegroundService
import com.black.code.util.Log

/**
 * 동작하지 않음
 * 스토어를 통해서 업데이트하면 동작하지 않을까 추측
 **/
class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d()
        context ?: run {
            Log.w("context is null")
            return
        }

        intent ?: run {
            Log.w("intent is null")
            return
        }

        ForegroundService.start(context)
    }
}