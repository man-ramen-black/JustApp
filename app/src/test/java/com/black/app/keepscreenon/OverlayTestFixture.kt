package com.black.app.keepscreenon

import android.content.Context
import com.black.app.keepscreenon.overlay.KeepScreenOnGlobal
import com.black.test.OverlayTestUtil

/**
 * 오버레이 싱글턴 상태 정리를 맡는 테스트 헬퍼
 *
 * WindowManager 섀도 조회는 기능 모듈 테스트도 쓰도록 [OverlayTestUtil]에 있음
 */
object OverlayTestFixture {

    /**
     * 켜짐 유지 창·화면 꺼짐 Receiver·활성 상태 해제
     *
     * [KeepScreenOnGlobal]은 프로세스 단일 객체라 테스트 사이에 상태가 남으므로 각 테스트의 @After에서 호출
     */
    fun deactivateKeepScreenOn(context: Context) {
        KeepScreenOnGlobal.deactivate(context)
    }
}
