package com.black.core.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

object UiUtil {
    fun pxToDp(context: Context, px: Int) : Float {
        return px.toFloat() / context.resources.displayMetrics.density
    }

    fun dpToPx(context: Context, dp: Float) : Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
    }

    /**
     * https://myksb1223.github.io/develop_diary/2019/03/28/Screen-size-in-Android.html
     */
    fun getScreenSize(context: Context) : Point {
        val metrics = context.resources.displayMetrics
        return Point(metrics.widthPixels, metrics.heightPixels)
    }

    /** 현재 시스템바를 제외한 콘텐츠 영역 반환 */
    fun getCurrentViewportSize(context: Context): Point {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val windowInsets = windowMetrics.windowInsets

            val insets = windowInsets.getInsets(
                WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars()
            )

            val bounds =  windowMetrics.bounds
            Point(bounds.width() - insets.left - insets.right, bounds.height() - insets.top - insets.bottom)
        } else {
            // Android R 이전엔 추가 처리가 필요하지만 임시로 화면 전체 사이즈 반환
            // 전체화면 여부 확인 후 getIdentifier 로 시스템 바 높이 획득 후 계산 필요
            getScreenSize(context)
        }
    }

    /**
     * 몰입 모드(전체 화면, 상단바, 하단바 숨김) 설정
     */
    fun setImmersiveMode(window: Window?, isActivated: Boolean) {
        window ?: run {
            Log.w("window is null")
            return
        }

        // R 이상
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.decorView.windowInsetsController

            // 상단바, 하단바 노출 설정
            if (isActivated) {
                controller?.hide(WindowInsets.Type.statusBars())
                controller?.hide(WindowInsets.Type.navigationBars())

                // 호출하지 않을 경우 컷아웃 영역까지 확장되지 않고, cutout inset이 onApplyWindowInsets으로 들어오지 않음
                window.setDecorFitsSystemWindows(false)
                // immersiveMode 설정 시 시스템바 노출 규칙 적용
                // SYSTEM_UI_FLAG_IMMERSIVE_STICKY 와 동일한 옵션 (가장자리 스와이프 시 잠깐 노출)
                controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            } else {
                controller?.show(WindowInsets.Type.statusBars())
                controller?.show(WindowInsets.Type.navigationBars())

            }
        }
        // R 미만
        else {
            if (isActivated) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    /**
     * Cutout 설정
     * @param cutoutMode ex) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
     */
    fun setCutout(window: Window?, @Cutout cutoutMode: Int) {
        window ?: run {
            Log.w("window is null")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }

        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = cutoutMode
        }
    }

    /**
     * TabLayout과 ViewPager2 연결
     */
    fun TabLayout.setupWithViewPager2(viewPager: ViewPager2, autoRefresh: Boolean = true, smoothScroll: Boolean = true, tabText: ((TabLayout.Tab, Int) -> String)? = null) {
        TabLayoutMediator(this, viewPager, autoRefresh, smoothScroll) { tab, position ->
            tab.text = tabText?.invoke(tab, position) ?: ""
        }.attach()
    }
}

annotation class Cutout {
    companion object {
        const val DEFAULT = 0
        const val SHORT_EDGES = 1
        const val NEVER = 2
        const val ALWAYS = 3
    }
}