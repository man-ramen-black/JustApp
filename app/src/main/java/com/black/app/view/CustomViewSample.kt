package com.black.app.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.black.app.R
import com.black.app.databinding.ViewSampleBinding

// CustomView 가이드
// https://gun0912.tistory.com/38
// CustomView ViewBinding
// https://0391kjy.tistory.com/28
// CustomView ViewModel
// https://medium.com/@nicholas.rose/level-up-your-custom-views-d0c6f69fd1ec
// findViewTreeViewModelStoreOwner
// https://pluu.github.io/blog/android/2020/12/21/viewtreelifecycle/
class CustomViewSample : LinearLayout {

    private val binding = ViewSampleBinding.inflate(LayoutInflater.from(context), this, true)

    constructor(context: Context?) : super(context) {
        initialize(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initialize(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initialize(attrs)
    }

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize(attrs)
    }

    var text : String
        get() = binding.text ?: ""
        set(value) {
            binding.text = value
        }

    private fun initialize(attrs: AttributeSet?) {
        if (attrs == null) {
            return
        }

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CustomViewSample)
        binding.text = typedArray.getString(R.styleable.CustomViewSample_text) ?: ""
        typedArray.recycle()
    }
}