package com.black.core.view

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.view.children
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.lifecycleScope
import com.black.core.model.OverlayPosition
import com.black.core.util.Log
import com.black.core.util.OverlayViewUtil
import com.black.core.util.UiUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * 이동시킬 수 있는 Overlay 전용 View
 * Android에서 공식적으로 지원하는 Drag and drop 기능은 뷰를 직접적으로 이동시키지 않고 shadow를 끌어다 놓는 방식이기 때문에 직접 구현
 * https://developer.android.com/guide/topics/ui/drag-drop?hl=ko
 */
abstract class MovableOverlayView<T : ViewDataBinding> : OverlayView<T> {
    var isMovable = false
        set(value) {
            field = value
            if (isDown) {
                isMoving = true
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

    /**
     * 창을 처음 붙일 때 적용할 기본 위치
     *
     * null이면 위치를 지정하지 않음(하위 클래스가 [onInitializeWindowLayoutParams]에서 직접 지정)
     */
    protected open val defaultPosition: OverlayPosition? get() = null

    /**
     * 저장된 위치를 내보내는 Flow, null이면 저장된 위치를 쓰지 않음
     *
     * null이 아니면 [defaultPosition]도 함께 override해야 함(예외 시 대체 위치).
     * 첫 값을 반영하기 전까지 뷰를 숨겨 기본 위치가 깜빡이지 않게 함.
     * 부착마다 [onInitializeWindowLayoutParams]에서 한 번만 호출됨
     */
    protected open fun createPositionFlow(): Flow<OverlayPosition>? = null

    /**
     * 마지막으로 위치 설정에서 반영한 배치 값
     *
     * 끌어서 옮겼는지 판별에 사용
     */
    private var appliedPlacement: OverlayViewUtil.WindowPlacement? = null

    /** [onInitializeWindowLayoutParams]에서 만들어 [onAttachedToWindow]가 수집하는 위치 Flow */
    private var positionFlow: Flow<OverlayPosition>? = null

    /** 하위 클래스는 super를 호출한 뒤 자기 설정을 더함 */
    override fun onInitializeWindowLayoutParams(windowParams: WindowManager.LayoutParams) {
        super.onInitializeWindowLayoutParams(windowParams)
        // ViewRootImpl은 첫 순회에서 호스트 가시성을 onAttachedToWindow보다 먼저 읽어 첫 프레임을 판정하므로
        // 창을 추가(addView)하기 전인 여기서 숨겨야 기본 위치가 한 프레임도 보이지 않음
        positionFlow = createPositionFlow()
        if (positionFlow != null) visibility = INVISIBLE
        val position = defaultPosition ?: return
        val placement = OverlayViewUtil.calculateWindowPlacement(position, resources.displayMetrics.density)
        windowParams.gravity = placement.gravity
        windowParams.x = placement.x
        windowParams.y = placement.y
        appliedPlacement = placement
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val flow = positionFlow ?: return
        val fallback = defaultPosition ?: return
        lifecycleScope.launch {
            OverlayViewUtil.positionOrDefault(flow, fallback).collect { position ->
                val placement = OverlayViewUtil.calculateWindowPlacement(position, resources.displayMetrics.density)
                // 배치 반영에 실패해도 뷰가 계속 숨겨지지 않도록 표시는 항상 실행
                try {
                    OverlayViewUtil.applyWindowPlacement(this@MovableOverlayView, placement)
                    appliedPlacement = placement
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("apply overlay position failed : $e")
                } finally {
                    visibility = VISIBLE
                }
            }
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        handleTouchEvent(event)
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        handleTouchEvent(event)
        return super.onTouchEvent(event)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        Log.v()
        super.onConfigurationChanged(newConfig)

        // 화면 회전 시 뷰 위치를 유지할 경우 화면 밖으로 벗어날 수 있으므로 화면 회전 시 뷰 위치 이동
        // 유튜브 전체화면 등 화면 회전과 함께 상단바, 네비바 숨김 처리되는 경우 화면 사이즈 계산에 오차가 생기므로
        // 일정 시간 지연 후 위치 변경
        lifecycleScope.launch {
            delay(300)
            // 위치 설정대로 두고 끌어서 옮기지 않은 창은 WindowManager가 같은 가장자리 기준으로 다시 배치하므로 재배치하지 않음
            // 재배치하면 Top·Left 기준으로 바뀌어 오른쪽·아래·가운데 설정이 풀림
            val applied = appliedPlacement
            if (applied != null && applied == OverlayViewUtil.currentWindowPlacement(this@MovableOverlayView)) return@launch
            val viewPosition = OverlayViewUtil.getAbsoluteWindowPosition(this@MovableOverlayView)
            OverlayViewUtil.moveView(this@MovableOverlayView, viewPosition.x.toFloat(), viewPosition.y.toFloat())
        }
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
                        }
                    }

                    if (isMoving) {
                        OverlayViewUtil.moveView(this, x, y)
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                isDown = false
                if (isMoving) {
                    isMoving = false
                    val viewPosition = OverlayViewUtil.getAbsoluteWindowPosition(this)
                }
            }
        }
    }

}