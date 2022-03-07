package com.black.code.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import com.black.code.util.Log

class UnlockReceiver : BroadcastReceiver() {
    companion object {
        fun register(context: Context) : UnlockReceiver {
            return UnlockReceiver()
                .also {
                    context.registerReceiver(it, IntentFilter(Intent.ACTION_USER_PRESENT))
                }
        }

        fun unregister(context: Context, unlockReceiver: UnlockReceiver?) {
            unlockReceiver
                ?: run {
                    Log.w("receiver is null")
                    return
                }
            context.unregisterReceiver(unlockReceiver)
        }
    }


    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("UnlockReceiver: context = $context, intent = $intent")

        context ?: return

        Toast.makeText(context, "Unlock detected", Toast.LENGTH_SHORT)
            .show()
    }
}