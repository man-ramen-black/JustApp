package com.black.core.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

object OverlayViewUtil {
    fun attachView(
        view: View,
        onSetWindowParams: ((windowParams: WindowManager.LayoutParams) -> Unit)? = null
    ) {
        Log.d()

        val context = view.context
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
        layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        // 컷아웃 영역까지 배치·이동 허용
        // 기본 모드는 가로 화면에서 컷아웃 쪽 가장자리를 x, y 기준 영역에서 빼므로, 시스템 바만 빼는 이동 제한과 어긋나 반대쪽 화면 밖까지 이동됨
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        onSetWindowParams?.invoke(layoutParams)

        Log.d("isCenter : ${layoutParams.gravity and Gravity.CENTER == Gravity.CENTER}")

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

    /** 화면의 x, y 좌표로 뷰를 이동 */
    @SuppressLint("RtlHardcoded")
    fun moveView(view: View, x: Float, y: Float) {
        updateView(view) {
            it.gravity = Gravity.TOP or Gravity.LEFT
            val screenSize = UiUtil.getCurrentViewportSize(view.context)
            // 뷰가 화면 밖으로 벗어나지 않게 제한
            it.x = min(max(x.toInt(), 0), screenSize.x - view.width)
            it.y = min(max(y.toInt(), 0), screenSize.y - view.height)
        }
    }

    /**
     * Gravity가 right인 경우 x값은 오른쪽에서부터의 x이고, bottom도 아래부터의 y이기 때문에,
     * 화면 사이즈, view size를 계산하여 Top, Left 기준의 x, y 위치를 반환
     * 화면 사이즈는 [moveView]와 같은 기준 영역(시스템 바 제외)이어야 이동 시작 시 뷰가 튀지 않음
     */
    fun getAbsoluteWindowPosition(view: View) : Point {
        val context = view.context
        val screenSize = UiUtil.getCurrentViewportSize(context)

        val windowParams = view.layoutParams as? WindowManager.LayoutParams
            ?: throw ClassCastException("LayoutParams is not WindowManager.LayoutParams")

        return calculateTopLeftPosition(windowParams.gravity, windowParams.x, windowParams.y, screenSize, view.width, view.height)
    }

    /** Gravity 기준 창 x, y를 containerSize 영역의 Top, Left 기준 위치로 변환 */
    @SuppressLint("RtlHardcoded")
    fun calculateTopLeftPosition(
        gravity: Int,
        windowX: Int,
        windowY: Int,
        containerSize: Point,
        viewWidth: Int,
        viewHeight: Int
    ) : Point {
        val x = when {
            gravity and Gravity.LEFT == Gravity.LEFT -> {
                Log.d("gravity is left")
                windowX
            }
            gravity and Gravity.RIGHT == Gravity.RIGHT -> {
                Log.d("gravity is right")
                containerSize.x - viewWidth - windowX
            }
            gravity and Gravity.CENTER_HORIZONTAL == Gravity.CENTER_HORIZONTAL -> {
                Log.d("gravity is center horizontal")
                containerSize.x / 2 - viewWidth / 2 + windowX
            }
            else -> {
                Log.d("gravity is unknown")
                windowX
            }
        }

        val y = when {
            gravity and Gravity.TOP == Gravity.TOP -> {
                Log.d("gravity is top")
                windowY
            }
            gravity and Gravity.BOTTOM == Gravity.BOTTOM -> {
                Log.d("gravity is bottom")
                containerSize.y - viewHeight - windowY
            }
            gravity and Gravity.CENTER_VERTICAL == Gravity.CENTER_VERTICAL -> {
                Log.d("gravity is center vertical")
                containerSize.y / 2 - viewHeight / 2 + windowY
            }
            else -> {
                Log.d("gravity is unknown")
                windowY
            }
        }
        return Point(x, y)
    }

}