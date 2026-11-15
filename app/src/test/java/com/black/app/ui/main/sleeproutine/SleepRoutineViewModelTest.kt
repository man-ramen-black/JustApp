package com.black.app.ui.main.sleeproutine

import com.black.app.R
import com.black.app.model.SleepRoutineRepository
import com.black.app.sleeproutine.SleepRoutineSettings
import com.black.core.viewmodel.ViewModelEvent
import com.black.test.BaseTest
import com.black.test.collectDuring
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/** [SleepRoutineViewModel]의 설정·상태 로드, 입력 검증·저장, 수면 루틴 시작·종료와 이벤트 발송 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SleepRoutineViewModelTest : BaseTest() {

    @MockK
    lateinit var repository: SleepRoutineRepository

    @MockK
    lateinit var systemStatus: SleepRoutineSystemStatus

    private val running = MutableStateFlow(false)
    private val sessionEndTime = MutableStateFlow<Long?>(null)

    @Before
    fun setupViewModel() {
        running.value = false
        sessionEndTime.value = null
        every { systemStatus.running } returns running
        every { systemStatus.hasDoNotDisturbAccess() } returns true
        every { systemStatus.canDrawOverlays() } returns true
        every { systemStatus.isNotificationListenerEnabled() } returns true
        every { systemStatus.canShowNotification() } returns true
        every { systemStatus.maxVolumeLevel() } returns 15
        every { systemStatus.startRoutine() } just Runs
        every { systemStatus.stopRoutine() } just Runs
        every { repository.sessionEndTime } returns sessionEndTime
        coEvery { repository.getSettings() } returns SleepRoutineSettings()
        coEvery { repository.saveSettings(any()) } returns Result.success(Unit)
    }

    /** ViewModel 생성 후 init의 설정 로드와 Flow 수집 시작까지 대기 */
    private fun createViewModel() = SleepRoutineViewModel(repository, systemStatus)
        .also { waitCoroutine() }

    /** 올바른 입력으로 바꾼 ViewModel 생성 */
    private fun createViewModelWithValidInputs() = createViewModel().apply {
        updateYoutubeUrlInput("  https://youtu.be/abc  ")
        updateDurationInput("90")
        setVolumeEnabled(false)
        updateVolumeLevel(8)
    }

    /**
     * [createViewModelWithValidInputs]의 입력을 저장할 때 기대하는 설정
     *
     * URL은 앞뒤 공백을 뺀 값
     */
    private val validInputSettings = SleepRoutineSettings(
        youtubeUrl = "https://youtu.be/abc",
        durationMinutes = 90,
        volumeEnabled = false,
        volumeLevel = 8,
    )

    /**
     * 생성 시 저장 설정·권한 상태·최대 볼륨이 UiState에 반영되고 최대보다 큰 저장 볼륨은 최대로 맞추는지 검증
     *
     * Given: 저장 설정(URL, 45분, 볼륨 꺼짐, 볼륨 20), 방해 금지·알림 접근 미허용, 다른 앱 위에 표시·알림 표시 허용, 최대 볼륨 15
     * When: ViewModel 생성
     * Then: settingsLoaded true, 입력값·스위치·권한 상태 반영, volumeLevel 15, maxVolumeLevel 15
     */
    @Test
    fun test_01_initLoadsSettingsAndStatus() = runTest {
        /** Given **/
        coEvery { repository.getSettings() } returns SleepRoutineSettings(
            youtubeUrl = "https://www.youtube.com/watch?v=abc",
            durationMinutes = 45,
            volumeEnabled = false,
            volumeLevel = 20,
        )
        every { systemStatus.hasDoNotDisturbAccess() } returns false
        every { systemStatus.isNotificationListenerEnabled() } returns false

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        val uiState = viewModel.uiState.value
        assertTrue(uiState.settingsLoaded)
        assertEquals("https://www.youtube.com/watch?v=abc", uiState.youtubeUrlInput)
        assertEquals("45", uiState.durationInput)
        assertFalse(uiState.volumeEnabled)
        assertEquals(15, uiState.volumeLevel)
        assertEquals(15, uiState.maxVolumeLevel)
        assertFalse(uiState.doNotDisturbAccessGranted)
        assertTrue(uiState.overlayGranted)
        assertFalse(uiState.notificationListenerEnabled)
        assertTrue(uiState.notificationsEnabled)
    }

    /**
     * 서비스 실행 여부와 세션 종료 시각 Flow 변경이 UiState에 반영되는지 검증
     *
     * Given: 서비스 멈춤, 세션 없음 상태로 ViewModel 생성
     * When: 실행 여부 true·종료 시각 지정, 이어서 false·null로 변경
     * Then: running·sessionEndTimeMillis·inProgress가 변경 순서대로 갱신
     */
    @Test
    fun test_02_runningAndSessionEndTimeChangesUpdateUiState() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        assertFalse(viewModel.uiState.value.inProgress)

        /** When **/
        running.value = true
        sessionEndTime.value = 1_700_000_000_000L
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.running)
        assertEquals(1_700_000_000_000L, viewModel.uiState.value.sessionEndTimeMillis)
        assertTrue(viewModel.uiState.value.inProgress)

        /** When **/
        running.value = false
        sessionEndTime.value = null
        waitCoroutine()

        /** Then **/
        assertFalse(viewModel.uiState.value.running)
        assertNull(viewModel.uiState.value.sessionEndTimeMillis)
        assertFalse(viewModel.uiState.value.inProgress)
    }

    /**
     * YouTube 주소가 아니면 저장하지 않고 URL 입력란에만 오류를 표시하는지 검증
     *
     * Given: URL 입력 "https://music.youtube.com/watch?v=abc", 정지 시간 기본값
     * When: save 호출
     * Then: youtubeUrlError true, durationError false, saveSettings 미호출, 입력 확인 토스트 하나
     */
    @Test
    fun test_03_saveWithInvalidUrlShowsUrlError() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        viewModel.updateYoutubeUrlInput("https://music.youtube.com/watch?v=abc")

        /** When **/
        val receivedEvents = collectDuring(viewModel.events) { viewModel.save() }

        /** Then **/
        assertTrue(viewModel.uiState.value.youtubeUrlError)
        assertFalse(viewModel.uiState.value.durationError)
        coVerify(exactly = 0) { repository.saveSettings(any()) }
        assertEquals(listOf(SleepRoutineViewModel.EventShowToast(R.string.sleep_routine_input_invalid)), receivedEvents)
    }

    /**
     * 정지 시간이 범위 밖이면 저장하지 않고 정지 시간 입력란에만 오류를 표시하는지 검증
     *
     * Given: 올바른 URL, 정지 시간 "0"
     * When: save 호출
     * Then: durationError true, youtubeUrlError false, saveSettings 미호출, 입력 확인 토스트 하나
     */
    @Test
    fun test_04_saveWithInvalidDurationShowsDurationError() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        viewModel.updateYoutubeUrlInput("https://youtu.be/abc")
        viewModel.updateDurationInput("0")

        /** When **/
        val receivedEvents = collectDuring(viewModel.events) { viewModel.save() }

        /** Then **/
        assertTrue(viewModel.uiState.value.durationError)
        assertFalse(viewModel.uiState.value.youtubeUrlError)
        coVerify(exactly = 0) { repository.saveSettings(any()) }
        assertEquals(listOf(SleepRoutineViewModel.EventShowToast(R.string.sleep_routine_input_invalid)), receivedEvents)
    }

    /**
     * 오류 표시 중인 입력란의 값을 바꾸면 그 입력란의 오류만 지우는지 검증
     *
     * Given: URL·정지 시간 모두 잘못 입력 후 save 호출로 두 오류 표시
     * When: URL 입력 변경, 이어서 정지 시간 입력 변경
     * Then: URL 변경 후 youtubeUrlError만 false, 정지 시간 변경 후 durationError도 false
     */
    @Test
    fun test_05_inputChangeClearsItsError() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        viewModel.updateYoutubeUrlInput("abc")
        viewModel.updateDurationInput("601")
        viewModel.save()
        waitCoroutine()
        assertTrue(viewModel.uiState.value.youtubeUrlError)
        assertTrue(viewModel.uiState.value.durationError)

        /** When **/
        viewModel.updateYoutubeUrlInput("https://youtu.be/abc")

        /** Then **/
        assertFalse(viewModel.uiState.value.youtubeUrlError)
        assertTrue(viewModel.uiState.value.durationError)

        /** When **/
        viewModel.updateDurationInput("30")

        /** Then **/
        assertFalse(viewModel.uiState.value.durationError)
    }

    /**
     * 올바른 입력을 앞뒤 공백을 뺀 URL과 함께 저장하고 저장 완료 토스트를 보내는지 검증
     *
     * Given: URL "  https://youtu.be/abc  ", 정지 시간 "90", 볼륨 꺼짐, 볼륨 8
     * When: save 호출
     * Then: saveSettings(SleepRoutineSettings("https://youtu.be/abc", 90, false, 8)) 호출, 저장 완료 토스트 하나
     */
    @Test
    fun test_06_saveWithValidInputsSavesAndShowsToast() = runTest {
        /** Given **/
        val viewModel = createViewModelWithValidInputs()

        /** When **/
        val receivedEvents = collectDuring(viewModel.events) { viewModel.save() }

        /** Then **/
        coVerify { repository.saveSettings(validInputSettings) }
        assertEquals(listOf(SleepRoutineViewModel.EventShowToast(R.string.sleep_routine_save_completed)), receivedEvents)
    }

    /**
     * 저장에 실패하면 저장 실패 토스트만 보내는지 검증
     *
     * Given: 올바른 입력, saveSettings 실패 결과
     * When: save 호출
     * Then: 저장 실패 토스트 하나
     */
    @Test
    fun test_07_saveFailureShowsFailedToast() = runTest {
        /** Given **/
        coEvery { repository.saveSettings(any()) } returns Result.failure(IOException("disk full"))
        val viewModel = createViewModelWithValidInputs()

        /** When **/
        val receivedEvents = collectDuring(viewModel.events) { viewModel.save() }

        /** Then **/
        assertEquals(listOf(SleepRoutineViewModel.EventShowToast(R.string.sleep_routine_save_failed)), receivedEvents)
    }

    /**
     * 시작은 올바른 입력을 저장한 뒤에만 서비스를 시작하는지 검증
     *
     * Given: 올바른 입력(URL "  https://youtu.be/abc  ", 90분, 볼륨 꺼짐, 볼륨 8)
     * When: startRoutine 호출, 이어서 URL을 잘못 바꾼 뒤 startRoutine 호출
     * Then: 첫 호출은 saveSettings(SleepRoutineSettings("https://youtu.be/abc", 90, false, 8)) 후 systemStatus.startRoutine 1회, 이벤트 없음. 두 번째는 URL 오류, 입력 확인 토스트 하나, 저장·시작 추가 호출 없음
     */
    @Test
    fun test_08_startRoutineStartsOnlyAfterSave() = runTest {
        /** Given **/
        val viewModel = createViewModelWithValidInputs()

        /** When **/
        val receivedEvents = collectDuring(viewModel.events) { viewModel.startRoutine() }

        /** Then **/
        coVerifyOrder {
            repository.saveSettings(validInputSettings)
            systemStatus.startRoutine()
        }
        coVerify(exactly = 1) {
            repository.saveSettings(any())
            systemStatus.startRoutine()
        }
        assertEquals(emptyList<ViewModelEvent>(), receivedEvents)

        /** When **/
        viewModel.updateYoutubeUrlInput("not url")
        val invalidStartEvents = collectDuring(viewModel.events) { viewModel.startRoutine() }

        /** Then **/
        assertTrue(viewModel.uiState.value.youtubeUrlError)
        coVerify(exactly = 1) {
            repository.saveSettings(any())
            systemStatus.startRoutine()
        }
        assertEquals(listOf(SleepRoutineViewModel.EventShowToast(R.string.sleep_routine_input_invalid)), invalidStartEvents)
    }

    /**
     * 저장에 실패하면 서비스를 시작하지 않고 저장 실패 토스트를 보내는지 검증
     *
     * Given: 올바른 입력, saveSettings 실패 결과
     * When: startRoutine 호출
     * Then: systemStatus.startRoutine 미호출, 저장 실패 토스트 하나
     */
    @Test
    fun test_09_startRoutineSaveFailureDoesNotStart() = runTest {
        /** Given **/
        coEvery { repository.saveSettings(any()) } returns Result.failure(IOException("disk full"))
        val viewModel = createViewModelWithValidInputs()

        /** When **/
        val receivedEvents = collectDuring(viewModel.events) { viewModel.startRoutine() }

        /** Then **/
        verify(exactly = 0) { systemStatus.startRoutine() }
        assertEquals(listOf(SleepRoutineViewModel.EventShowToast(R.string.sleep_routine_save_failed)), receivedEvents)
    }

    /**
     * 종료가 서비스 종료를 요청하는지 검증
     *
     * Given: 서비스 실행 중 상태로 ViewModel 생성
     * When: stopRoutine 호출
     * Then: systemStatus.stopRoutine 1회
     */
    @Test
    fun test_10_stopRoutineStopsService() = runTest {
        /** Given **/
        running.value = true
        val viewModel = createViewModel()

        /** When **/
        viewModel.stopRoutine()

        /** Then **/
        verify(exactly = 1) { systemStatus.stopRoutine() }
    }

    /**
     * 볼륨 단계를 0부터 최대 단계까지로 맞추는지 검증
     *
     * Given: 최대 볼륨 15로 ViewModel 생성
     * When: updateVolumeLevel(99), 이어서 updateVolumeLevel(-1) 호출
     * Then: volumeLevel 15, 이어서 0
     */
    @Test
    fun test_11_updateVolumeLevelCoercesToRange() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.updateVolumeLevel(99)

        /** Then **/
        assertEquals(15, viewModel.uiState.value.volumeLevel)

        /** When **/
        viewModel.updateVolumeLevel(-1)

        /** Then **/
        assertEquals(0, viewModel.uiState.value.volumeLevel)
    }

    /**
     * refreshStatus가 바뀐 권한 상태를 다시 읽어 반영하는지 검증
     *
     * Given: 권한 모두 미허용 상태로 생성 후 모두 허용으로 변경
     * When: refreshStatus 호출
     * Then: 네 권한 상태 모두 true
     */
    @Test
    fun test_12_refreshStatusUpdatesPermissions() = runTest {
        /** Given **/
        every { systemStatus.hasDoNotDisturbAccess() } returns false
        every { systemStatus.canDrawOverlays() } returns false
        every { systemStatus.isNotificationListenerEnabled() } returns false
        every { systemStatus.canShowNotification() } returns false
        val viewModel = createViewModel()
        every { systemStatus.hasDoNotDisturbAccess() } returns true
        every { systemStatus.canDrawOverlays() } returns true
        every { systemStatus.isNotificationListenerEnabled() } returns true
        every { systemStatus.canShowNotification() } returns true

        /** When **/
        viewModel.refreshStatus()

        /** Then **/
        val uiState = viewModel.uiState.value
        assertTrue(uiState.doNotDisturbAccessGranted)
        assertTrue(uiState.overlayGranted)
        assertTrue(uiState.notificationListenerEnabled)
        assertTrue(uiState.notificationsEnabled)
    }

    /**
     * 설정 화면 열기 함수가 각 설정 화면 이동 이벤트를 보내는지 검증
     *
     * Given: ViewModel 생성
     * When: openDoNotDisturbSettings, openOverlaySettings, openNotificationListenerSettings, openNotificationSettings 순서로 호출
     * Then: 대응하는 이벤트 4개를 호출 순서대로 수신
     */
    @Test
    fun test_13_openSettingsSendsSettingsEvents() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        val receivedEvents = collectDuring(viewModel.events) {
            viewModel.openDoNotDisturbSettings()
            viewModel.openOverlaySettings()
            viewModel.openNotificationListenerSettings()
            viewModel.openNotificationSettings()
        }

        /** Then **/
        assertEquals(
            listOf(
                SleepRoutineViewModel.EventOpenDoNotDisturbSettings,
                SleepRoutineViewModel.EventOpenOverlaySettings,
                SleepRoutineViewModel.EventOpenNotificationListenerSettings,
                SleepRoutineViewModel.EventOpenNotificationSettings,
            ),
            receivedEvents,
        )
    }

    /**
     * 서비스가 멈췄어도 세션이 남아 있으면 진행 중으로 보고 종료로 서비스 마무리를 요청하는지 검증
     *
     * Given: 서비스 멈춤, 세션 종료 시각 남음(앱 업데이트·강제 중지 뒤 상태)
     * When: ViewModel 생성 후 stopRoutine 호출
     * Then: running false, inProgress true, systemStatus.stopRoutine 1회
     */
    @Test
    fun test_14_leftoverSessionIsInProgressAndStops() = runTest {
        /** Given **/
        running.value = false
        sessionEndTime.value = 1_700_000_000_000L

        /** When **/
        val viewModel = createViewModel()
        viewModel.stopRoutine()

        /** Then **/
        assertFalse(viewModel.uiState.value.running)
        assertTrue(viewModel.uiState.value.inProgress)
        verify(exactly = 1) { systemStatus.stopRoutine() }
    }

    /**
     * 설정을 불러오기 전에는 저장·시작 요청을 무시하고, 불러온 뒤 settingsLoaded가 true가 되는지 검증
     *
     * Given: getSettings가 끝나지 않은 상태로 ViewModel 생성, URL 입력을 올바른 값으로 변경
     * When: save, startRoutine 호출 후 getSettings 완료
     * Then: 완료 전 settingsLoaded false, saveSettings·systemStatus.startRoutine 미호출, 이벤트 없음. 완료 후 settingsLoaded true, 저장된 URL 반영
     */
    @Test
    fun test_15_ignoresSaveAndStartUntilSettingsLoaded() = runTest {
        /** Given **/
        val loadedSettings = CompletableDeferred<SleepRoutineSettings>()
        coEvery { repository.getSettings() } coAnswers { loadedSettings.await() }
        val viewModel = createViewModel()
        viewModel.updateYoutubeUrlInput("https://youtu.be/abc")

        /** When **/
        val receivedEvents = collectDuring(viewModel.events) {
            viewModel.save()
            viewModel.startRoutine()
        }

        /** Then **/
        assertFalse(viewModel.uiState.value.settingsLoaded)
        coVerify(exactly = 0) {
            repository.saveSettings(any())
            systemStatus.startRoutine()
        }
        assertEquals(emptyList<ViewModelEvent>(), receivedEvents)

        /** When **/
        loadedSettings.complete(SleepRoutineSettings(youtubeUrl = "https://www.youtube.com/watch?v=saved"))
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.settingsLoaded)
        assertEquals("https://www.youtube.com/watch?v=saved", viewModel.uiState.value.youtubeUrlInput)
    }

    /**
     * 저장·시작이 진행 중이면 연타한 시작 요청을 무시하고, 끝난 뒤 들어온 요청은 다시 처리하는지 검증
     *
     * Given: 올바른 입력, saveSettings가 끝나지 않는 상태
     * When: startRoutine 두 번 호출 후 saveSettings 완료, 이어서 startRoutine 한 번 더 호출
     * Then: 완료 전 saveSettings 1회·systemStatus.startRoutine 미호출, 완료 후 둘 다 1회, 마지막 호출 뒤 둘 다 2회
     */
    @Test
    fun test_16_startRoutineIgnoresRepeatWhileSaving() = runTest {
        /** Given **/
        val saveResult = CompletableDeferred<Result<Unit>>()
        coEvery { repository.saveSettings(any()) } coAnswers { saveResult.await() }
        val viewModel = createViewModelWithValidInputs()

        /** When **/
        viewModel.startRoutine()
        viewModel.startRoutine()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { repository.saveSettings(any()) }
        verify(exactly = 0) { systemStatus.startRoutine() }

        /** When **/
        saveResult.complete(Result.success(Unit))
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) {
            repository.saveSettings(any())
            systemStatus.startRoutine()
        }

        /** When **/
        viewModel.startRoutine()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 2) {
            repository.saveSettings(any())
            systemStatus.startRoutine()
        }
    }
}
