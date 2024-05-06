package com.black.app.util

import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView

/*
#twoway #databinding #양방향 #데이터바인딩
https://developer.android.com/topic/libraries/data-binding/two-way
 */
object BottomNavigationViewBindingAdapter {
    @BindingAdapter("itemIconTintList")
    @JvmStatic
    fun setItemIconTintList(view: BottomNavigationView, @ColorRes colorResId: Int?) {
        val colorStateList = colorResId?.let {
            ContextCompat.getColorStateList(view.context, it)
        }
        view.itemIconTintList = colorStateList
    }
}

object RecyclerViewBindingAdapter {
    @BindingAdapter("adapter")
    @JvmStatic
    fun setAdapter(view: RecyclerView, adapter: RecyclerView.Adapter<*>) {
        view.adapter = adapter
    }
}

/**
 * ImageView
 */
object ImageViewBindingAdapter {
    @BindingAdapter("glideUrl", "glideCircle", "glideError")
    @JvmStatic
    fun setGlideImage(view: ImageView, url: String?, isCircle: Boolean?, @DrawableRes errorDrawableResId: Int?) {
        if (url == null) {
            Log.d("url is null")
            return
        }

//        Glide.with(view)
//            .asBitmap()
//            .load(url)
//            .run { if (isCircle == true) circleCrop() else this }
//            .error(errorDrawableResId ?: 0)
//            .into(view)
    }

    @BindingAdapter("glideUrl")
    @JvmStatic
    fun setGlideImage(view: ImageView, url: String?) {
        setGlideImage(view, url, false, null)
    }

    @BindingAdapter("android:src")
    @JvmStatic
    fun setImageResource(view: ImageView, resId: Int?) {
        view.setImageResource(resId ?: return)
    }
}

/**
 * ViewPager
 */
object ViewPagerBindingAdapter {
    interface OnPagerScrollStateChangedListener {
        fun onPagerScrollStateChanged(state: Int)
    }

    @BindingAdapter("currentItemSmooth")
    @JvmStatic fun setCurrentItemSmooth(view: ViewPager2, newValue: Int) {
        if (view.currentItem != newValue) {
            view.setCurrentItem(newValue, true)
        }
    }

    @InverseBindingAdapter(attribute = "currentItemSmooth")
    @JvmStatic fun getCurrentItemSmooth(view: ViewPager2) : Int {
        return view.currentItem
    }

    @BindingAdapter("currentItemSmoothAttrChanged")
    @JvmStatic fun setCurrentItemSmoothListener(
        view: ViewPager2,
        attrChanged : InverseBindingListener
    ) {
        view.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                attrChanged.onChange()
            }
        })
    }

    @BindingAdapter("currentItem")
    @JvmStatic fun setCurrentItem(view: ViewPager2, newValue: Int) {
        if (view.currentItem != newValue) {
            view.setCurrentItem(newValue, false)
        }
    }

    @InverseBindingAdapter(attribute = "currentItem")
    @JvmStatic fun getCurrentItem(view: ViewPager2) : Int {
        return view.currentItem
    }

    @BindingAdapter("currentItemAttrChanged")
    @JvmStatic fun setCurrentItemListener(
        view: ViewPager2,
        attrChanged : InverseBindingListener
    ) {
        view.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                attrChanged.onChange()
            }
        })
    }

    @BindingAdapter("onPageScrollStateChanged")
    @JvmStatic fun setOnPagerScrollStateChangedListener(view: ViewPager2, listener: OnPagerScrollStateChangedListener?) {
        if (listener == null) {
            Log.w("onPageScrollStateChanged is null")
            return
        }

        view.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                listener.onPagerScrollStateChanged(state)
            }
        })
    }

    @BindingAdapter("adapter")
    @JvmStatic
    fun setAdapter(view: ViewPager2, adapter: RecyclerView.Adapter<*>) {
        view.adapter = adapter
    }
}


/**
 * EditText
 */
object EditTextBindingAdapter {
    @BindingAdapter("onFocusChange")
    @JvmStatic
    fun setOnFocusChangeListener(view: EditText, onFocusChangeListener: View.OnFocusChangeListener?) {
        view.onFocusChangeListener = onFocusChangeListener
    }

    @BindingAdapter("errorRes")
    @JvmStatic
    fun setErrorResId(view: EditText, @StringRes errorResId: Int?) {
        view.error = errorResId?.takeIf { it != 0 }?.let { view.context.getString(it) }
    }
}

/**
 * TextView
 */
object TextViewBindingAdapter {
    @BindingAdapter("textRes")
    @JvmStatic
    fun setTextRes(view: TextView, @StringRes stringResId: Int) {
        if (stringResId == 0) {
            return
        }
        view.setText(stringResId)
    }
}