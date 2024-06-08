package com.black.core.util

import android.app.Activity
import android.widget.Toast
import java.lang.ref.WeakReference

object BackPressHelper {
    // Tip) Toast.LENGTH_SHORT : 2s, Toast.LENGTH_LONG : 3.5s
    private const val CLOSE_DELAY = 2000

    private var backPressedTime : Long = 0L
    private var toast: WeakReference<Toast>? = null

    fun onBackPressed(activity: Activity, message: String, onKill: () -> Unit) {
        if (System.currentTimeMillis() - backPressedTime < CLOSE_DELAY) {
            toast?.get()?.cancel()
            onKill()

        } else {
            backPressedTime = System.currentTimeMillis()
            val toast = Toast.makeText(activity, message, CLOSE_DELAY).also { it.show() }
            this.toast = WeakReference(toast)
        }
    }
}