package com.black.code.base.view

import android.content.Context
import android.util.AttributeSet
import android.view.WindowManager
import androidx.databinding.ViewDataBinding
import com.black.code.util.OverlayViewUtil

abstract class OverlayView<T : ViewDataBinding> : DataBindingCustomView<T> {
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

    abstract fun onInitializeWindowLayoutParams(windowParams: WindowManager.LayoutParams)

    open fun attachView() {
        OverlayViewUtil.attachView(this) {
            onInitializeWindowLayoutParams(it)
        }
    }

    open fun detachView() {
        OverlayViewUtil.detachView(this)
    }
}