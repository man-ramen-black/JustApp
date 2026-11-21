package com.black.feature.usagetimer.ui

import com.black.core.model.OverlayPosition
import com.black.core.model.OverlayPosition.HorizontalAnchor
import com.black.core.model.OverlayPosition.VerticalAnchor
import com.black.core.system.AppUsageSession
import com.black.core.system.AppUsageTimeRepository
import com.black.core.system.RecentAppUsage
import com.black.core.ui.component.OverlayPositionInput
import com.black.core.util.AccessibilityServiceStatus
import com.black.core.util.InstalledAppResolver
import com.black.core.util.OverlayPermissionStatus
import com.black.core.util.UsageAccessStatus
import com.black.feature.usagetimer.R
import com.black.feature.usagetimer.data.UsageTimerRepository
import com.black.feature.usagetimer.model.UsageTimerDefaults
import com.black.test.BaseTest
import com.black.test.collectValues
import com.black.test.mockInstalledApps
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.IOException
import kotlin.time.Duration.Companion.hours

/** [UsageTimerViewModel]의 상태 로드·저장·권한 안내·이벤트 발송 동작 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerViewModelTest : BaseTest() {

    @MockK
    lateinit var repository: UsageTimerRepository

    @MockK
    lateinit var installedAppResolver: InstalledAppResolver

    @MockK
    lateinit var overlayPermissionStatus: OverlayPermissionStatus

    @MockK
    lateinit var accessibilityServiceStatus: AccessibilityServiceStatus

    @MockK
    lateinit var usageAccessStatus: UsageAccessStatus

    @MockK
    lateinit var appUsageTimeRepository: AppUsageTimeRepository

    /** 저장소가 기본으로 돌려주는 최근 사용 구간(com.a는 1분 구간 하나, com.b는 사용 없음) */
    private val sampleUsage = RecentAppUsage(
        queriedAtMillis = 1_000_000L,
        sessions = mapOf("com.a" to listOf(AppUsageSession(0L, 60_000L)), "com.b" to emptyList()),
    )

    @Before
    fun setupViewModel() {
        coEvery { repository.getPauseDuration() } returns 3
        coEvery { repository.getPauseRemainTimeMillis() } returns 0L
        coEvery { repository.getSelectedApps() } returns listOf("com.a", "com.b")
        every { repository.getPositionFlow() } returns flowOf(UsageTimerDefaults.POSITION)
        installedAppResolver.mockInstalledApps()
        every { overlayPermissionStatus.canDrawOverlays() } returns true
        every { accessibilityServiceStatus.isAccessibilityServiceEnabled() } returns false
        every { usageAccessStatus.isUsageAccessGranted() } returns true
        coEvery { appUsageTimeRepository.getRecentUsageSessions(any(), any(), any()) } returns sampleUsage
    }

    /** ViewModel 생성 후 init의 비동기 초기 로드 완료까지 대기 */
    private fun createViewModel() = UsageTimerViewModel(
        repository,
        installedAppResolver,
        overlayPermissionStatus,
        accessibilityServiceStatus,
        usageAccessStatus,
        appUsageTimeRepository,
    ).also { waitCoroutine() }

    /**
     * 생성 시 저장된 일시정지 시간·선택 앱이 UiState로 로드되는지 검증
     *
     * Given: repository가 pauseDuration 3, 선택 앱 [com.a, com.b] 반환
     * When: ViewModel 생성(초기 로드 완료 대기)
     * Then: pauseStateLoaded true, uiState.pauseDurationInput "3", selectedApps 패키지명 [com.a, com.b]
     */
    @Test
    fun test_01_initLoadsSavedState() = runTest {
        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        assertTrue(viewModel.uiState.value.pauseStateLoaded)
        assertEquals("3", viewModel.uiState.value.pauseDurationInput)
        assertEquals(
            listOf("com.a", "com.b"),
            viewModel.uiState.value.selectedApps.map { it.packageName },
        )
    }

    /**
     * 앱 선택 결과를 저장하고 UiState를 갱신하는지 검증
     *
     * Given: repository.saveSelectedApps 모킹
     * When: onAppsSelected([com.x]) 호출 후 코루틴 완료 대기
     * Then: repository.saveSelectedApps 호출 + selectedApps가 [com.x]로 갱신
     */
    @Test
    fun test_02_onAppsSelectedSavesAndUpdates() = runTest {
        /** Given **/
        coEvery { repository.saveSelectedApps(any()) } returns Result.success(Unit)
        val viewModel = createViewModel()

        /** When **/
        viewModel.onAppsSelected(listOf("com.x"))
        waitCoroutine()

        /** Then **/
        coVerify { repository.saveSelectedApps(listOf("com.x")) }
        assertEquals(
            listOf("com.x"),
            viewModel.uiState.value.selectedApps.map { it.packageName },
        )
    }

    /**
     * Pause 클릭 시 저장·일시정지 후 DetachTimerView·ShowToast 이벤트가 발송되는지 검증
     *
     * Given: pauseDurationInput "5" 입력, repository 저장·일시정지 모킹
     * When: onClickPause 호출
     * Then: repository.savePauseDuration(5)·pause(5) 호출 + DetachTimerView, 5분 일시정지 문구 ShowToast 이벤트 수신
     */
    @Test
    fun test_03_onClickPauseSavesAndSendsEvents() = runTest {
        /** Given **/
        coEvery { repository.savePauseDuration(any()) } returns Result.success(Unit)
        coEvery { repository.pause(any()) } returns Result.success(Unit)
        val viewModel = createViewModel()
        viewModel.updatePauseDuration("5")

        /** When **/
        viewModel.onClickPause()
        val receivedEvents = viewModel.events.collectValues(count = 2)

        /** Then **/
        coVerify { repository.savePauseDuration(5) }
        coVerify { repository.pause(5) }
        assertEquals(UsageTimerViewModel.EventDetachTimerView, receivedEvents[0])
        assertEquals(UsageTimerViewModel.EventShowToast(R.string.usage_timer_paused, listOf(5)), receivedEvents[1])
    }

    /**
     * Select app 클릭 시 현재 선택 앱 목록을 담은 OpenSelectApp 이벤트가 발송되는지 검증
     *
     * Given: 선택 앱 [com.a, com.b] 로드 상태
     * When: onClickSelectApp 호출
     * Then: OpenSelectApp(checkedPackageNames=[com.a, com.b]) 이벤트 수신
     */
    @Test
    fun test_04_onClickSelectAppSendsCheckedPackages() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.onClickSelectApp()
        val receivedEvents = viewModel.events.collectValues(count = 1)

        /** Then **/
        assertEquals(
            UsageTimerViewModel.EventOpenSelectApp(listOf("com.a", "com.b")),
            receivedEvents[0],
        )
    }

    /**
     * 숫자가 아니거나 1분 미만인 입력은 저장·일시정지하지 않고 안내 토스트를 보내는지 검증
     *
     * Given: ViewModel 생성
     * When: 입력 "abc"·"0"마다 onClickPause 호출 후 코루틴 완료 대기
     * Then: savePauseDuration·pause 미호출, 안내 토스트 2개 수신
     */
    @Test
    fun test_05_invalidDurationInputIsRejected() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.updatePauseDuration("abc")
        viewModel.onClickPause()
        viewModel.updatePauseDuration("0")
        viewModel.onClickPause()
        waitCoroutine()
        val receivedEvents = viewModel.events.collectValues(count = 2)

        /** Then **/
        coVerify(exactly = 0) {
            repository.savePauseDuration(any())
            repository.pause(any())
        }
        val invalidToast = UsageTimerViewModel.EventShowToast(R.string.usage_timer_pause_duration_invalid)
        assertEquals(listOf(invalidToast, invalidToast), receivedEvents)
    }

    /**
     * Pause 클릭 후 잔여 시간이 양수로 갱신되는지 검증
     *
     * Given: pause 후 getPauseRemainTimeMillis가 5분 반환
     * When: onClickPause 호출 후 코루틴 완료 대기
     * Then: uiState.pauseRemainTimeMillis 5분
     */
    @Test
    fun test_06_onClickPauseUpdatesRemainTime() = runTest {
        /** Given **/
        coEvery { repository.savePauseDuration(any()) } returns Result.success(Unit)
        coEvery { repository.pause(any()) } returns Result.success(Unit)
        val viewModel = createViewModel()
        coEvery { repository.getPauseRemainTimeMillis() } returns 5 * 60 * 1000L

        /** When **/
        viewModel.onClickPause()
        waitCoroutine()

        /** Then **/
        assertEquals(5 * 60 * 1000L, viewModel.uiState.value.pauseRemainTimeMillis)
    }

    /**
     * 일시정지 시각을 저장하지 못하면 타이머를 떼지 않고 실패 토스트만 보내는지 검증
     *
     * Given: pause가 실패 결과를 돌려주는 저장소, 입력 "5"
     * When: onClickPause 호출 뒤 구분용으로 onClickAccessibility 호출
     * Then: 저장 실패 토스트 다음에 바로 접근성 설정 이벤트 수신(EventDetachTimerView 없음)
     */
    @Test
    fun test_07_pauseSaveFailureShowsToastOnly() = runTest {
        /** Given **/
        coEvery { repository.savePauseDuration(any()) } returns Result.success(Unit)
        coEvery { repository.pause(any()) } returns Result.failure(IOException("disk full"))
        val viewModel = createViewModel()
        viewModel.updatePauseDuration("5")

        /** When **/
        viewModel.onClickPause()
        waitCoroutine()
        viewModel.onClickAccessibility()
        val receivedEvents = viewModel.events.collectValues(count = 2)

        /** Then **/
        assertEquals(
            listOf(
                UsageTimerViewModel.EventShowToast(R.string.usage_timer_save_failed),
                UsageTimerViewModel.EventOpenAccessibilitySettings,
            ),
            receivedEvents,
        )
    }

    /**
     * 생성 시 남은 일시정지 시간이 UiState로 로드되는지 검증
     *
     * Given: getPauseRemainTimeMillis가 1분 반환
     * When: ViewModel 생성(초기 로드 완료 대기)
     * Then: uiState.pauseRemainTimeMillis 1분
     */
    @Test
    fun test_08_initLoadsPauseRemainTime() = runTest {
        /** Given **/
        coEvery { repository.getPauseRemainTimeMillis() } returns 60_000L

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        assertEquals(60_000L, viewModel.uiState.value.pauseRemainTimeMillis)
    }

    /**
     * 다른 앱 위에 표시 권한이 있으면 타이머 표시 클릭 시 안내 없이 ShowTimerView 이벤트가 발송되는지 검증
     *
     * Given: canDrawOverlays true
     * When: onClickShow 호출
     * Then: ShowTimerView 이벤트 수신, 권한 안내 다이얼로그 숨김
     */
    @Test
    fun test_09_showWithPermissionSendsShowTimerView() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.onClickShow()
        val receivedEvents = viewModel.events.collectValues(count = 1)

        /** Then **/
        assertEquals(UsageTimerViewModel.EventShowTimerView, receivedEvents[0])
        assertFalse(viewModel.uiState.value.showOverlayPermissionDialog)
    }

    /**
     * 다른 앱 위에 표시 권한이 없으면 타이머 표시 클릭 시 타이머 대신 권한 안내 다이얼로그를 표시하는지 검증
     *
     * Given: canDrawOverlays false
     * When: onClickShow 호출 뒤 구분용으로 onClickAccessibility 호출
     * Then: 권한 안내 다이얼로그 표시, 첫 이벤트가 접근성 설정 이벤트(ShowTimerView 없음)
     */
    @Test
    fun test_10_showWithoutPermissionShowsPermissionDialog() = runTest {
        /** Given **/
        every { overlayPermissionStatus.canDrawOverlays() } returns false
        val viewModel = createViewModel()

        /** When **/
        viewModel.onClickShow()
        viewModel.onClickAccessibility()
        val receivedEvents = viewModel.events.collectValues(count = 1)

        /** Then **/
        assertTrue(viewModel.uiState.value.showOverlayPermissionDialog)
        assertEquals(UsageTimerViewModel.EventOpenAccessibilitySettings, receivedEvents[0])
    }

    /**
     * 권한 안내 다이얼로그에서 설정으로 이동하면 다이얼로그를 닫고 권한 설정 이동 이벤트를 보내는지 검증
     *
     * Given: 권한 안내 다이얼로그 표시 상태
     * When: openOverlaySettings 호출
     * Then: 다이얼로그 숨김, OpenOverlaySettings 이벤트 수신
     */
    @Test
    fun test_11_openOverlaySettingsDismissesDialogAndSendsEvent() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        viewModel.showOverlayPermissionDialog()

        /** When **/
        viewModel.openOverlaySettings()
        val receivedEvents = viewModel.events.collectValues(count = 1)

        /** Then **/
        assertFalse(viewModel.uiState.value.showOverlayPermissionDialog)
        assertEquals(UsageTimerViewModel.EventOpenOverlaySettings, receivedEvents[0])
    }

    /**
     * 권한 안내 다이얼로그를 취소하면 이동 없이 다이얼로그만 닫는지 검증
     *
     * Given: 권한 안내 다이얼로그 표시 상태
     * When: dismissOverlayPermissionDialog 호출 뒤 구분용으로 onClickAccessibility 호출
     * Then: 다이얼로그 숨김, 첫 이벤트가 접근성 설정 이벤트(OpenOverlaySettings 없음)
     */
    @Test
    fun test_12_dismissOverlayPermissionDialog() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        viewModel.showOverlayPermissionDialog()

        /** When **/
        viewModel.dismissOverlayPermissionDialog()
        viewModel.onClickAccessibility()
        val receivedEvents = viewModel.events.collectValues(count = 1)

        /** Then **/
        assertFalse(viewModel.uiState.value.showOverlayPermissionDialog)
        assertEquals(UsageTimerViewModel.EventOpenAccessibilitySettings, receivedEvents[0])
    }

    /**
     * 권한 상태 갱신이 접근성 서비스 켜짐과 다른 앱 위에 표시 허용 여부를 UiState에 반영하는지 검증
     *
     * Given: 접근성 서비스 켜짐, 다른 앱 위에 표시 거부
     * When: refreshStatus 호출
     * Then: accessibilityServiceEnabled true, overlayPermissionGranted false
     * 반대 조합(접근성 꺼짐, 표시 허용)도 같은 방식으로 반영
     */
    @Test
    fun test_13_refreshStatusReflectsPermissionStatus() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        every { accessibilityServiceStatus.isAccessibilityServiceEnabled() } returns true
        every { overlayPermissionStatus.canDrawOverlays() } returns false

        /** When **/
        viewModel.refreshStatus()

        /** Then **/
        assertTrue(viewModel.uiState.value.accessibilityServiceEnabled)
        assertFalse(viewModel.uiState.value.overlayPermissionGranted)

        /** When **/
        every { accessibilityServiceStatus.isAccessibilityServiceEnabled() } returns false
        every { overlayPermissionStatus.canDrawOverlays() } returns true
        viewModel.refreshStatus()

        /** Then **/
        assertFalse(viewModel.uiState.value.accessibilityServiceEnabled)
        assertTrue(viewModel.uiState.value.overlayPermissionGranted)
    }

    /**
     * 생성 직후 UiState가 현재 권한 상태를 반영하는지 검증
     *
     * Given: 접근성 서비스 켜짐, 다른 앱 위에 표시 거부
     * When: ViewModel 생성(초기 로드 완료 대기)
     * Then: refreshStatus 호출 없이 accessibilityServiceEnabled true, overlayPermissionGranted false
     */
    @Test
    fun test_14_initReflectsPermissionStatus() = runTest {
        /** Given **/
        every { accessibilityServiceStatus.isAccessibilityServiceEnabled() } returns true
        every { overlayPermissionStatus.canDrawOverlays() } returns false

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        assertTrue(viewModel.uiState.value.accessibilityServiceEnabled)
        assertFalse(viewModel.uiState.value.overlayPermissionGranted)
    }

    /**
     * 생성 시 저장된 표시 위치가 UiState 입력값으로 로드되는지 검증
     *
     * Given: 저장된 위치가 오른쪽 위
     * When: ViewModel 생성(초기 로드 완료 대기)
     * Then: uiState.positionInput이 그 위치의 입력값
     */
    @Test
    fun test_15_initLoadsSavedPosition() = runTest {
        /** Given **/
        val position = OverlayPosition(HorizontalAnchor.RIGHT, VerticalAnchor.TOP)
        every { repository.getPositionFlow() } returns flowOf(position)

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        assertEquals(OverlayPositionInput.from(position), viewModel.uiState.value.positionInput)
    }

    /**
     * 표시 위치 변경이 UiState에 반영되고 저장되는지 검증
     *
     * Given: 위치 저장 성공 모킹
     * When: 오른쪽 아래 입력으로 updatePosition 호출 후 코루틴 완료 대기
     * Then: uiState.positionInput이 그 입력, savePosition이 입력을 변환한 위치로 호출
     */
    @Test
    fun test_16_updatePositionSavesAndUpdatesState() = runTest {
        /** Given **/
        coEvery { repository.savePosition(any()) } returns Result.success(Unit)
        val viewModel = createViewModel()
        val input = OverlayPositionInput.from(OverlayPosition(HorizontalAnchor.RIGHT, VerticalAnchor.BOTTOM, marginBottomDp = 30))

        /** When **/
        viewModel.updatePosition(input)
        waitCoroutine()

        /** Then **/
        assertEquals(input, viewModel.uiState.value.positionInput)
        coVerify { repository.savePosition(input.toPosition()) }
    }

    /**
     * 표시 위치 저장에 실패하면 실패 토스트를 보내는지 검증
     *
     * Given: savePosition이 실패 결과를 돌려주는 저장소
     * When: updatePosition 호출
     * Then: 저장 실패 토스트 수신
     */
    @Test
    fun test_17_updatePositionFailureShowsToast() = runTest {
        /** Given **/
        coEvery { repository.savePosition(any()) } returns Result.failure(IOException("disk full"))
        val viewModel = createViewModel()

        /** When **/
        viewModel.updatePosition(OverlayPositionInput.from(UsageTimerDefaults.POSITION))
        val receivedEvents = viewModel.events.collectValues(count = 1)

        /** Then **/
        assertEquals(UsageTimerViewModel.EventShowToast(R.string.usage_timer_save_failed), receivedEvents[0])
    }

    /**
     * 생성 시 선택 앱의 최근 24시간 사용 구간과 사용 정보 접근 권한이 UiState로 로드되는지 검증
     *
     * Given: 선택 앱 [com.a, com.b], 권한 허용, 저장소가 앱별 사용 구간 반환
     * When: ViewModel 생성(초기 로드 완료 대기)
     * Then: recentUsage가 그 결과, 조회 불가 아님, usageAccessGranted true, 24시간·10초 간격으로 조회
     */
    @Test
    fun test_18_initLoadsRecentUsage() = runTest {
        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        val state = viewModel.uiState.value
        assertEquals(sampleUsage, state.recentUsage)
        assertFalse(state.recentUsageUnavailable)
        assertTrue(state.usageAccessGranted)
        coVerify { appUsageTimeRepository.getRecentUsageSessions(listOf("com.a", "com.b"), 24.hours, 10_000L) }
    }

    /**
     * 저장소가 사용 구간을 돌려주지 못하면 조회 불가 상태가 되는지 검증
     *
     * Given: getRecentUsageSessions가 null 반환
     * When: ViewModel 생성
     * Then: recentUsage null, recentUsageUnavailable true
     */
    @Test
    fun test_19_recentUsageNullWhenUnavailable() = runTest {
        /** Given **/
        coEvery { appUsageTimeRepository.getRecentUsageSessions(any(), any(), any()) } returns null

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        val state = viewModel.uiState.value
        assertNull(state.recentUsage)
        assertTrue(state.recentUsageUnavailable)
    }

    /**
     * 상태 갱신이 사용 정보 접근 권한과 사용 구간을 다시 반영하는지 검증
     *
     * Given: 생성 뒤 권한 거부, 저장소가 com.a 빈 구간 목록 반환으로 변경
     * When: refreshStatus 호출 후 코루틴 완료 대기
     * Then: usageAccessGranted false, recentUsage가 바뀐 결과
     */
    @Test
    fun test_20_refreshStatusReloadsUsageAndPermission() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val reloadedUsage = RecentAppUsage(2L, mapOf("com.a" to emptyList()))
        every { usageAccessStatus.isUsageAccessGranted() } returns false
        coEvery { appUsageTimeRepository.getRecentUsageSessions(any(), any(), any()) } returns reloadedUsage

        /** When **/
        viewModel.refreshStatus()
        waitCoroutine()

        /** Then **/
        assertFalse(viewModel.uiState.value.usageAccessGranted)
        assertEquals(reloadedUsage, viewModel.uiState.value.recentUsage)
    }

    /**
     * 앱 선택을 저장하면 새 선택 앱 기준으로 사용 구간을 다시 조회하는지 검증
     *
     * Given: 저장 성공, 저장 뒤 getSelectedApps가 [com.c] 반환
     * When: onAppsSelected([com.c]) 호출 후 코루틴 완료 대기
     * Then: [com.c]로 getRecentUsageSessions 호출
     */
    @Test
    fun test_21_appsSelectedReloadsUsage() = runTest {
        /** Given **/
        coEvery { repository.saveSelectedApps(any()) } returns Result.success(Unit)
        val viewModel = createViewModel()
        coEvery { repository.getSelectedApps() } returns listOf("com.c")

        /** When **/
        viewModel.onAppsSelected(listOf("com.c"))
        waitCoroutine()

        /** Then **/
        coVerify { appUsageTimeRepository.getRecentUsageSessions(listOf("com.c"), any(), any()) }
    }

    /**
     * 사용 구간 조회가 예외로 실패해도 조회 불가로만 두고 다른 상태는 정상 로드하는지 검증
     *
     * Given: getRecentUsageSessions가 RuntimeException을 던짐
     * When: ViewModel 생성
     * Then: recentUsage null, recentUsageUnavailable true, 일시정지 시간 입력과 선택 앱은 정상 로드
     */
    @Test
    fun test_22_usageQueryFailureHidesUsage() = runTest {
        /** Given **/
        coEvery { appUsageTimeRepository.getRecentUsageSessions(any(), any(), any()) } throws RuntimeException("query failed")

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        val state = viewModel.uiState.value
        assertNull(state.recentUsage)
        assertTrue(state.recentUsageUnavailable)
        assertEquals("3", state.pauseDurationInput)
        assertEquals(listOf("com.a", "com.b"), state.selectedApps.map { it.packageName })
    }

    /**
     * 사용 정보 접근 설정 열기가 설정 이동 이벤트를 보내는지 검증
     *
     * Given: ViewModel 생성
     * When: openUsageAccessSettings 호출
     * Then: EventOpenUsageAccessSettings 수신
     */
    @Test
    fun test_23_openUsageAccessSettingsSendsEvent() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.openUsageAccessSettings()
        val receivedEvents = viewModel.events.collectValues(count = 1)

        /** Then **/
        assertEquals(UsageTimerViewModel.EventOpenUsageAccessSettings, receivedEvents[0])
    }

    /**
     * 늦게 끝난 옛 사용 구간 조회가 새 조회 결과를 덮어쓰지 않는지 검증
     *
     * Given: 첫 조회는 1초 뒤 조회 시각 1, 둘째 조회는 즉시 조회 시각 2 결과 반환
     * When: ViewModel 생성 직후 첫 조회가 진행 중일 때 refreshStatus 호출 후 모두 끝날 때까지 대기
     * Then: recentUsage가 둘째 조회 결과
     */
    @Test
    fun test_24_staleUsageQueryDoesNotOverwriteNewer() = runTest {
        /** Given **/
        coEvery { appUsageTimeRepository.getRecentUsageSessions(any(), any(), any()) } coAnswers {
            delay(1000)
            RecentAppUsage(1L, emptyMap())
        } andThen RecentAppUsage(2L, emptyMap())
        val viewModel = UsageTimerViewModel(
            repository,
            installedAppResolver,
            overlayPermissionStatus,
            accessibilityServiceStatus,
            usageAccessStatus,
            appUsageTimeRepository,
        )
        advanceCoroutineTime(0)

        /** When **/
        viewModel.refreshStatus()
        waitCoroutine()

        /** Then **/
        assertEquals(2L, viewModel.uiState.value.recentUsage?.queriedAtMillis)
    }

    /**
     * 유효한 일시정지 시간 입력을 바로 저장하는지 검증
     *
     * Given: savePauseDuration 성공
     * When: updatePauseDuration("7")
     * Then: savePauseDuration(7) 호출, 입력 "7", 오류 표시 없음
     */
    @Test
    fun test_25_updatePauseDurationSavesValidInput() = runTest {
        /** Given **/
        coEvery { repository.savePauseDuration(any()) } returns Result.success(Unit)
        val viewModel = createViewModel()

        /** When **/
        viewModel.updatePauseDuration("7")
        waitCoroutine()

        /** Then **/
        coVerify { repository.savePauseDuration(7) }
        assertEquals("7", viewModel.uiState.value.pauseDurationInput)
        assertFalse(viewModel.uiState.value.showsPauseDurationError)
    }

    /**
     * 비었거나 1 미만인 입력은 저장하지 않고 오류를 표시하는지 검증
     *
     * Given: ViewModel 생성(입력 "3", 고친 적 없음)
     * When: updatePauseDuration(""), updatePauseDuration("0")
     * Then: 생성 직후 오류 없음, 고친 뒤 오류 표시, savePauseDuration 미호출
     */
    @Test
    fun test_26_updatePauseDurationRejectsInvalidInput() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val errorBeforeEdit = viewModel.uiState.value.showsPauseDurationError

        /** When **/
        viewModel.updatePauseDuration("")
        viewModel.updatePauseDuration("0")
        waitCoroutine()

        /** Then **/
        assertFalse(errorBeforeEdit)
        assertTrue(viewModel.uiState.value.showsPauseDurationError)
        coVerify(exactly = 0) { repository.savePauseDuration(any()) }
    }

    /**
     * 일시정지 시간 저장에 실패하면 실패 토스트를 보내는지 검증
     *
     * Given: savePauseDuration 실패
     * When: updatePauseDuration("5")
     * Then: 저장 실패 토스트 수신
     */
    @Test
    fun test_27_updatePauseDurationFailureShowsToast() = runTest {
        /** Given **/
        coEvery { repository.savePauseDuration(any()) } returns Result.failure(IOException("disk full"))
        val viewModel = createViewModel()

        /** When **/
        viewModel.updatePauseDuration("5")
        val receivedEvents = viewModel.events.collectValues(count = 1)

        /** Then **/
        assertEquals(UsageTimerViewModel.EventShowToast(R.string.usage_timer_save_failed), receivedEvents[0])
    }

    /**
     * 구간 목록을 볼 앱을 바꾸는지 검증
     *
     * Given: 선택 앱 [com.a, com.b]
     * When: showUsageSessions("com.b")
     * Then: 처음에는 com.a, 바꾼 뒤 com.b
     */
    @Test
    fun test_28_showUsageSessionsChangesViewedApp() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val initialPackageName = viewModel.uiState.value.usageSessionsApp?.packageName

        /** When **/
        viewModel.showUsageSessions("com.b")

        /** Then **/
        assertEquals("com.a", initialPackageName)
        assertEquals("com.b", viewModel.uiState.value.usageSessionsApp?.packageName)
    }

    /**
     * 일시정지 값이 선택 앱 해석을 기다리지 않고 반영되는지 검증
     *
     * Given: getSelectedApps가 1초 뒤에 돌아옴
     * When: ViewModel 생성 직후(시간 진행 없음)
     * Then: pauseStateLoaded true, 입력 "3", 선택 앱은 아직 비어 있음
     */
    @Test
    fun test_29_pauseStateLoadsWithoutWaitingSelectedApps() = runTest {
        /** Given **/
        coEvery { repository.getSelectedApps() } coAnswers {
            delay(1000)
            listOf("com.a")
        }

        /** When **/
        val viewModel = UsageTimerViewModel(
            repository,
            installedAppResolver,
            overlayPermissionStatus,
            accessibilityServiceStatus,
            usageAccessStatus,
            appUsageTimeRepository,
        )
        advanceCoroutineTime(0)

        /** Then **/
        assertTrue(viewModel.uiState.value.pauseStateLoaded)
        assertEquals("3", viewModel.uiState.value.pauseDurationInput)
        assertTrue(viewModel.uiState.value.selectedApps.isEmpty())
    }

    /**
     * 첫 조회가 끝나기 전에는 조회 불가가 아니라 조회 중 상태인지 검증
     *
     * Given: getRecentUsageSessions가 1초 뒤에 돌아옴
     * When: ViewModel 생성 직후(시간 진행 없음)
     * Then: recentUsage null, recentUsageUnavailable false
     */
    @Test
    fun test_30_initialUsageIsLoadingNotUnavailable() = runTest {
        /** Given **/
        coEvery { appUsageTimeRepository.getRecentUsageSessions(any(), any(), any()) } coAnswers {
            delay(1000)
            sampleUsage
        }

        /** When **/
        val viewModel = UsageTimerViewModel(
            repository,
            installedAppResolver,
            overlayPermissionStatus,
            accessibilityServiceStatus,
            usageAccessStatus,
            appUsageTimeRepository,
        )
        advanceCoroutineTime(0)

        /** Then **/
        assertNull(viewModel.uiState.value.recentUsage)
        assertFalse(viewModel.uiState.value.recentUsageUnavailable)
    }

    /**
     * 조회 불가 상태에서 다시 조회하는 동안에는 조회 불가로 표시하지 않는지 검증
     *
     * Given: 조회 불가(null 반환)로 생성된 뒤, 새 조회가 1초 뒤에 돌아오도록 변경
     * When: refreshStatus 호출 직후(시간 진행 없음)
     * Then: recentUsageUnavailable false(조회 중), 1초 뒤 결과 반영 후 recentUsage 표시
     */
    @Test
    fun test_31_refreshStatusClearsUnavailableWhileQuerying() = runTest {
        /** Given **/
        coEvery { appUsageTimeRepository.getRecentUsageSessions(any(), any(), any()) } returns null
        val viewModel = createViewModel()
        val unavailableBefore = viewModel.uiState.value.recentUsageUnavailable
        coEvery { appUsageTimeRepository.getRecentUsageSessions(any(), any(), any()) } coAnswers {
            delay(1000)
            sampleUsage
        }

        /** When **/
        viewModel.refreshStatus()
        advanceCoroutineTime(0)

        /** Then **/
        assertTrue(unavailableBefore)
        assertFalse(viewModel.uiState.value.recentUsageUnavailable)
        assertNull(viewModel.uiState.value.recentUsage)
        advanceCoroutineTime(1000)
        assertEquals(sampleUsage, viewModel.uiState.value.recentUsage)
        assertFalse(viewModel.uiState.value.recentUsageUnavailable)
    }

    /**
     * 선택 앱에 없는 패키지명으로 구간 목록을 열면 첫 앱으로 대체되는지 검증
     *
     * Given: 선택 앱 [com.a, com.b]
     * When: showUsageSessions("com.zzz")
     * Then: usageSessionsApp 패키지명 com.a
     */
    @Test
    fun test_32_showUsageSessionsFallsBackToFirstApp() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.showUsageSessions("com.zzz")

        /** Then **/
        assertEquals("com.a", viewModel.uiState.value.usageSessionsApp?.packageName)
    }
}
