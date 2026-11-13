package com.black.feature.floatingbutton.service

import android.app.Notification
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import com.black.core.service.BaseForegroundService
import com.black.core.service.ServiceActivity
import com.black.core.util.OverlayViewUtil
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowToast

/**
 * [FloatingForegroundService]의 오버레이 부착 성공 시 보관, 부착 실패 시 권한 안내와 중지, 상시 알림 구성 검증
 *
 * 부착은 [OverlayViewUtil.attachView]만 대체해, 성공 시 실제 창 추가(시계 갱신이 끝없이 이어짐)를 피하고
 * 실패 시 권한 회수로 창 추가가 거부되는 상황을 실제 [OverlayViewUtil.tryAttachView] 경로로 재현
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FloatingForegroundServiceTest : BaseTest() {

    private val application = RuntimeEnvironment.getApplication()
    private var serviceController: ServiceController<FloatingForegroundService>? = null

    @Before
    fun setupOverlay() {
        ShadowSettings.setCanDrawOverlays(true)
        mockkObject(OverlayViewUtil)
    }

    /** 각 테스트 후 서비스 종료 */
    @After
    fun destroyService() {
        serviceController?.destroy()
    }

    /** 서비스 생성 후 시작 명령 전달 */
    private fun startService(): ServiceController<FloatingForegroundService> {
        return Robolectric.buildService(FloatingForegroundService::class.java)
            .create()
            .startCommand(0, 1)
            .also { serviceController = it }
    }

    /** 권한 확인 뒤 회수돼 시스템이 오버레이 창 추가를 거부하는 상황 */
    private fun rejectOverlayWindow() {
        every { OverlayViewUtil.attachView(any(), any()) } throws
            WindowManager.BadTokenException("permission denied for window type")
    }

    /**
     * 권한 확인 뒤 오버레이 부착이 실패하면 권한 안내 후 중지 인텐트로 서비스를 멈추는지 검증
     *
     * Given: 다른 앱 위에 표시 권한 허용, 창 추가 시 BadTokenException
     * When: 서비스 시작
     * Then: 예외 없이 권한 안내 토스트, 다른 앱 위에 표시 설정 화면 시작, EXTRA_STOP을 담은 서비스 시작 인텐트 기록
     */
    @Test
    fun test_01_attachFailureStopsWithPermissionGuide() = runTest {
        /** Given **/
        rejectOverlayWindow()

        /** When **/
        startService()

        /** Then **/
        assertEquals("다른 앱 위에 표시 권한 허용이 필요합니다.", ShadowToast.getTextOfLatestToast())
        assertEquals(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            shadowOf(application).nextStartedActivity.action,
        )
        val stopIntent = shadowOf(application).nextStartedService
        assertEquals(FloatingForegroundService::class.java.name, stopIntent.component?.className)
        assertTrue(stopIntent.getBooleanExtra(BaseForegroundService.EXTRA_STOP, false))
    }

    /**
     * 부착에 실패한 오버레이를 보관하지 않아 다음 시작 명령에서 다시 부착을 시도하는지 검증
     *
     * Given: 다른 앱 위에 표시 권한 허용, 창 추가 시 BadTokenException, 서비스 시작
     * When: 시작 명령 한 번 더 전달
     * Then: 창 추가 2회 시도(보관됐다면 두 번째 명령은 부착을 건너뜀)
     */
    @Test
    fun test_02_attachFailureDoesNotKeepView() = runTest {
        /** Given **/
        rejectOverlayWindow()
        val controller = startService()

        /** When **/
        controller.startCommand(0, 2)

        /** Then **/
        verify(exactly = 2) { OverlayViewUtil.attachView(any(), any()) }
    }

    /**
     * 오버레이 부착이 성공하면 보관하고 서비스를 멈추지 않는지 검증
     *
     * Given: 다른 앱 위에 표시 권한 허용, 창 추가 성공
     * When: 서비스 시작 후 시작 명령 한 번 더 전달
     * Then: 서비스 시작 인텐트와 설정 화면 시작 없음, 창 추가 1회(두 번째 명령은 보관한 오버레이로 부착 생략)
     */
    @Test
    fun test_03_attachSuccessKeepsViewWithoutStop() = runTest {
        /** Given **/
        every { OverlayViewUtil.attachView(any(), any()) } just Runs

        /** When **/
        val controller = startService()
        controller.startCommand(0, 2)

        /** Then **/
        assertNull(shadowOf(application).nextStartedService)
        assertNull(shadowOf(application).nextStartedActivity)
        verify(exactly = 1) { OverlayViewUtil.attachView(any(), any()) }
    }

    /**
     * 상시 알림이 중요도 낮음 채널의 지속 알림이고 터치 시 ServiceActivity를 거쳐 서비스를 중지하는지 검증
     *
     * Given: 다른 앱 위에 표시 권한 허용, 창 추가 성공
     * When: 서비스 시작
     * Then: 제목·문구, FloatingButton 채널 중요도 LOW, 지속 알림, contentIntent가 EXTRA_STOP을 담은 ServiceActivity 인텐트
     */
    @Test
    fun test_04_foregroundNotificationStopsThroughServiceActivity() = runTest {
        /** Given **/
        every { OverlayViewUtil.attachView(any(), any()) } just Runs

        /** When **/
        val controller = startService()

        /** Then **/
        val notification = shadowOf(controller.get()).lastForegroundNotification
        assertEquals("Floating Button", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("터치 시 종료", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        val channel = NotificationManagerCompat.from(application).getNotificationChannelCompat(notification.channelId)
        assertEquals(NotificationManagerCompat.IMPORTANCE_LOW, channel?.importance)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        val contentIntent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(ServiceActivity::class.java.name, contentIntent.component?.className)
        assertTrue(contentIntent.getBooleanExtra(BaseForegroundService.EXTRA_STOP, false))
    }
}
