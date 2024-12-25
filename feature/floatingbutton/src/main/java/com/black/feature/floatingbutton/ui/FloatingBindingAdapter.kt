package com.black.feature.floatingbutton.ui

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.updateLayoutParams
import androidx.databinding.BindingAdapter
import com.black.core.util.UiUtil
import java.lang.Float.max
import java.lang.Float.min

object FloatingBindingAdapter {
    @BindingAdapter("sizeDp")
    @JvmStatic
    fun setSize(view: View, sizeDp: Float) {
        if (sizeDp == 0f) {
            return
        }

        val sizePx = UiUtil.dpToPx(view.context, sizeDp)
        view.updateLayoutParams {
            width = sizePx
            height = sizePx
        }
    }

    @BindingAdapter(value = ["backgroundColor", "backgroundOpacity", "backgroundRadius"], requireAll = true)
    @JvmStatic
    fun setBackground(view: View, backgroundColor: Int, opacity: Float, radiusDp: Float) {
        view.background = GradientDrawable()
            .apply {
                shape = GradientDrawable.RECTANGLE
                setColor(backgroundColor)
                alpha = (255 * max(min(opacity, 1f), 0f)).toInt()
                cornerRadius = UiUtil.dpToPx(view.context, radiusDp).toFloat()
            }

    }

    @BindingAdapter("marginRightDp")
    @JvmStatic
    fun setMarginRightDp(view: View, dp: Float) {
        view.updateLayoutParams<MarginLayoutParams> {
            marginEnd = UiUtil.dpToPx(view.context, dp)
        }
    }

    @BindingAdapter("paddingDp")
    @JvmStatic
    fun setPaddingDp(view: View, dp: Float) {
        val padding = UiUtil.dpToPx(view.context, dp)
        view.setPadding(padding, padding, padding, padding)
    }
}