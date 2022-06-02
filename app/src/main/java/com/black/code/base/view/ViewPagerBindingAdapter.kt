package com.black.code.base.view

import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import androidx.viewpager2.widget.ViewPager2
import com.black.code.util.Log

/*
#twoway #databinding #양방향 #데이터바인딩
https://developer.android.com/topic/libraries/data-binding/two-way
 */
object ViewPagerBindingAdapter {
    @BindingAdapter("currentItemSmooth")
    @JvmStatic fun setCurrentItemSmooth(view: ViewPager2, newValue: Int) {
        Log.d("${view.currentItem} : $newValue, size : ${view.adapter?.itemCount}")
        // Important to break potential infinite loops.
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
        Log.d("${view.currentItem} : $newValue")
        // Important to break potential infinite loops.
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
}