package com.black.feature.floatingbutton.ui

import com.black.feature.floatingbutton.service.FloatingButtonController
import com.black.test.BaseTest
import com.black.test.collectValues
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [FloatingSettingViewModel]의 실행 상태 반영·서비스 시작과 중지·권한 안내 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FloatingSettingViewModelTest : BaseTest() {

    @MockK
    lateinit var controller: FloatingButtonController

    private val running = MutableStateFlow(false)

    @Before
    fun setupMocks() {
        every { controller.running } returns running
        every { controller.canDrawOverlays() } returns true
        every { controller.isAccessibilityServiceEnabled() } returns true
        every { controller.start() } just Runs
        every { controller.stop() } just Runs
    }

    /** ViewModel 생성 후 init의 실행 상태 구독 시작까지 대기 */
    private fun createViewModel() = FloatingSettingViewModel(controller)
        .also { waitCoroutine() }

    /**
     * 생성 시 서비스 실행·권한·접근성 서비스 상태를 반영하는지 검증
     *
     * Given: 서비스 실행 중, 다른 앱 위에 표시 권한 없음, 접근성 서비스 꺼짐
     * When: ViewModel 생성
     * Then: uiState에 각 값 반영
     */
    @Test
    fun test_01_initReflectsStatus() = runTest {
        /** Given **/
        running.value = true
        every { controller.canDrawOverlays() } returns false
        every { controller.isAccessibilityServiceEnabled() } returns false

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        val uiState = viewModel.uiState.value
        assertTrue(uiState.running)
        assertFalse(uiState.overlayPermissionGranted)
        assertFalse(uiState.accessibilityServiceEnabled)
    }

    /**
     * 화면이 떠 있는 동안 알림·바로가기로 서비스가 켜지고 꺼지면 스위치 상태가 따라가는지 검증
     *
     * Given: 서비스 중지 상태로 ViewModel 생성
     * When: 서비스 실행 → 중지
     * Then: running이 true → false로 반영
     */
    @Test
    fun test_02_runningFollowsService() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When / Then **/
        running.value = true
        waitCoroutine()
        assertTrue(viewModel.uiState.value.running)

        running.value = false
        waitCoroutine()
        assertFalse(viewModel.uiState.value.running)
    }

    /**
     * 권한이 있으면 켤 때 서비스를 시작하고 권한 안내 다이얼로그를 띄우지 않는지 검증
     *
     * Given: 다른 앱 위에 표시 권한 있음
     * When: setRunning(true)
     * Then: controller.start() 호출, 권한 안내 다이얼로그 숨김
     */
    @Test
    fun test_03_startWithPermission() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.setRunning(true)

        /** Then **/
        verify(exactly = 1) { controller.start() }
        assertFalse(viewModel.uiState.value.showOverlayPermissionDialog)
    }

    /**
     * 권한 없이 켜면 서비스를 시작하지 않고 권한 안내 다이얼로그를 띄우는지 검증
     *
     * Given: 다른 앱 위에 표시 권한 없음
     * When: setRunning(true)
     * Then: controller.start() 미호출, 권한 안내 다이얼로그 표시
     */
    @Test
    fun test_04_startWithoutPermissionShowsPermissionDialog() = runTest {
        /** Given **/
        every { controller.canDrawOverlays() } returns false
        val viewModel = createViewModel()

        /** When **/
        viewModel.setRunning(true)

        /** Then **/
        verify(exactly = 0) { controller.start() }
        assertTrue(viewModel.uiState.value.showOverlayPermissionDialog)
    }

    /**
     * 끄면 권한 확인 없이 서비스를 중지하는지 검증
     *
     * Given: 서비스 실행 중
     * When: setRunning(false)
     * Then: controller.stop() 호출, controller.start() 미호출
     */
    @Test
    fun test_05_stop() = runTest {
        /** Given **/
        running.value = true
        val viewModel = createViewModel()

        /** When **/
        viewModel.setRunning(false)

        /** Then **/
        verify(exactly = 1) { controller.stop() }
        verify(exactly = 0) { controller.start() }
    }

    /**
     * 권한 안내 다이얼로그에서 확인을 누르면 다이얼로그를 닫고 권한 설정 이동 이벤트를 보내는지 검증
     *
     * Given: 권한 없이 켜서 권한 안내 다이얼로그 표시
     * When: openOverlaySettings 호출
     * Then: 다이얼로그 숨김 + EventOpenOverlaySettings 수신
     */
    @Test
    fun test_06_openOverlaySettingsDismissesDialogAndSendsEvent() = runTest {
        /** Given **/
        every { controller.canDrawOverlays() } returns false
        val viewModel = createViewModel()
        viewModel.setRunning(true)

        /** When **/
        viewModel.openOverlaySettings()
        val receivedEvents = viewModel.events.collectValues(count = 1)

        /** Then **/
        assertFalse(viewModel.uiState.value.showOverlayPermissionDialog)
        assertEquals(FloatingSettingViewModel.EventOpenOverlaySettings, receivedEvents[0])
    }

    /**
     * 권한 안내 다이얼로그를 취소하면 설정 이동 없이 닫히는지 검증
     *
     * Given: 권한 안내 다이얼로그 표시
     * When: dismissOverlayPermissionDialog 호출
     * Then: 다이얼로그 숨김
     */
    @Test
    fun test_07_dismissOverlayPermissionDialog() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        viewModel.showOverlayPermissionDialog()

        /** When **/
        viewModel.dismissOverlayPermissionDialog()

        /** Then **/
        assertFalse(viewModel.uiState.value.showOverlayPermissionDialog)
    }

    /**
     * 접근성 서비스 상태 행을 누르면 접근성 설정 이동 이벤트를 보내는지 검증
     *
     * Given: 접근성 서비스 꺼짐
     * When: openAccessibilitySettings 호출
     * Then: EventOpenAccessibilitySettings 수신
     */
    @Test
    fun test_08_openAccessibilitySettingsSendsEvent() = runTest {
        /** Given **/
        every { controller.isAccessibilityServiceEnabled() } returns false
        val viewModel = createViewModel()

        /** When **/
        viewModel.openAccessibilitySettings()
        val receivedEvents = viewModel.events.collectValues(count = 1)

        /** Then **/
        assertEquals(FloatingSettingViewModel.EventOpenAccessibilitySettings, receivedEvents[0])
    }

    /**
     * 설정 앱에서 권한·접근성 서비스를 켜고 돌아오면 refreshStatus가 상태를 다시 조회하는지 검증
     *
     * Given: 권한 없음, 접근성 서비스 꺼짐으로 ViewModel 생성
     * When: 권한·접근성 서비스 허용 후 refreshStatus 호출
     * Then: overlayPermissionGranted·accessibilityServiceEnabled true
     */
    @Test
    fun test_09_refreshStatusReflectsChanges() = runTest {
        /** Given **/
        every { controller.canDrawOverlays() } returns false
        every { controller.isAccessibilityServiceEnabled() } returns false
        val viewModel = createViewModel()

        /** When **/
        every { controller.canDrawOverlays() } returns true
        every { controller.isAccessibilityServiceEnabled() } returns true
        viewModel.refreshStatus()

        /** Then **/
        assertTrue(viewModel.uiState.value.overlayPermissionGranted)
        assertTrue(viewModel.uiState.value.accessibilityServiceEnabled)
    }
}
