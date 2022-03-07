package com.black.code.util

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.Toast

object OverlayViewUtil {
    fun attachView(context: Context, view: View) {
        Log.d()

        if (!PermissionUtil.canDrawOverlays(context)) {
            Toast.makeText(context, "다른 앱 위에 그리기 권한 허용이 필요합니다.", Toast.LENGTH_SHORT)
                .show()
            PermissionUtil.showDrawOverlaysSetting(context)
            return
        }

        if (view.parent != null) {
            Log.w("view is already attached")
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        // https://jjun5050.tistory.com/23
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(view, layoutParams)
    }

    fun updateView(view: View, onUpdate: (windowParams: WindowManager.LayoutParams) -> Unit) {
        val windowParams = view.layoutParams as? WindowManager.LayoutParams
        windowParams ?: run {
            throw ClassCastException("LayoutParams is not WindowManager.LayoutParams")
        }

        onUpdate(windowParams)

        val windowManager = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.updateViewLayout(view, windowParams)
    }

    fun detachView(context: Context, view: View) {
        Log.d()

        if (view.parent == null) {
            Log.w("view is not attached")
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.removeView(view)
    }
}