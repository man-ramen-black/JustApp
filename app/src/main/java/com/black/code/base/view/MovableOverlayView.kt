package com.black.code.base.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.databinding.ViewDataBinding
import com.black.code.util.OverlayViewUtil

/**
 * 이동시킬 수 있는 Overlay 전용 View
 * Android에서 공식적으로 지원하는 Drag and drop 기능은 뷰를 직접적으로 이동시키지 않고 shadow를 끌어다 놓는 방식이기 때문에 직접 구현
 * https://developer.android.com/guide/topics/ui/drag-drop?hl=ko
 */
abstract class MovableOverlayView<T : ViewDataBinding> : OverlayView<T> {
    companion object {
        const val ACTION_MOVE_STARTED = 0
        const val ACTION_MOVING = 1
        const val ACTION_MOVE_ENDED = 2
    }

    private var onMoveListener : ((view: View, action: Int, x: Float, y: Float) -> Unit)? = null

    var isMovable = false
        set(value) {
            field = value
            if (isDown) {
                onMoveListener?.invoke(this, ACTION_MOVE_STARTED, 0f, 0f)
            }
        }

    private var isDown = false
    private var isMoving = false
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

        // View move
        // https://localazy.com/blog/floating-windows-on-android-5-moving-window
        // https://kimch3617.tistory.com/entry/%EC%95%88%EB%93%9C%EB%A1%9C%EC%9D%B4%EB%93%9C-%EA%B0%84%EB%8B%A8%ED%9E%88-View-%EC%9B%80%EC%A7%81%EC%9D%B4%EA%B2%8C-%ED%95%98%EA%B8%B0-Drag-and-Drop
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDown = true
                if (isMovable) {
                    isMoving = true
                    val viewPosition = OverlayViewUtil.getAbsoluteWindowPosition(this)
                    downXFromView = event.rawX - viewPosition.x.toFloat()
                    downYFromView = event.rawY - viewPosition.y.toFloat()
                    onMoveListener?.invoke(this, ACTION_MOVE_STARTED, viewPosition.x.toFloat(), viewPosition.y.toFloat())
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isMovable) {
                    val x = event.rawX - downXFromView
                    val y = event.rawY - downYFromView
                    OverlayViewUtil.moveView(this, x, y)
                    onMoveListener?.invoke(this, ACTION_MOVING, x, y)
                }
            }

            MotionEvent.ACTION_UP -> {
                isDown = false
                if (isMoving) {
                    isMoving = false
                    val viewPosition = OverlayViewUtil.getAbsoluteWindowPosition(this)
                    onMoveListener?.invoke(this, ACTION_MOVE_ENDED, viewPosition.x.toFloat(), viewPosition.y.toFloat())
                }
            }
        }
        return super.onTouchEvent(event)
    }

    fun setOnMoveListener(onMoveListener: ((view: View, action: Int, x: Float, y: Float) -> Unit)?) {
        this.onMoveListener = onMoveListener
    }
}