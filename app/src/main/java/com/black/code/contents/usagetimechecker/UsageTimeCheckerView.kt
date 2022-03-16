package com.black.code.contents.usagetimechecker

import android.content.Context
import android.content.res.TypedArray
import android.view.Gravity
import android.view.WindowManager
import com.black.code.R
import com.black.code.base.MovableOverlayView
import com.black.code.databinding.ViewUsageTimeCheckerBinding
import com.black.code.util.OverlayViewUtil
import com.black.code.util.Util

/**
 * CustomView 가이드
 * https://gun0912.tistory.com/38
 * https://localazy.com/blog/floating-windows-on-android-4-floating-window
 *
 * CustomView ViewBinding
 * https://0391kjy.tistory.com/28
 *
 * BindingMethods
 * https://developer.android.com/topic/libraries/data-binding/binding-adapters?hl=ko#specify-method
 */
class UsageTimeCheckerView(context: Context) : MovableOverlayView<ViewUsageTimeCheckerBinding>(context) {
    override val layoutId: Int
        get() = R.layout.view_usage_time_checker

    override val styleableId: IntArray?
        get() = null

    override fun initialize(binding: ViewUsageTimeCheckerBinding, typedArray: TypedArray?) {
        isMovable = true
    }

    override fun onSetLayoutParams(windowParams: WindowManager.LayoutParams) {
        windowParams.apply {
            gravity = Gravity.TOP or Gravity.RIGHT
            x = Util.dpToPx(context, 20f)
            y = Util.dpToPx(context, 20f)
        }
    }

    fun start() {
        binding.timer.start()
    }

    fun pause() {
        binding.timer.pause()
    }

    fun stop() {
        binding.timer.stop()
    }

    override fun attachView() {
        binding.viewModel = this
        start()
        super.attachView()
    }
}