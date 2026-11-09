package com.black.app.ui.maintab.main.appinstallnotification

import android.graphics.drawable.Drawable
import com.black.app.service.ForegroundServiceController
import com.black.app.service.ForegroundServiceController.StartResult
import com.black.app.ui.common.selectapp.InstalledAppResolver
import com.black.app.ui.common.selectapp.SelectedAppUiItem
import com.black.core.viewmodel.ViewModelEvent
import com.black.app.R
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import java.io.IOException
import org.junit.runners.MethodSorters

/** [AppInstallNotificationViewModel]의 상태 로드, 실행 설정 변경·상태 갱신·테스트 알림 동작과 이벤트 발송 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppInstallNotificationViewModelTest : BaseTest() {

    @MockK
    lateinit var foregroundServiceController: ForegroundServiceController

    @MockK
    lateinit var notificationStatus: AppInstallNotificationStatus

    @MockK
    lateinit var installedAppResolver: InstalledAppResolver

    private val activated = MutableStateFlow(false)
    private val serviceRunning = MutableStateFlow(false)

    @Before
    fun setupViewModel() {
        activated.value = false
        serviceRunning.value = false
        every { foregroundServiceController.activated } returns activated
        every { foregroundServiceController.running } returns serviceRunning
        every { foregroundServiceController.isBatteryOptimizationIgnored() } returns true
        // 저장 결과가 실행 설정 Flow로 돌아오는 실제 흐름을 따라 activate·deactivate가 Flow 값을 바꿈
        coEvery { foregroundServiceController.activate() } answers {
            activated.value = true
            StartResult.Started
        }
        coEvery { foregroundServiceController.deactivate() } answers {
            activated.value = false
            Result.success(Unit)
        }
        coEvery { foregroundServiceController.startIfActivated() } returns StartResult.Started
        every { notificationStatus.previewPackageName } returns "com.black.app"
        every { notificationStatus.notifierPackageName } returns "com.black.notifier"
        every { notificationStatus.canShowNotification() } returns true
        every { notificationStatus.showPreviewNotification(any()) } just Runs
        every { installedAppResolver.resolve(any()) } answers {
            SelectedAppUiItem(firstArg(), "JustApp", mockk<Drawable>())
        }
    }

    /** ViewModel 생성 후 init의 실행 설정·실행 상태 수집 시작까지 대기 */
    private fun createViewModel() = AppInstallNotificationViewModel(foregroundServiceController, notificationStatus, installedAppResolver)
        .also { waitCoroutine() }

    /** 동작 전에 이벤트 구독을 시작해 발송된 이벤트 전부(없음 포함)를 수집 */
    private fun TestScope.collectEvents(viewModel: AppInstallNotificationViewModel): List<ViewModelEvent> {
        val receivedEvents = mutableListOf<ViewModelEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(receivedEvents)
        }
        return receivedEvents
    }

    /**
     * 생성 시 실행 설정·시스템 상태·실행 상태·예시 앱이 UiState로 로드되는지 검증
     *
     * Given: 실행 설정 켜짐, 배터리 예외 아님, 알림 표시 불가, 서비스 실행 중
     * When: ViewModel 생성
     * Then: 각 상태가 UiState에 반영, 예시 앱은 previewPackageName, 배지 앱은 notifierPackageName으로 조회
     */
    @Test
    fun test_01_initLoadsState() = runTest {
        /** Given **/
        activated.value = true
        every { foregroundServiceController.isBatteryOptimizationIgnored() } returns false
        every { notificationStatus.canShowNotification() } returns false
        serviceRunning.value = true

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        val uiState = viewModel.uiState.value
        assertTrue(uiState.enabled)
        assertFalse(uiState.batteryOptimizationIgnored)
        assertFalse(uiState.notificationsEnabled)
        assertTrue(uiState.serviceRunning)
        assertEquals("com.black.app", uiState.previewApp?.packageName)
        assertEquals("com.black.notifier", uiState.notifierApp?.packageName)
        verify {
            installedAppResolver.resolve("com.black.app")
            installedAppResolver.resolve("com.black.notifier")
        }
    }

    /**
     * 서비스 실행 상태 변화가 UiState에 바로 반영되는지 검증
     *
     * Given: 서비스 멈춤 상태로 ViewModel 생성
     * When: 실행 상태 true, 이어서 false로 변경
     * Then: serviceRunning이 true, false 순서로 갱신
     */
    @Test
    fun test_02_serviceRunningChangeUpdatesUiState() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        serviceRunning.value = true
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.serviceRunning)

        /** When **/
        serviceRunning.value = false
        waitCoroutine()

        /** Then **/
        assertFalse(viewModel.uiState.value.serviceRunning)
    }

    /**
     * 다른 화면에서 바꾼 실행 설정이 스위치 상태에 반영되는지 검증
     *
     * Given: 실행 설정 꺼짐 상태로 ViewModel 생성
     * When: 실행 설정 Flow 값 true로 변경
     * Then: enabled true
     */
    @Test
    fun test_03_activatedChangeUpdatesEnabled() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        activated.value = true
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.enabled)
    }

    /**
     * 켜고 서비스가 시작되고 알림도 표시 가능하면 설정만 반영하고 안내하지 않는지 검증
     *
     * Given: activate가 Started 반환, 알림 표시 가능
     * When: setEnabled(true) 호출
     * Then: activate 호출, enabled true, 이벤트 없음
     */
    @Test
    fun test_04_setEnabledTrueStartsWithoutEvent() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.setEnabled(true)
        waitCoroutine()

        /** Then **/
        coVerify { foregroundServiceController.activate() }
        assertTrue(viewModel.uiState.value.enabled)
        assertEquals(emptyList<ViewModelEvent>(), receivedEvents)
    }

    /**
     * 켰는데 배터리 최적화 예외가 없으면 스위치는 켜 두고 배터리 최적화 설정으로만 안내하는지 검증
     *
     * Given: activate가 실행 설정을 켜고 BatteryOptimizationRequired 반환
     * When: setEnabled(true) 호출
     * Then: enabled true, 이벤트는 EventOpenBatteryOptimizationSettings 하나
     */
    @Test
    fun test_05_setEnabledTrueOpensBatterySettingsWhenRequired() = runTest {
        /** Given **/
        coEvery { foregroundServiceController.activate() } answers {
            activated.value = true
            StartResult.BatteryOptimizationRequired
        }
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.setEnabled(true)
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.enabled)
        assertEquals(listOf(AppInstallNotificationViewModel.EventOpenBatteryOptimizationSettings), receivedEvents)
    }

    /**
     * 서비스는 시작됐지만 알림을 표시할 수 없으면 켤 때 알림 설정으로만 안내하는지 검증
     *
     * Given: activate가 Started 반환, 알림 표시 불가
     * When: setEnabled(true) 호출
     * Then: enabled true, 이벤트는 EventOpenNotificationSettings 하나
     */
    @Test
    fun test_06_setEnabledTrueOpensNotificationSettingsWhenCannotShow() = runTest {
        /** Given **/
        every { notificationStatus.canShowNotification() } returns false
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.setEnabled(true)
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.enabled)
        assertEquals(listOf(AppInstallNotificationViewModel.EventOpenNotificationSettings), receivedEvents)
    }

    /**
     * 실행 설정 저장에 실패하면 스위치가 저장값을 따라 꺼진 채 남고 안내하지 않는지 검증
     *
     * Given: activate가 실행 설정을 바꾸지 못하고 NotActivated 반환
     * When: setEnabled(true) 호출
     * Then: enabled false, 이벤트 없음
     */
    @Test
    fun test_07_setEnabledTrueKeepsDisabledWhenNotActivated() = runTest {
        /** Given **/
        coEvery { foregroundServiceController.activate() } returns StartResult.NotActivated
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.setEnabled(true)
        waitCoroutine()

        /** Then **/
        assertFalse(viewModel.uiState.value.enabled)
        assertEquals(emptyList<ViewModelEvent>(), receivedEvents)
    }

    /**
     * 끄면 실행 설정 저장·서비스 중지를 요청하는지 검증
     *
     * Given: 실행 설정 켜짐 상태로 ViewModel 생성
     * When: setEnabled(false) 호출
     * Then: deactivate 호출, activate 미호출, enabled false, 이벤트 없음
     */
    @Test
    fun test_08_setEnabledFalseDeactivates() = runTest {
        /** Given **/
        activated.value = true
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.setEnabled(false)
        waitCoroutine()

        /** Then **/
        coVerify { foregroundServiceController.deactivate() }
        coVerify(exactly = 0) { foregroundServiceController.activate() }
        assertFalse(viewModel.uiState.value.enabled)
        assertEquals(emptyList<ViewModelEvent>(), receivedEvents)
    }

    /**
     * refreshStatus가 시스템 상태를 다시 읽고 실행 설정 확인을 컨트롤러에 맡겨 시작을 요청하는지 검증
     *
     * Given: 배터리 예외 아님·알림 표시 불가 상태로 생성 후 둘 다 허용으로 변경
     * When: refreshStatus 호출
     * Then: batteryOptimizationIgnored·notificationsEnabled true로 갱신, startIfActivated 호출, 이벤트 없음
     */
    @Test
    fun test_09_refreshStatusRefreshesAndRequestsStart() = runTest {
        /** Given **/
        every { foregroundServiceController.isBatteryOptimizationIgnored() } returns false
        every { notificationStatus.canShowNotification() } returns false
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)
        every { foregroundServiceController.isBatteryOptimizationIgnored() } returns true
        every { notificationStatus.canShowNotification() } returns true

        /** When **/
        viewModel.refreshStatus()
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.batteryOptimizationIgnored)
        assertTrue(viewModel.uiState.value.notificationsEnabled)
        coVerify { foregroundServiceController.startIfActivated() }
        assertEquals(emptyList<ViewModelEvent>(), receivedEvents)
    }

    /**
     * 알림을 표시할 수 있으면 테스트 알림 보내기가 실제 알림만 요청하는지 검증
     *
     * Given: 알림 표시 가능
     * When: sendTestNotification(true) 호출
     * Then: showPreviewNotification(true) 호출, 이벤트 없음
     */
    @Test
    fun test_10_sendTestNotificationShowsPreviewNotification() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.sendTestNotification(true)
        waitCoroutine()

        /** Then **/
        verify { notificationStatus.showPreviewNotification(true) }
        assertEquals(emptyList<ViewModelEvent>(), receivedEvents)
    }

    /**
     * 알림을 표시할 수 없으면 테스트 알림 보내기가 알림 없이 알림 설정으로 안내하는지 검증
     *
     * Given: 알림 표시 불가
     * When: sendTestNotification(false) 호출
     * Then: showPreviewNotification 미호출, 이벤트는 EventOpenNotificationSettings 하나
     */
    @Test
    fun test_11_sendTestNotificationOpensSettingsWhenCannotShow() = runTest {
        /** Given **/
        every { notificationStatus.canShowNotification() } returns false
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.sendTestNotification(false)
        waitCoroutine()

        /** Then **/
        verify(exactly = 0) { notificationStatus.showPreviewNotification(any()) }
        assertEquals(listOf(AppInstallNotificationViewModel.EventOpenNotificationSettings), receivedEvents)
    }

    /**
     * 설정 화면 열기 함수가 각 설정 화면 이동 이벤트를 보내는지 검증
     *
     * Given: ViewModel 생성
     * When: openBatteryOptimizationSettings, openNotificationSettings 순서로 호출
     * Then: EventOpenBatteryOptimizationSettings, EventOpenNotificationSettings 순서로 수신
     */
    @Test
    fun test_12_openSettingsSendsSettingsEvents() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.openBatteryOptimizationSettings()
        viewModel.openNotificationSettings()
        waitCoroutine()

        /** Then **/
        assertEquals(
            listOf(
                AppInstallNotificationViewModel.EventOpenBatteryOptimizationSettings,
                AppInstallNotificationViewModel.EventOpenNotificationSettings,
            ),
            receivedEvents,
        )
    }

    /**
     * 설정 저장에 실패하면 끄기와 켜기 모두 실패 토스트를 보내는지 검증
     *
     * Given: deactivate는 실패 결과, activate는 SaveFailed를 돌려주는 컨트롤러
     * When: setEnabled(false), setEnabled(true) 호출
     * Then: 저장 실패 토스트 2개 수신
     */
    @Test
    fun test_13_saveFailureShowsToast() = runTest {
        /** Given **/
        coEvery { foregroundServiceController.deactivate() } returns Result.failure(IOException("disk full"))
        coEvery { foregroundServiceController.activate() } returns StartResult.SaveFailed
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.setEnabled(false)
        viewModel.setEnabled(true)
        waitCoroutine()

        /** Then **/
        val failedToast = AppInstallNotificationViewModel.EventShowToast(R.string.app_install_notification_save_failed)
        assertEquals(listOf(failedToast, failedToast), receivedEvents)
    }
}
