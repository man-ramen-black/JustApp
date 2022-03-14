package com.black.code.base

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.databinding.ViewDataBinding
import com.black.code.util.OverlayViewUtil

abstract class DraggableView<T : ViewDataBinding> : CustomView<T> {
    var isDraggable = false

    private var downXFromView = 0f
    private var downYFromView = 0f

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: run {
            return super.onTouchEvent(event)
        }

        // View move, drag and drop
        // https://localazy.com/blog/floating-windows-on-android-5-moving-window
        // https://kimch3617.tistory.com/entry/%EC%95%88%EB%93%9C%EB%A1%9C%EC%9D%B4%EB%93%9C-%EA%B0%84%EB%8B%A8%ED%9E%88-View-%EC%9B%80%EC%A7%81%EC%9D%B4%EA%B2%8C-%ED%95%98%EA%B8%B0-Drag-and-Drop
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                val viewPosition = OverlayViewUtil.getAbsoluteWindowPosition(this)
                downXFromView = event.rawX - viewPosition.x.toFloat()
                downYFromView = event.rawY - viewPosition.y.toFloat()
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDraggable) {
                    val x = event.rawX - downXFromView
                    val y = event.rawY - downYFromView
                    OverlayViewUtil.moveView(this, x, y)
                }
            }
        }
        return super.onTouchEvent(event)
    }
}