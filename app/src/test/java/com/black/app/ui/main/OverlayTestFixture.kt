package com.black.app.ui.main

import android.content.Context
import android.view.View
import android.view.WindowManager
import com.black.app.ui.main.keepscreenon.KeepScreenOnGlobal
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl

/** 오버레이 창의 WindowManager 섀도 조회와 오버레이 싱글턴 상태 정리를 맡는 테스트 헬퍼 */
object OverlayTestFixture {

    /** WindowManager에 추가된 뷰 목록 조회 */
    fun attachedViews(context: Context): List<View> =
        Shadow.extract<ShadowWindowManagerImpl>(context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).views

    /** WindowManager에 추가된 뷰 중 [T] 타입만 조회 */
    inline fun <reified T : View> attachedViewsOf(context: Context): List<T> =
        attachedViews(context).filterIsInstance<T>()

    /**
     * 켜짐 유지 창·화면 꺼짐 Receiver·활성 상태 해제
     *
     * [KeepScreenOnGlobal]은 프로세스 단일 객체라 테스트 사이에 상태가 남으므로 각 테스트의 @After에서 호출
     */
    fun deactivateKeepScreenOn(context: Context) {
        KeepScreenOnGlobal.deactivate(context)
    }
}
