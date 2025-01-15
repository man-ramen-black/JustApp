package com.black.core.view

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.view.children
import androidx.databinding.ViewDataBinding
import com.black.core.util.OverlayViewUtil
import com.black.core.util.UiUtil
import kotlin.math.sqrt

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
                isMoving = true
                onMoveListener?.invoke(this, ACTION_MOVE_STARTED, 0f, 0f)
            }
        }

    private var isDown = false
    private var isMoving = false
    private var downX = 0f
    private var downY = 0f
    private var downXFromView = 0f
    private var downYFromView = 0f

    // 클릭/이동 분기 처리용 이동 최소 거리
    private val moveDistance: Float = UiUtil.dpToPx(context, 20f).toFloat()

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(
        context,
        attrs,
        defStyleAttr,
        0
    )
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        handleTouchEvent(event)
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        handleTouchEvent(event)
        return super.onTouchEvent(event)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        val viewPosition = OverlayViewUtil.getAbsoluteWindowPosition(this)
        OverlayViewUtil.moveView(this, viewPosition.x.toFloat(), viewPosition.y.toFloat())
    }

    private fun handleTouchEvent(event: MotionEvent) {
        // View move
        // https://localazy.com/blog/floating-windows-on-android-5-moving-window
        // https://kimch3617.tistory.com/entry/%EC%95%88%EB%93%9C%EB%A1%9C%EC%9D%B4%EB%93%9C-%EA%B0%84%EB%8B%A8%ED%9E%88-View-%EC%9B%80%EC%A7%81%EC%9D%B4%EA%B2%8C-%ED%95%98%EA%B8%B0-Drag-and-Drop
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDown = true
                if (isMovable) {
                    val viewPosition = OverlayViewUtil.getAbsoluteWindowPosition(this)
                    downX = event.rawX
                    downY = event.rawY
                    downXFromView = event.rawX - viewPosition.x.toFloat()
                    downYFromView = event.rawY - viewPosition.y.toFloat()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isMovable) {
                    val x = event.rawX - downXFromView
                    val y = event.rawY - downYFromView

                    if (!isMoving) {
                        val distance = sqrt(((event.rawX - downX) * (event.rawX - downX) + (event.rawY - downY) * (event.rawY - downY)))
                        // down x, y에서 moveDistance 이상 벗어나면 뷰 이동 시작
                        if (distance > moveDistance) {
                            // 자식 뷰에 취소 이벤트 전달
                            val cancelEvent = MotionEvent.obtain(event)
                                .apply { action = MotionEvent.ACTION_CANCEL }
                            children.find { it.dispatchTouchEvent(cancelEvent) }
                            cancelEvent.recycle()

                            isMoving = true
                            onMoveListener?.invoke(this, ACTION_MOVE_STARTED, x, y)
                        }
                    }

                    if (isMoving) {
                        OverlayViewUtil.moveView(this, x, y)
                        onMoveListener?.invoke(this, ACTION_MOVING, x, y)
                    }
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
    }

    fun setOnMoveListener(onMoveListener: ((view: View, action: Int, x: Float, y: Float) -> Unit)?) {
        this.onMoveListener = onMoveListener
    }
}