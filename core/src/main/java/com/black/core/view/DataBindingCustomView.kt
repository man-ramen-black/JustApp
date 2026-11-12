package com.black.core.view

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
// https://gun0912.tistory.com/38
// #CustomView 가이드
// https://0391kjy.tistory.com/28
// CustomView ViewBinding
/**
 * 데이터 바인딩과 자체 수명주기를 가진 커스텀 뷰
 *
 * 창에 붙으면 RESUMED, 떼면 DESTROYED가 되고 DESTROYED에서 다시 시작할 수 없으므로 한 인스턴스는 한 번만 붙임.
 * 다시 표시할 때는 새 인스턴스를 만듦
 */
abstract class DataBindingCustomView<T : ViewDataBinding> : FrameLayout, LifecycleOwner {

    private lateinit var lifecycleRegistry: LifecycleRegistry

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    protected lateinit var binding : T

    /**
     * get() = 형태로 필수 구현
     * = 또는 by lazy로 할 경우 생성자 완료 후 초기화되므로 initialize에서 접근 시 NPE 발생
     */
    protected abstract val layoutId: Int

    /**
     * get() = 형태로 필수 구현
     * = 또는 by lazy로 할 경우 생성자 완료 후 초기화되므로 initialize에서 접근 시 NPE 발생
     */
    protected abstract val styleableId: IntArray?

    constructor(context: Context) : super(context) {
        initializeInternal(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initializeInternal(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initializeInternal(attrs)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        initializeInternal(attrs)
    }

    protected abstract fun initialize(binding: T, typedArray: TypedArray?)

    private fun initializeInternal(attrs: AttributeSet?) {
        lifecycleRegistry = LifecycleRegistry(this)

        binding = DataBindingUtil.inflate<T>(LayoutInflater.from(context), layoutId, this, true)
            .apply { lifecycleOwner = this@DataBindingCustomView }

        val typedArray = let {
            context.obtainStyledAttributes(
                attrs ?: return@let null,
                styleableId ?: return@let null
            )
        }

        initialize(binding, typedArray)
        typedArray?.recycle()
    }

    @CallSuper
    override fun onAttachedToWindow() {
        // 변수 바인딩 후 수명주기 시작(ON_START에서 대기 중인 바인딩이 실행되므로, 먼저 시작하면 미설정 변수 null이 non-null BindingAdapter 인자로 전달)
        bindVariable(binding)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    protected open fun bindVariable(binding: T) {}
}