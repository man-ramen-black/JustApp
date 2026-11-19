package com.black.test

import android.content.Context
import android.view.View
import android.view.WindowManager
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl

/** 오버레이 창의 WindowManager 섀도 조회 테스트 헬퍼 */
object OverlayTestUtil {

    /** WindowManager에 추가된 뷰 목록 조회 */
    fun attachedViews(context: Context): List<View> =
        Shadow.extract<ShadowWindowManagerImpl>(context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).views

    /** WindowManager에 추가된 뷰 중 [T] 타입만 조회 */
    inline fun <reified T : View> attachedViewsOf(context: Context): List<T> =
        attachedViews(context).filterIsInstance<T>()
}
