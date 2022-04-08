package com.black.code.ui.example.usagetimechecker

import android.content.Context
import android.content.res.TypedArray
import android.view.Gravity
import android.view.WindowManager
import com.black.code.R
import com.black.code.base.view.MovableOverlayView
import com.black.code.base.viewmodel.LiveEvent
import com.black.code.databinding.ViewUsageTimeCheckerBinding
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

    private val viewModel by lazy { ViewModel() }

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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding.viewModel = viewModel
        viewModel.event.observeForever(this::onReceivedEvent)
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
        val event by lazy { LiveEvent() }

        fun onClickClose() {
            event.send()
        }
    }
}