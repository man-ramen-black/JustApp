package com.black.core.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStateAtLeast
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

object Util {

    /**
     * 특정 lifecycle 시점에 로직 실행
     */
    fun launchWhenState(owner : LifecycleOwner, state: Lifecycle.State, runnable: () -> Unit): Job {
        return owner.lifecycleScope.launch {
            owner.lifecycle.withStateAtLeast(state, runnable)
        }
    }

    fun vibrateOneShot(context: Context, milliseconds: Long) {
        val vibrator : Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(milliseconds)
        }
    }

    fun turnScreenOn(activity: Activity) {
        with(activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
            } else {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
    }

    /**
     * 클립보드 텍스트 반환
     */
    fun getClipData(context: Context): String? {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboardManager.hasPrimaryClip()) {
            return null
        }

        val clipData = clipboardManager.primaryClip ?: run {
            return null
        }

        if (clipData.itemCount <= 0) {
            return null
        }

        return clipData.getItemAt(0).text?.toString()
    }

    /**
     * 텍스트 공유
     */
    fun share(context: Context, text: String){
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            if(context !is Activity){
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        try {
            context.startActivity(Intent.createChooser(intent, null))
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }
    }

    fun <T> T.ifThen(isTrue: Boolean, then: T.() -> Unit): T = apply {
        if (isTrue) then()
    }
}