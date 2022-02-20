package com.black.code.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.black.code.util.Log

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("UnlockReceiver: context = $context, intent = $intent")

        context ?: return

        Toast.makeText(context, "Unlock detected", Toast.LENGTH_SHORT)
            .show()
    }
}