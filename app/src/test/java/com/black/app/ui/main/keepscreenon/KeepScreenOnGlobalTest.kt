package com.black.app.ui.main.keepscreenon

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.black.app.ui.main.OverlayTestFixture
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/** [KeepScreenOnGlobal] 오버레이 창 추가·제거와 화면 꺼짐 자동 해제 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class KeepScreenOnGlobalTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** 각 테스트 후 활성 상태 해제(싱글턴 상태 격리) */
    @After
    fun deactivateKeepScreenOn() {
        OverlayTestFixture.deactivateKeepScreenOn(context)
    }

    /**
     * 권한 허용 상태에서 활성화 시 FLAG_KEEP_SCREEN_ON 창 추가 검증
     *
     * Given: 다른 앱 위에 표시 권한 허용
     * When: activate 호출
     * Then: true 반환, 활성 상태, FLAG_KEEP_SCREEN_ON 포함 창 1개 추가
     */
    @Test
    fun test_01_activateAddsKeepScreenOnWindow() = runTest {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)

        /** When **/
        val result = KeepScreenOnGlobal.activate(context)

        /** Then **/
        assertTrue(result)
        assertTrue(KeepScreenOnGlobal.isActive.value)
        val windowParams = OverlayTestFixture.attachedViews(context).single().layoutParams as WindowManager.LayoutParams
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
    fun test_02_screenOffDeactivates() = runTest {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)
        KeepScreenOnGlobal.activate(context)

        /** When **/
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        idleMainLooper()

        /** Then **/
        assertFalse(KeepScreenOnGlobal.isActive.value)
        assertTrue(OverlayTestFixture.attachedViews(context).isEmpty())
    }

    /**
     * 권한 미허용 상태에서 활성화 실패 검증
     *
     * Given: 다른 앱 위에 표시 권한 미허용
     * When: activate 호출
     * Then: false 반환, 비활성 상태, 창 없음
     */
    @Test
    fun test_03_activateWithoutPermissionKeepsInactive() = runTest {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(false)

        /** When **/
        val result = KeepScreenOnGlobal.activate(context)

        /** Then **/
        assertFalse(result)
        assertFalse(KeepScreenOnGlobal.isActive.value)
        assertTrue(OverlayTestFixture.attachedViews(context).isEmpty())
    }

    /**
     * 토글 2회 시 활성 후 비활성 전환 검증
     *
     * Given: 권한 허용
     * When: toggle 2회 호출
     * Then: 첫 호출 true, 두 번째 호출 false, 창 없음
     */
    @Test
    fun test_04_toggleTwiceDeactivates() = runTest {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)

        /** When **/
        val firstResult = KeepScreenOnGlobal.toggle(context)
        val secondResult = KeepScreenOnGlobal.toggle(context)

        /** Then **/
        assertTrue(firstResult)
        assertFalse(secondResult)
        assertFalse(KeepScreenOnGlobal.isActive.value)
        assertTrue(OverlayTestFixture.attachedViews(context).isEmpty())
    }

    /**
     * 연속 활성화 시 창 중복 추가 방지 검증
     *
     * Given: 권한 허용
     * When: activate 2회 호출
     * Then: 두 번째 호출 true 반환, 창 1개만 추가
     */
    @Test
    fun test_05_activateTwiceAddsSingleWindow() = runTest {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)

        /** When **/
        KeepScreenOnGlobal.activate(context)
        val secondResult = KeepScreenOnGlobal.activate(context)

        /** Then **/
        assertTrue(secondResult)
        assertEquals(1, OverlayTestFixture.attachedViews(context).size)
    }
}
