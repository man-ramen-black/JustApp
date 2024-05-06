package com.black.app.ui.example.usagetimer.view

import android.content.Context
import android.content.res.TypedArray
import android.view.Gravity
import android.view.WindowManager
import com.black.app.R
import com.black.app.databinding.ViewUsageTimerBinding
import com.black.core.util.UiUtil
import com.black.core.util.Util

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
class UsageTimerView(context: Context) : com.black.core.view.MovableOverlayView<ViewUsageTimerBinding>(context) {
    override val layoutId: Int
        get() = R.layout.view_usage_timer

    override val styleableId: IntArray?
        get() = null

    private val viewModel by lazy { ViewModel() }

    override fun initialize(binding: ViewUsageTimerBinding, typedArray: TypedArray?) {
        isMovable = true
    }

    override fun onInitializeWindowLayoutParams(windowParams: WindowManager.LayoutParams) {
        windowParams.apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = UiUtil.dpToPx(context, 20f)
            windowAnimations = android.R.style.Animation_Toast
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

    override fun bindVariable(binding: ViewUsageTimerBinding) {
        super.bindVariable(binding)
        binding.viewModel = viewModel
        viewModel.event.observeForever(this::onReceivedEvent)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        viewModel.event.removeObserver()
        super.onDetachedFromWindow()
    }

    private fun onReceivedEvent(action: String, data: Any?) {
        detachView()
    }

    class ViewModel {
        val event by lazy { com.black.core.viewmodel.LiveEvent() }

        fun onClickClose() {
            event.send()
        }
    }
}