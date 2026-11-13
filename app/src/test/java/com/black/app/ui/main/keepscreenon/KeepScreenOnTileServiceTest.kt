package com.black.app.ui.main.keepscreenon

import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.black.app.ui.main.OverlayTestFixture
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/** [KeepScreenOnTileService] 타일 클릭 시 권한 여부에 따른 분기 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Config(sdk = [34])
class KeepScreenOnTileServiceTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** 싱글턴 상태 격리를 위해 켜짐 유지 해제 */
    @After
    fun deactivateKeepScreenOn() {
        OverlayTestFixture.deactivateKeepScreenOn(context)
    }

    /**
     * 다른 앱 위에 표시 권한이 있으면 클릭할 때마다 켜짐 유지를 켜고 끄는지 검증
     *
     * Given: 권한 허용, 켜짐 유지 꺼짐
     * When: 타일 두 번 클릭
     * Then: 첫 클릭 뒤 켜짐, 두 번째 클릭 뒤 꺼짐
     */
    @Test
    fun test_01_clickWithPermissionToggles() = runTest {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)
        val service = Robolectric.buildService(KeepScreenOnTileService::class.java).create().get()

        /** When **/
        service.onClick()
        val activeAfterFirstClick = KeepScreenOnGlobal.isActive.value
        service.onClick()

        /** Then **/
        assertTrue(activeAfterFirstClick)
        assertFalse(KeepScreenOnGlobal.isActive.value)
    }

    /**
     * 켜 둔 상태에서 권한이 회수된 뒤 클릭하면 토글하지 않고 켜짐 유지를 해제하는지 검증
     *
     * Given: 권한 허용 상태로 켜짐 유지를 켠 뒤 권한 회수
     * When: 타일 클릭
     * Then: 켜짐 유지 꺼짐, 권한 설정 화면을 여는 startActivityAndCollapse 호출
     */
    @Test
    fun test_02_clickAfterPermissionRevokedDeactivates() = runTest {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)
        // 시스템에 바인딩되지 않은 테스트 서비스는 startActivityAndCollapse가 실패하므로 호출만 기록
        val service = spyk(Robolectric.buildService(KeepScreenOnTileService::class.java).create().get())
        every { service.startActivityAndCollapse(any<PendingIntent>()) } just Runs
        service.onClick()
        ShadowSettings.setCanDrawOverlays(false)

        /** When **/
        service.onClick()

        /** Then **/
        assertFalse(KeepScreenOnGlobal.isActive.value)
        verify(exactly = 1) { service.startActivityAndCollapse(any<PendingIntent>()) }
    }
}
