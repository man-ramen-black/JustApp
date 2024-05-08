package com.black.core.view

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding

// https://gun0912.tistory.com/38
// #CustomView 가이드
// https://0391kjy.tistory.com/28
// CustomView ViewBinding
abstract class DataBindingCustomView<T : ViewDataBinding> : FrameLayout {

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
        binding = DataBindingUtil.inflate(LayoutInflater.from(context), layoutId, this, true)

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
        bindVariable(binding)
        super.onAttachedToWindow()
    }

    protected open fun bindVariable(binding: T) {

    }
}