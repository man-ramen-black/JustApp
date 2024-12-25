package com.black.core.util

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStateAtLeast
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

    /** 딥링크 실행 */
    fun startDeepLink(context: Context?, url: String?)
            = startDeepLink(context, Uri.parse(url ?: ""))

    fun startDeepLink(context: Context?, uri: Uri) {
        if(context == null){
            Log.w("context is null")
            return
        }

        val scheme = uri.scheme
        if (scheme == null) {
            Log.e("invalid uri : $uri")
            return
        }

        Log.v("deeplink : $uri")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            if(context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 접근성 권한 있는지 체크 */
    fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = ComponentName(context, service)

        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServicesSetting
            .split(":")
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expectedComponentName }
    }

    fun openAccessibilitySetting(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .apply {
                setData(Uri.fromParts("package", context.packageName, null))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        context.startActivity(intent)
    }
}