package com.black.feature.usagetimer.overlay

import android.content.Context
import android.content.res.TypedArray
import android.view.WindowManager
import com.black.core.model.OverlayPosition
import com.black.core.util.OnClick
import com.black.core.view.MovableOverlayView
import com.black.feature.usagetimer.R
import com.black.feature.usagetimer.data.UsageTimerRepository
import com.black.feature.usagetimer.databinding.ViewUsageTimerBinding
import com.black.feature.usagetimer.model.UsageTimerDefaults
import kotlinx.coroutines.flow.Flow

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
class UsageTimerView(context: Context) : MovableOverlayView<ViewUsageTimerBinding>(context) {

    override val layoutId: Int get() = R.layout.view_usage_timer
    override val styleableId: IntArray? get() = null

    override fun initialize(binding: ViewUsageTimerBinding, typedArray: TypedArray?) {
        isMovable = true
    }

    /**
     * 저장된 위치를 읽기 전까지 쓰는 표시 위치
     *
     * 읽기에 실패했을 때도 이 위치를 씀.
     */
    override val defaultPosition: OverlayPosition get() = UsageTimerDefaults.POSITION

    /**
     * 저장된 표시 위치 Flow
     *
     * 설정 화면에서 바꾸면 떠 있는 타이머도 옮겨짐.
     */
    override fun createPositionFlow(): Flow<OverlayPosition> =
        UsageTimerRepository(context.applicationContext).getPositionFlow()

    /** 위치는 [MovableOverlayView]가 맡으므로 표시 애니메이션만 지정 */
    override fun onInitializeWindowLayoutParams(windowParams: WindowManager.LayoutParams) {
        super.onInitializeWindowLayoutParams(windowParams)
        windowParams.windowAnimations = android.R.style.Animation_Toast
    }

    /** attach 시 적용할 타이머 기준 시각(elapsedRealtime ms). null이면 0부터 시작 */
    var baseElapsedRealtime: Long? = null

    fun start() {
        binding.timer.start()
    }

    /** 지정한 기준 시각부터 이어서 표시(세션 유지 시 뷰 재생성 없이 기준점만 반영) */
    fun startFrom(baseElapsedRealtime: Long) {
        this.baseElapsedRealtime = baseElapsedRealtime
        binding.timer.startFrom(baseElapsedRealtime)
    }

    fun restart() {
        binding.timer.restart()
    }

    fun pause() {
        binding.timer.pause()
    }

    fun stop() {
        binding.timer.stop()
    }

    override fun detachView() {
        super.detachView()
        UsageTimerGlobal.onViewDetached(this)
    }

    override fun bindVariable(binding: ViewUsageTimerBinding) {
        super.bindVariable(binding)
        binding.onClickClose = OnClick { UsageTimerGlobal.closeByUser(this@UsageTimerView) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val base = baseElapsedRealtime
        if (base != null) {
            binding.timer.startFrom(base)
        } else {
            start()
        }
    }
}