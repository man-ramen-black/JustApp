package com.black.code.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri

object PermissionUtil {
    fun canDrawOverlays(context: Context) : Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            true
        } else {
            Settings.canDrawOverlays(context)
        }
    }

    private fun getDrawOverlaysSettingIntent(context: Context) : Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.w("Build.VERSION.SDK_INT < Build.VERSION_CODES.M")
            return null
        }
        return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
    }

    fun showDrawOverlaysSetting(context: Context) {
        val intent = getDrawOverlaysSettingIntent(context)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ?: return
        context.startActivity(intent)
    }

    fun requestDrawOverlaysPermission(activity: ComponentActivity, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            onResult(true)
            return
        }
        val activityLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onResult(canDrawOverlays(activity))
        }
        val intent = getDrawOverlaysSettingIntent(activity)
            ?: return
        activityLauncher.launch(intent)
    }
}