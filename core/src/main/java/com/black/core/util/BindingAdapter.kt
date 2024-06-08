package com.black.core.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.Dimension
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.doOnDetach
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.black.core.webkit.BKWebChromeClient
import com.black.core.webkit.BKWebView
import com.black.core.webkit.BKWebViewClient
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.Target
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/*
#twoway #databinding #양방향 #데이터바인딩
https://developer.android.com/topic/libraries/data-binding/two-way
 */
object BottomNavigationViewBindingAdapter {
    @BindingAdapter("itemIconTintList")
    @JvmStatic
    fun setItemIconTintList(view: BottomNavigationView, list: ColorStateList?) {
        view.itemIconTintList = list
    }
}

/**
 * View
 */
object ViewBindingAdapter {
    private const val DURATION = 300L

    @BindingAdapter("fadeVisible")
    @JvmStatic
    fun setFadeVisible(view: View, isFadeVisible: Boolean) {
        with (view) {
            clearAnimation()
            animate().cancel()
            if (isFadeVisible && !isVisible) {
                alpha = 0f
                visibility = View.VISIBLE
                animate()
                    .alpha(1f)
                    .apply { duration = DURATION }
                    .setListener(null)
            } else if (!isFadeVisible && isVisible) {
                animate()
                    .alpha(0f)
                    .apply { duration = DURATION }
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            this@with.visibility = View.GONE
                        }
                    })
            }
            null
        }
    }

    @BindingAdapter("onTouch")
    @JvmStatic
    fun setOnTouchListener(view: View, listener: View.OnTouchListener?) {
        view.setOnTouchListener(listener)
    }

    @BindingAdapter("backgroundTint")
    @JvmStatic
    fun setBackgroundTint(view: View, color: Int?) {
        view.backgroundTintList = ColorStateList.valueOf(color?.takeIf { it != 0 } ?: return)
    }

    @BindingAdapter("app:layout_constraintVertical_bias")
    @JvmStatic
    fun setVerticalBias(view: View, bias: Float) {
        val parent = view.parent as? ConstraintLayout
            ?: return

        ConstraintSet()
            .apply {
                clone(parent)
                setVerticalBias(view.id, bias)
            }
            .applyTo(parent)
    }

    @BindingAdapter("onScrollChange")
    @JvmStatic
    fun setOnScrollChangeListener(view: View, onScrollChangeListener: View.OnScrollChangeListener?) {
        view.setOnScrollChangeListener(onScrollChangeListener)
    }

    private const val CLICK_DELAY = 500L
    private val clickTime = AtomicLong(0L)

    /**
     * 전역 멀티 터치 방지 onClick
     *
     * 해당 onClick이 설정된 뷰들은 특정 뷰 onClick 시 500ms 동안 모든 뷰의 onClick 차단
     */
    @BindingAdapter("onClick")
    @JvmStatic
    fun setOnClickListener(view: View, listener: View.OnClickListener?) {
        view.setOnClickListener {
            if (System.currentTimeMillis() - clickTime.get() < CLICK_DELAY) {
                Log.v("Cannot click")
                @Suppress("LABEL_NAME_CLASH")
                return@setOnClickListener
            }
            clickTime.set(System.currentTimeMillis())
            listener?.onClick(view)
        }
    }

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

/**
 * ImageView
 */
object ImageViewBindingAdapter {

    @BindingAdapter("glideUrl", "glideCircle", "glideErrorDrawable", "glidePlaceholder", "glideErrorUrl", requireAll = false)
    @JvmStatic
    fun setGlideImage(view: ImageView, url: String?, isCircle: Boolean?, errorDrawable: Drawable?, placeholder: Drawable?, errorUrl: String?) {
        if (url == null) {
            return
        }

        Glide.with(view)
            .load(url)
            .transition(DrawableTransitionOptions.withCrossFade())
            .override(Target.SIZE_ORIGINAL) // 이미지 원본 사이즈 사용
            .run { if (isCircle == true) circleCrop() else this }
            .run {
                errorUrl ?: return@run this
                error(Glide.with(view).load(errorUrl))
            }
            .run { error(errorDrawable ?: return@run this) }
            .run { placeholder(placeholder ?: return@run this) }
            .into(view)
    }

    @BindingAdapter("android:src")
    @JvmStatic
    fun setImageResource(view: ImageView, resId: Int?) {
        if (resId == null || resId == 0) {
            return
        }
        view.setImageResource(resId)
    }

    @BindingAdapter("drawable")
    @JvmStatic
    fun setImageDrawable(view: ImageView, drawable: Drawable?) {
        if (drawable == null) {
            return
        }
        view.setImageDrawable(drawable)
    }

    @BindingAdapter("clipToOutline")
    @JvmStatic
    fun setClipToOutline(view: ImageView, isEnable: Boolean) {
        view.clipToOutline = isEnable
    }

    @BindingAdapter("cornerRadiusDp")
    @JvmStatic
    fun setCornerRadius(view: ImageView, radiusDp: Float) {
        if (radiusDp <= 0) {
            view.background = null
            view.clipToOutline = false
            return
        }

        view.background = GradientDrawable().apply {
            cornerRadius = UiUtil.dpToPx(view.context, radiusDp).toFloat()
            colors = intArrayOf(0xFFFFFF, 0xFFFFFF)
        }
        view.clipToOutline = true
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

    @BindingAdapter("offscreenPageLimit")
    @JvmStatic
    fun setOffscreenPageLimit(view: ViewPager2, limit: Int?) {
        view.offscreenPageLimit = limit ?: 1
    }

    @BindingAdapter("pageMarginDp")
    @JvmStatic
    fun setPageMargin(view: ViewPager2, @Dimension marginDp: Float?) {
        view.setPageTransformer(MarginPageTransformer(UiUtil.dpToPx(view.context, marginDp ?: 0f)))
    }

    @BindingAdapter("overScrollMode")
    @JvmStatic
    fun setOverScrollMode(view: ViewPager2, overScrollMode: Int) {
        view.getChildAt(0).overScrollMode = overScrollMode
    }

    @BindingAdapter("userInputEnabled")
    @JvmStatic
    fun setUserInputEnabled(view: ViewPager2, enabled: Boolean) {
        view.isUserInputEnabled = enabled
    }
}

/**
 * TabLayout
 */
object TabLayoutBindingAdapter {
    @BindingAdapter("tabMaxWidth")
    @JvmStatic
    fun setTabMaxWidth(view: TabLayout, tabMaxWidth: Float) {
        TabLayout::class.java
            .getDeclaredField("requestedTabMaxWidth")
            .apply { isAccessible = true }
            .set(view, tabMaxWidth.toInt())
        view.invalidate()
    }

    @BindingAdapter("tabMinWidth")
    @JvmStatic
    fun setTabMinWidth(view: TabLayout, tabMinWidth: Float) {
        TabLayout::class.java
            .getDeclaredField("requestedTabMinWidth")
            .apply { isAccessible = true }
            .set(view, tabMinWidth.toInt())
        view.invalidate()
    }

    @BindingAdapter("tabLineSpacingExtra")
    @JvmStatic
    fun setTabLineSpacingExtra(view: TabLayout, lineSpacingExtra: Float) {
        view.addOnLayoutChangeListener(View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            for (i in 0 until view.tabCount) {
                val tabView = view.getTabAt(i)?.view as? ViewGroup
                tabView?.children?.find { it is TextView }
                    ?.let { (it as TextView).setLineSpacing(lineSpacingExtra, 1f) }
            }
        }.also { view.doOnDetach { view -> view.removeOnLayoutChangeListener(it) } })
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
    fun setTextRes(view: TextView, @StringRes resId: Int) {
        view.setText(resId.takeIf { it != 0 } ?: return)
    }

    @BindingAdapter("textColorRes")
    @JvmStatic
    fun setTextColorRes(view: TextView, @ColorRes resId: Int?) {
        if (resId == null || resId == 0)
            return

        try {
            val colorInt = view.context.getColor(resId)
            view.setTextColor(colorInt)

        } catch (ignored: Exception) {
        }
    }


    @BindingAdapter("textColor")
    @JvmStatic
    fun setTextColor(view: TextView, color: Int) {
        view.setTextColor(color.takeIf { it != 0 } ?: return)
    }

    @BindingAdapter("underline")
    @JvmStatic
    fun setUnderline(view: TextView, applyUnderline: Boolean) {
        view.paintFlags = if (applyUnderline) {
            view.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        } else {
            view.paintFlags xor Paint.UNDERLINE_TEXT_FLAG
        }
    }

    @BindingAdapter("time", "dateFormat")
    @JvmStatic
    fun setDateTime(view: TextView, timeMs: Long, dateFormat: String?) {
        if (timeMs == 0L || dateFormat.isNullOrEmpty()) {
            return
        }

        val text = try {
            SimpleDateFormat(dateFormat, Locale.getDefault())
                .format(timeMs)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            return
        }

        view.text = text
    }
}

/**
 * MaterialButton
 */
object MaterialButtonBindingAdapter {
    @BindingAdapter(value = ["progress", "progressWithDisabled"], requireAll = false)
    @JvmStatic
    fun setProgress(view: MaterialButton, progress: Boolean, progressWithDisabled: Boolean?) {
        if (progress) {
            view.icon = CircularProgressDrawable(view.context).apply {
                setStyle(CircularProgressDrawable.DEFAULT)
                // 중심 반지름 크기이므로 아이콘 사이즈 / 2 - 선 두께
                centerRadius = view.iconSize.toFloat() / 2 - strokeWidth
                setColorSchemeColors(view.iconTint.defaultColor)
                callback = object : Drawable.Callback {
                    override fun invalidateDrawable(who: Drawable) {
                        view.invalidate()
                    }
                    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}
                    override fun unscheduleDrawable(who: Drawable, what: Runnable) {}
                }
                start()
            }
        } else {
            view.icon = null
        }

        if (progressWithDisabled != false) {
            view.isEnabled = !progress
        }
    }
}

object RecyclerViewBindingAdapter {
    @BindingAdapter(value = ["adapter", "itemList"], requireAll = false)
    @JvmStatic
    fun <T> setItemList(view: RecyclerView, adapter: ListAdapter<T, *>?, list: List<T>?) {
        view.adapter = adapter ?: return
        adapter.submitList(list ?: return)
    }

    /**
     * [DividerItemDecoration] 기반으로 재구성
     */
    @BindingAdapter(value = ["dividerHeight", "dividerPaddingHorizontal", "dividerPaddingVertical", "dividerColor"], requireAll = false)
    @JvmStatic
    fun setDivider(view: RecyclerView, dividerHeight: Float?, dividerPaddingHorizontal: Float?, dividerPaddingVertical: Float?, @ColorInt dividerColor: Int?) {
        val decoration = object: RecyclerView.ItemDecoration() {
            private val paint = Paint()
            init {
                paint.color = dividerColor ?: Color.parseColor("#FFFFFF")
            }

            override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                parent.layoutManager ?: return

                canvas.save()
                val left: Float
                val right: Float
                if (parent.clipToPadding) {
                    left = parent.paddingLeft + (dividerPaddingHorizontal ?: 0f)
                    right = parent.width - parent.paddingRight - (dividerPaddingHorizontal ?: 0f)
                    canvas.clipRect(
                        left.toInt(),
                        parent.paddingTop,
                        right.toInt(),
                        parent.height - parent.paddingBottom
                    )
                } else {
                    left = 0f
                    right = parent.width.toFloat()
                }

                // childCount - 1 : 마지막 Divider는 그리지 않음
                for (i in 0 until parent.childCount - 1) {
                    val child = parent.getChildAt(i)
                    val mBounds = Rect()

                    // 해당 아이템의 AlignBottom에 Divider를 그림
                    parent.getDecoratedBoundsWithMargins(child, mBounds)
                    val bottom = mBounds.bottom + child.translationY
                    val top: Float = bottom - (dividerHeight ?: 0f)
                    canvas.drawRect(left, top, right, bottom, paint)
                }
                canvas.restore()
            }

            /**
             * 아이템의 여백 설정
             */
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)

                // 첫번째 아이템 상단엔 여백을 추가하지 않음
                val top = if (position == 0) {
                    0
                } else {
                    dividerPaddingVertical?.toInt() ?: 0
                }

                // 마지막 아이템 하단엔 여백을 추가하지 않음
                val bottom = if (position == state.itemCount - 1) {
                    0
                } else {
                    // Divider가 AlignBottom에 그려지므로 Divider가 그려질 영역만큼 여백을 더 추가해줌
                    (dividerPaddingVertical?.toInt() ?: 0) + (dividerHeight?.toInt() ?: 0)
                }
                outRect.set(0, top, 0, bottom)
            }
        }
        view.addItemDecoration(decoration)
    }

    @BindingAdapter("itemAnimateEnabled")
    @JvmStatic
    fun setItemAnimateEnabled(view: RecyclerView, enabled: Boolean) {
        view.itemAnimator = if (enabled) {
            DefaultItemAnimator()
        } else {
            null
        }
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

    @BindingAdapter("alignItems")
    @JvmStatic
    fun setAlignItems(view: RecyclerView, @AlignItems value: Int) {
        val layoutManager = view.layoutManager as? FlexboxLayoutManager
        layoutManager?.alignItems = value
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

    @BindingAdapter("onItemTouchDisabled")
    @JvmStatic
    fun setOnItemTouchDisabled(view: RecyclerView, disabled: Boolean) {
        view.suppressLayout(disabled)
        if (!disabled) {
            return
        }

        view.addOnItemTouchListener(object: RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                return true
            }
        })
    }
}

object SwipeRefreshLayoutBindingAdapter {
    @BindingAdapter("onRefresh")
    @JvmStatic
    fun setOnRefreshListener(view: SwipeRefreshLayout, onRefreshListener: SwipeRefreshLayout.OnRefreshListener?) {
        view.setOnRefreshListener(onRefreshListener)
    }

    @BindingAdapter("refreshing")
    @JvmStatic
    fun setRefreshing(view: SwipeRefreshLayout, refreshing: Boolean) {
        view.isRefreshing = refreshing
    }
}

object CompoundButtonBindingAdapter {
    @BindingAdapter("checked")
    @JvmStatic
    fun setChecked(view: CompoundButton, checked: Boolean) {
        if (view.isChecked == checked) {
            // 동일한 값이면 설정하지 않음
            return
        }
        view.isChecked = checked
    }

    @InverseBindingAdapter(attribute = "checked")
    @JvmStatic fun getChecked(view: CompoundButton) : Boolean {
        return view.isChecked
    }

    @BindingAdapter("checkedAttrChanged")
    @JvmStatic fun setCheckedListener(
        view: CompoundButton,
        attrChanged : InverseBindingListener
    ) {
        view.setOnCheckedChangeListener { _, _ ->
            attrChanged.onChange()
        }
    }
}

object WebViewBindingAdapter {
    @BindingAdapter("url")
    @JvmStatic
    fun setUrl(view: WebView, url: String?) {
        if (view.url != url) {
            view.loadUrl(url ?: return)
        }
    }

    @BindingAdapter(value = ["onPageStarted", "onPageLoading", "onPageFinished", "onVisitedHistoryUpdated"], requireAll = false)
    @JvmStatic
    fun setWebViewClientCallback(
        view: BKWebView,
        onPageStarted: BKWebViewClient.OnPageStarted?,
        onPageLoading: BKWebViewClient.OnPageLoading?,
        onPageFinished: BKWebViewClient.OnPageFinished?,
        onVisitedHistoryUpdated: BKWebViewClient.OnVisitedHistoryUpdated?,
    ) {
        view.addWebViewClientCallback(object: BKWebViewClient.Callback {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                onPageStarted?.onPageStarted(view, url, favicon)
            }

            override fun onPageLoading(view: WebView, uri: Uri): Boolean {
                return onPageLoading?.onPageLoading(view, uri) ?: false
            }

            override fun onPageFinished(view: WebView, url: String, isError: Boolean) {
                onPageFinished?.onPageFinished(view, url, isError)
            }

            override fun onVisitedHistoryUpdated(view: WebView, url: String, isReload: Boolean) {
                onVisitedHistoryUpdated?.onVisitedHistoryUpdated(view, url, isReload)
            }
        })
    }

    @BindingAdapter(value = ["onReceivedTitle", "onProgressChanged"], requireAll = false)
    @JvmStatic
    fun setWebChromeClientCallback(
        view: BKWebView,
        onReceivedTitle: BKWebChromeClient.OnReceivedTitle?,
        onProgressChanged: BKWebChromeClient.OnProgressChanged?
    ) {
        view.addWebChromeClientCallback(object: BKWebChromeClient.Callback {
            override fun onReceivedTitle(view: WebView, title: String) {
                onReceivedTitle?.onReceivedTitle(view, title)
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                onProgressChanged?.onProgressChanged(view, newProgress)
            }
        })
    }

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

/**
 * layout xml variable로 OnCheckedChanged 이벤트를 설정하기 위한 인터페이스
 *
 * 기존 OnCheckedChangedListener에 View 디펜던시를 제거한 버전
 * Function1<Boolean, Unit> 방식은 사용 시 데이터 바인딩 버그가 발생하여 인터페이스로 구현
 */
fun interface OnCheckedChanged {
    fun invoke(checked: Boolean)
}