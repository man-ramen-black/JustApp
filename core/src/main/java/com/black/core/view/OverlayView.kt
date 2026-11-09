package com.black.core.view

import android.content.Context
import android.util.AttributeSet
import android.view.WindowManager
import androidx.databinding.ViewDataBinding
import com.black.core.util.OverlayViewUtil

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

    open fun onInitializeWindowLayoutParams(windowParams: WindowManager.LayoutParams) {}

    open fun attachView() {
        OverlayViewUtil.attachView(this) {
            onInitializeWindowLayoutParams(it)
        }
    }

    /** 창 추가를 시도하고 성공 여부 반환([OverlayViewUtil.tryAttachView]) */
    fun tryAttachView(): Boolean {
        return OverlayViewUtil.tryAttachView(this) {
            onInitializeWindowLayoutParams(it)
        }
    }

    open fun detachView() {
        OverlayViewUtil.detachView(this)
    }
}