package com.black.code.contents.usagetimechecker

import android.content.Context
import android.content.res.TypedArray
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import com.black.code.R
import com.black.code.base.BaseCustomView
import com.black.code.databinding.ViewUsageTimeCheckerBinding
import com.black.code.util.Log
import com.black.code.util.OverlayViewUtil
import com.black.code.util.Util
import kotlin.math.hypot

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
    companion object {
        private const val LONG_CLICK_DELAY = 250L
        private const val LONG_CLICK_RADIUS = 100
    }

    override val layoutId: Int
        get() = R.layout.view_usage_time_checker

    override val styleableId: IntArray?
        get() = null

    private var downX = 0f
    private var downY = 0f
    private var downXFromView = 0f
    private var downYFromView = 0f

    private var isMovingEnabled = false

    private val longClickHandler = Handler(Looper.getMainLooper())

    override fun initialize(binding: ViewUsageTimeCheckerBinding, typedArray: TypedArray?) {
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: run {
            return super.onTouchEvent(event)
        }

        // View move, drag and drop
        // https://localazy.com/blog/floating-windows-on-android-5-moving-window
        // https://kimch3617.tistory.com/entry/%EC%95%88%EB%93%9C%EB%A1%9C%EC%9D%B4%EB%93%9C-%EA%B0%84%EB%8B%A8%ED%9E%88-View-%EC%9B%80%EC%A7%81%EC%9D%B4%EA%B2%8C-%ED%95%98%EA%B8%B0-Drag-and-Drop
        when(event.action) {
            MotionEvent.ACTION_OUTSIDE -> {
                Log.d("ACTION_OUTSIDE")
            }

            MotionEvent.ACTION_DOWN -> {
                val windowParams = layoutParams as WindowManager.LayoutParams
                downX = event.rawX
                downY = event.rawY

                val viewPosition = OverlayViewUtil.getAbsoluteWindowPosition(this)
                downXFromView = downX - viewPosition.x.toFloat()
                downYFromView = downY - viewPosition.y.toFloat()
                longClickHandler.postDelayed({
                    Log.e("long clicked")
                    isMovingEnabled = true
                }, LONG_CLICK_DELAY)
            }

            MotionEvent.ACTION_MOVE -> {
                val distanceX = event.rawX - downX
                val distanceY = event.rawY - downY
                if (isMovingEnabled) {
                    val x = event.rawX - downXFromView
                    val y = event.rawY - downYFromView
                    OverlayViewUtil.moveView(this, x, y)
                }

                // hypot : 피타고라스 정리로 빗변의 길이를 구함 == sqrt(x.toDouble().pow(2), y.toDouble().pow(2))
                // move 좌표에서 down 좌표를 뺀 값으로 hypot 계산을 하면 down 좌표 기준으로 멀어진 거리가 절대값으로 구해짐
                // down 좌표에서 멀어진 거리를 LONG_CLICK_RADIUS와 비교하여 longTouch 이벤트를 실행
                else if (hypot(distanceX, distanceY) > LONG_CLICK_RADIUS) {
                    Log.e("long click cancel hypot")
                    longClickHandler.removeCallbacksAndMessages(null)
                }
            }

            MotionEvent.ACTION_UP -> {
                Log.e("long click cancel ACTION_UP")
                longClickHandler.removeCallbacksAndMessages(null)
                isMovingEnabled = false
                clearAnimation()
            }
        }
        return super.onTouchEvent(event)
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

    fun attachView() {
        binding.viewModel = this
        start()
        OverlayViewUtil.attachView(this) {
            it.gravity = Gravity.TOP or Gravity.RIGHT
            it.x = Util.dpToPx(context, 20f)
            it.y = Util.dpToPx(context, 20f)
        }
    }

    fun detachView() {
        OverlayViewUtil.detachView(this)
    }
}