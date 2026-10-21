package com.black.app.ui.maintab.main.keepscreenon

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.black.test.BaseTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * [KeepScreenOnGlobal] 오버레이 창 추가·제거와 화면 꺼짐 자동 해제 검증
 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class KeepScreenOnGlobalTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /**
     * 각 테스트 후 활성 상태 해제(싱글턴 상태 격리)
     */
    @After
    fun deactivateKeepScreenOn() {
        KeepScreenOnGlobal.deactivate(context)
    }

    /**
     * 권한 허용 상태에서 활성화 시 FLAG_KEEP_SCREEN_ON 창 추가 검증
     *
     * Given: 다른 앱 위에 표시 권한 허용
     * When: activate 호출
     * Then: true 반환, 활성 상태, FLAG_KEEP_SCREEN_ON 포함 창 1개 추가
     */
    @Test
    fun test_01_activateAddsKeepScreenOnWindow() {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)

        /** When **/
        val result = KeepScreenOnGlobal.activate(context)

        /** Then **/
        assertTrue(result)
        assertTrue(KeepScreenOnGlobal.isActive.value)
        val windowParams = attachedViews().single().layoutParams as WindowManager.LayoutParams
        assertTrue(windowParams.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        assertEquals(KeepScreenOnGlobal.WINDOW_TITLE, windowParams.title.toString())
    }

    /**
     * 활성 중 화면 꺼짐 브로드캐스트 수신 시 자동 해제 검증
     *
     * Given: 권한 허용 후 활성화
     * When: ACTION_SCREEN_OFF 브로드캐스트 전송
     * Then: 비활성 상태, 창 제거
     */
    @Test
    fun test_02_screenOffDeactivates() {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)
        KeepScreenOnGlobal.activate(context)

        /** When **/
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()

        /** Then **/
        assertFalse(KeepScreenOnGlobal.isActive.value)
        assertTrue(attachedViews().isEmpty())
    }

    /**
     * 권한 미허용 상태에서 활성화 실패 검증
     *
     * Given: 다른 앱 위에 표시 권한 미허용
     * When: activate 호출
     * Then: false 반환, 비활성 상태, 창 없음
     */
    @Test
    fun test_03_activateWithoutPermissionKeepsInactive() {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(false)

        /** When **/
        val result = KeepScreenOnGlobal.activate(context)

        /** Then **/
        assertFalse(result)
        assertFalse(KeepScreenOnGlobal.isActive.value)
        assertTrue(attachedViews().isEmpty())
    }

    /**
     * 토글 2회 시 활성 후 비활성 전환 검증
     *
     * Given: 권한 허용
     * When: toggle 2회 호출
     * Then: 첫 호출 true, 두 번째 호출 false, 창 없음
     */
    @Test
    fun test_04_toggleTwiceDeactivates() {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)

        /** When **/
        val firstResult = KeepScreenOnGlobal.toggle(context)
        val secondResult = KeepScreenOnGlobal.toggle(context)

        /** Then **/
        assertTrue(firstResult)
        assertFalse(secondResult)
        assertFalse(KeepScreenOnGlobal.isActive.value)
        assertTrue(attachedViews().isEmpty())
    }

    /**
     * 연속 활성화 시 창 중복 추가 방지 검증
     *
     * Given: 권한 허용
     * When: activate 2회 호출
     * Then: 두 번째 호출 true 반환, 창 1개만 추가
     */
    @Test
    fun test_05_activateTwiceAddsSingleWindow() {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)

        /** When **/
        KeepScreenOnGlobal.activate(context)
        val secondResult = KeepScreenOnGlobal.activate(context)

        /** Then **/
        assertTrue(secondResult)
        assertEquals(1, attachedViews().size)
    }

    /**
     * WindowManager에 추가된 뷰 목록 조회
     */
    private fun attachedViews(): List<View> =
        Shadow.extract<ShadowWindowManagerImpl>(windowManager).views
}
