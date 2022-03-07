package com.black.code.contents.usagetimechecker

import android.content.Context
import android.content.res.TypedArray
import android.view.MotionEvent
import android.view.WindowManager
import com.black.code.R
import com.black.code.base.BaseCustomView
import com.black.code.databinding.ViewUsageTimeCheckerBinding
import com.black.code.util.Log
import com.black.code.util.OverlayViewUtil

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
class UsageTimeCheckerView(context: Context) : BaseCustomView<ViewUsageTimeCheckerBinding>(context) {
    override val layoutId: Int
        get() = R.layout.view_usage_time_checker

    override val styleableId: IntArray?
        get() = null

    override fun initialize(binding: ViewUsageTimeCheckerBinding, typedArray: TypedArray?) {}

    private var viewX = 0f
    private var viewY = 0f

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: run {
            return super.onTouchEvent(event)
        }

        // View move, drag and drop
        // https://localazy.com/blog/floating-windows-on-android-4-floating-window
        // https://kimch3617.tistory.com/entry/%EC%95%88%EB%93%9C%EB%A1%9C%EC%9D%B4%EB%93%9C-%EA%B0%84%EB%8B%A8%ED%9E%88-View-%EC%9B%80%EC%A7%81%EC%9D%B4%EA%B2%8C-%ED%95%98%EA%B8%B0-Drag-and-Drop
        when(event.action) {
            MotionEvent.ACTION_OUTSIDE -> {
                Log.e("outside")
            }
            MotionEvent.ACTION_DOWN -> {
                val windowParams = layoutParams as WindowManager.LayoutParams
                Log.e("down x : ${windowParams.x}, rawX : ${event.rawX}, result : ${x - event.rawX}")
                viewX = windowParams.x - event.rawX
                viewY = windowParams.y - event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                OverlayViewUtil.updateView(this) {
                    it.x = (event.rawX + viewX).toInt()
                    it.y = (event.rawY + viewY).toInt()
                }
            }
            MotionEvent.ACTION_UP -> {

            }
        }
        return super.onTouchEvent(event)
    }

    fun start() {
        binding.timer.start()
    }

    fun stop() {
        binding.timer.stop()
    }

    fun attachView() {
        binding.viewModel = this
        OverlayViewUtil.attachView(context, this)
    }

    fun detachView() {
        OverlayViewUtil.detachView(context, this)
    }
}