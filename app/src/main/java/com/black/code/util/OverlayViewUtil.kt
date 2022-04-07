package com.black.code.util

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast

object OverlayViewUtil {
    fun attachView(view: View, onSetWindowParams: ((windowParams: WindowManager.LayoutParams) -> Unit)? = null) {
        Log.d()

        val context = view.context
        if (!PermissionHelper.canDrawOverlays(context)) {
            Toast.makeText(context, "다른 앱 위에 그리기 권한 허용이 필요합니다.", Toast.LENGTH_SHORT)
                .show()
            PermissionHelper.showDrawOverlaysSetting(context)
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
        onSetWindowParams?.invoke(layoutParams)
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

    fun detachView(view: View) {
        Log.d()

        if (view.parent == null) {
            Log.w("view is not attached")
            return
        }

        val context = view.context
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.removeView(view)
    }

    /**
     * 화면의 x, y 좌표로 뷰를 이동
     */
    fun moveView(view: View, x: Float, y: Float) {
        updateView(view) {
            it.gravity = Gravity.TOP or Gravity.LEFT
            it.x = x.toInt()
            it.y = y.toInt()
        }
    }

    /**
     * Gravity가 right인 경우 x값은 오른쪽에서부터의 x이고, bottom도 아래부터의 y이기 때문에,
     * 화면 사이즈, view size를 계산하여 Top, Left 기준의 x, y 위치를 반환
     */
    fun getAbsoluteWindowPosition(view: View) : Point {
        val context = view.context
        val screenSize = Util.getScreenSize(context)

        val windowParams = view.layoutParams as? WindowManager.LayoutParams
            ?: throw ClassCastException("LayoutParams is not WindowManager.LayoutParams")

        val x = if (windowParams.gravity and Gravity.RIGHT == Gravity.RIGHT) {
            Log.d("gravity is right")
            screenSize.x - view.width - windowParams.x
        } else {
            Log.d("gravity is left")
            windowParams.x
        }

        val y = if (windowParams.gravity and Gravity.BOTTOM == Gravity.BOTTOM) {
            Log.d("gravity is bottom")
            screenSize.y - view.height - windowParams.y
        } else {
            Log.d("gravity is top")
            windowParams.y
        }
        return Point(x, y)
    }

}