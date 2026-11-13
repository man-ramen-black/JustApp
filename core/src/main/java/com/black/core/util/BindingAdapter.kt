package com.black.core.util

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.bottomnavigation.BottomNavigationView

/*
#twoway #databinding #양방향 #데이터바인딩
https://developer.android.com/topic/libraries/data-binding/two-way
 */

/** View */
object ViewBindingAdapter {
    @BindingAdapter(value = ["fitsStatusBar", "fitsNavigationBar"], requireAll = false)
    @JvmStatic
    fun setFitsSystemBars(view: View, fitsStatusBar: Boolean, fitsNavigationBar: Boolean) {
        // 기존 padding 값을 저장한 후 사용
        val padding = Rect(
            view.paddingStart,
            view.paddingTop,
            view.paddingEnd,
            view.paddingBottom,
        )

        /** [BottomNavigationView.applyWindowInsets] 참고하여 구현 */
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            val statusBarHeight = if (fitsStatusBar) statusBar.top else 0
            val navigationBarHeight = if (fitsNavigationBar) navigationBar.bottom else 0

            ViewCompat.setPaddingRelative(
                v,
                padding.left,
                padding.top + statusBarHeight,
                padding.right,
                padding.bottom + navigationBarHeight
            )
            insets
        }
    }
}

/** ImageView */
object ImageViewBindingAdapter {
    @BindingAdapter("android:src")
    @JvmStatic
    fun setImageResource(view: ImageView, resId: Int?) {
        if (resId == null || resId == 0) {
            return
        }
        view.setImageResource(resId)
    }
}

object ViewPagerBindingAdapter {
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

    @BindingAdapter("adapter")
    @JvmStatic
    fun setAdapter(view: ViewPager2, adapter: RecyclerView.Adapter<*>) {
        view.adapter = adapter
    }
}

object RecyclerViewBindingAdapter {
    /**
     * [adapter]를 연결하고 [list]를 제출
     *
     * 목록이 바뀔 때마다 두 속성을 함께 받으므로 이미 연결된 어댑터는 다시 지정하지 않음.
     * 같은 어댑터라도 [RecyclerView.setAdapter]는 모든 항목 뷰를 버리고 다시 그려 스크롤이 맨 위로 돌아감
     */
    @BindingAdapter(value = ["adapter", "itemList"], requireAll = false)
    @JvmStatic
    fun <T> setItemList(view: RecyclerView, adapter: ListAdapter<T, *>?, list: List<T>?) {
        adapter ?: return
        if (view.adapter !== adapter) {
            view.adapter = adapter
        }
        adapter.submitList(list ?: return)
    }

    @BindingAdapter("hasFixedSize")
    @JvmStatic
    fun setHasFixedSize(view: RecyclerView, value: Boolean) {
        view.setHasFixedSize(value)
    }

    @BindingAdapter("flexDirection")
    @JvmStatic
    fun setFlexDirection(view: RecyclerView, @FlexDirection value: Int) {
        val layoutManager = view.layoutManager as? FlexboxLayoutManager
        layoutManager?.flexDirection = value
    }

    @BindingAdapter("justifyContent")
    @JvmStatic
    fun setJustifyContent(view: RecyclerView, @JustifyContent value: Int) {
        val layoutManager = view.layoutManager as? FlexboxLayoutManager
        layoutManager?.justifyContent = value
    }

    @BindingAdapter("flexWrap")
    @JvmStatic
    fun setFlexWrap(view: RecyclerView, @FlexWrap value: Int) {
        val layoutManager = view.layoutManager as? FlexboxLayoutManager
        layoutManager?.flexWrap = value
    }
}

object WebViewBindingAdapter {
    @BindingAdapter("webView")
    @JvmStatic
    fun addWebView(parent: ViewGroup, webView: WebView?) {
        webView ?: return
        webView.layoutParams = (webView.layoutParams ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            .apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }

        (webView.parent as? ViewGroup)?.removeView(webView)
        parent.addView(webView)
    }
}

/**
 * layout xml variable로 onClick 이벤트를 설정하기 위한 인터페이스
 *
 * 기존 onClickListener에 View 디펜던시를 제거한 버전
 * Function0<Unit> 방식은 사용 시 데이터 바인딩 버그가 발생하여 인터페이스로 구현
 */
fun interface OnClick {
    fun invoke()
}
