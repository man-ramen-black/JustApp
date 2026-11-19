package com.black.feature.sleeproutine.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import com.black.app.testutil.BaseUiTest
import com.black.core.ui.component.ToolTopBarTestTags
import com.black.core.ui.theme.BlackTheme
import com.black.feature.sleeproutine.R
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.concurrent.TimeUnit

/**
 * [SleepRoutineScreen]이 설정 입력·권한 상태·실행 섹션을 표시하는지 검증하는 Compose UI 테스트
 *
 * 상태 행·실행 섹션·설정 불러오기 전후는 기기의 권한·서비스 실행 상태에 따라 한 분기만 실행되지 않도록 [SleepRoutineContent]에
 * 테스트가 고른 상태를 주입해 분기마다 고정된 기대값으로 검증하고, 입력·저장·시작·종료는 콜백 호출로 검증.
 * 계측 테스트는 개발 앱에서 돌아 실사용 앱의 설정·세션은 건드리지 않지만, 시작하면 기기 전체 방해 금지가 켜지고
 * YouTube·Sleep as Android가 실행되므로 Screen 호스팅 테스트에서는 시작·종료를 누르지 않음.
 * 실제 저장·시작·종료 동작은 ViewModel 유닛 테스트로 검증
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SleepRoutineScreenTest : BaseUiTest() {

    /** 저장된 설정을 불러온 뒤의 화면 상태, 입력·저장·시작이 활성인 기본값 */
    private val loadedUiState = SleepRoutineUiState(settingsLoaded = true)

    /** [hostSleepRoutineContent]로 호스팅한 콘텐츠에 주입하는 화면 상태 */
    private val contentUiState = mutableStateOf(loadedUiState)

    /** SleepRoutineScreen을 HiltTestActivity에 setContent로 호스팅 */
    private fun hostSleepRoutineScreen(onNavigateUp: () -> Unit = {}) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    SleepRoutineScreen(onNavigateUp = onNavigateUp)
                }
            }
        }
    }

    /** [contentUiState]를 주입한 SleepRoutineContent를 HiltTestActivity에 setContent로 호스팅 */
    private fun hostSleepRoutineContent(
        onYoutubeUrlChange: (String) -> Unit = {},
        onDurationChange: (String) -> Unit = {},
        onVolumeEnabledChange: (Boolean) -> Unit = {},
        onClickSave: () -> Unit = {},
        onClickDoNotDisturb: () -> Unit = {},
        onClickOverlay: () -> Unit = {},
        onClickNotificationListener: () -> Unit = {},
        onClickNotification: () -> Unit = {},
        onClickAccessibility: () -> Unit = {},
        onClickStart: () -> Unit = {},
        onClickStop: () -> Unit = {},
    ) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    SleepRoutineContent(
                        uiState = contentUiState.value,
                        onNavigateUp = {},
                        onYoutubeUrlChange = onYoutubeUrlChange,
                        onDurationChange = onDurationChange,
                        onVolumeEnabledChange = onVolumeEnabledChange,
                        onVolumeLevelChange = {},
                        onClickSave = onClickSave,
                        onClickDoNotDisturb = onClickDoNotDisturb,
                        onClickOverlay = onClickOverlay,
                        onClickNotificationListener = onClickNotificationListener,
                        onClickNotification = onClickNotification,
                        onClickAccessibility = onClickAccessibility,
                        onClickStart = onClickStart,
                        onClickStop = onClickStop,
                    )
                }
            }
        }
    }

    /** 지금부터 1시간 뒤 시각(epoch ms), 아직 지나지 않은 세션 종료 시각으로 주입 */
    private fun futureEndTimeMillis(): Long = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)

    /**
     * 제목·입력 칸·저장 버튼이 표시되는지 검증
     *
     * Given:
     * - HiltTestActivity에 SleepRoutineScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 제목 "수면 루틴" 표시
     * - YouTube URL·정지 시간 입력 칸 표시
     * - 저장 버튼 표시
     */
    @Test
    fun test_01_displaysTitleInputsAndSaveButton() {
        /** Given **/
        hostSleepRoutineScreen()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagDisplayed(SleepRoutineTestTags.TITLE)
        assertTagText(SleepRoutineTestTags.TITLE, context.getString(R.string.fragment_name_sleep_routine))
        assertTagDisplayed(SleepRoutineTestTags.YOUTUBE_URL_INPUT, scrollTo = true)
        assertTagDisplayed(SleepRoutineTestTags.DURATION_INPUT, scrollTo = true)
        assertTagDisplayed(SleepRoutineTestTags.SAVE_BUTTON, scrollTo = true)
    }

    /**
     * 권한이 모두 없으면 상태 행이 필요로 표시되고 누르면 해당 설정 화면 이동을 요청하는지 검증
     *
     * Given:
     * - 방해 금지 접근·다른 앱 위에 표시·알림 접근·알림 표시 미허용, 접근성 서비스 꺼짐 상태로 SleepRoutineContent 호스팅
     * When:
     * - 다섯 상태 행 차례로 클릭
     * Then:
     * - 다섯 행 모두 라벨과 "필요" 표시
     * - 각 행의 콜백 1회씩 호출
     */
    @Test
    fun test_02_permissionRowsShowRequiredAndClick() {
        /** Given **/
        var doNotDisturbClickCount = 0
        var overlayClickCount = 0
        var notificationListenerClickCount = 0
        var notificationClickCount = 0
        var accessibilityClickCount = 0
        contentUiState.value = loadedUiState.copy(
            doNotDisturbAccessGranted = false,
            overlayGranted = false,
            notificationListenerEnabled = false,
            notificationsEnabled = false,
            accessibilityServiceEnabled = false,
        )
        hostSleepRoutineContent(
            onClickDoNotDisturb = { doNotDisturbClickCount++ },
            onClickOverlay = { overlayClickCount++ },
            onClickNotificationListener = { notificationListenerClickCount++ },
            onClickNotification = { notificationClickCount++ },
            onClickAccessibility = { accessibilityClickCount++ },
        )

        /** When **/
        clickTag(SleepRoutineTestTags.DO_NOT_DISTURB_STATUS, scrollTo = true)
        clickTag(SleepRoutineTestTags.OVERLAY_STATUS, scrollTo = true)
        clickTag(SleepRoutineTestTags.NOTIFICATION_LISTENER_STATUS, scrollTo = true)
        clickTag(SleepRoutineTestTags.NOTIFICATION_STATUS, scrollTo = true)
        clickTag(SleepRoutineTestTags.ACCESSIBILITY_STATUS, scrollTo = true)

        /** Then **/
        val requiredValue = context.getString(R.string.permission_status_required)
        assertTagTextContains(SleepRoutineTestTags.DO_NOT_DISTURB_STATUS, "방해 금지 접근", requiredValue, scrollTo = true)
        assertTagTextContains(SleepRoutineTestTags.OVERLAY_STATUS, "다른 앱 위에 표시", requiredValue, scrollTo = true)
        assertTagTextContains(SleepRoutineTestTags.NOTIFICATION_LISTENER_STATUS, "알림 접근", requiredValue, scrollTo = true)
        assertTagTextContains(SleepRoutineTestTags.NOTIFICATION_STATUS, "알림 표시", requiredValue, scrollTo = true)
        assertTagTextContains(SleepRoutineTestTags.ACCESSIBILITY_STATUS, "접근성 서비스", requiredValue, scrollTo = true)
        assertEquals(1, doNotDisturbClickCount)
        assertEquals(1, overlayClickCount)
        assertEquals(1, notificationListenerClickCount)
        assertEquals(1, notificationClickCount)
        assertEquals(1, accessibilityClickCount)
    }

    /**
     * 권한이 모두 있으면 상태 행이 허용으로 표시되고 누를 수 없는지 검증
     *
     * Given:
     * - 네 권한 모두 허용, 접근성 서비스 켜짐 상태로 SleepRoutineContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 다섯 행 모두 "허용" 표시, 클릭 동작 없음
     */
    @Test
    fun test_03_permissionRowsShowAllowed() {
        /** Given **/
        contentUiState.value = loadedUiState.copy(
            doNotDisturbAccessGranted = true,
            overlayGranted = true,
            notificationListenerEnabled = true,
            notificationsEnabled = true,
            accessibilityServiceEnabled = true,
        )
        hostSleepRoutineContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        val allowedValue = context.getString(R.string.permission_status_allowed)
        listOf(
            SleepRoutineTestTags.DO_NOT_DISTURB_STATUS,
            SleepRoutineTestTags.OVERLAY_STATUS,
            SleepRoutineTestTags.NOTIFICATION_LISTENER_STATUS,
            SleepRoutineTestTags.NOTIFICATION_STATUS,
            SleepRoutineTestTags.ACCESSIBILITY_STATUS,
        ).forEach { tag ->
            assertTagTextContains(tag, allowedValue, scrollTo = true)
            assertTagMatches(tag, !hasClickAction())
        }
    }

    /**
     * 입력 오류 상태에서만 입력 칸 아래 오류 문구가 표시되는지 검증
     *
     * Given:
     * - 오류 없는 상태로 SleepRoutineContent 호스팅
     * - URL·정지 시간 오류 문구 없음
     * When:
     * - 상태를 youtubeUrlError·durationError true로 변경
     * Then:
     * - "youtube.com 또는 youtu.be 주소를 입력해주세요.", "1부터 600 사이로 입력해주세요." 표시
     */
    @Test
    fun test_04_inputErrorsShowMessages() {
        /** Given **/
        val urlErrorMessage = context.getString(R.string.sleep_routine_youtube_url_error)
        val durationErrorMessage = context.getString(R.string.sleep_routine_duration_error)
        contentUiState.value = loadedUiState
        hostSleepRoutineContent()
        assertTextNotExists(urlErrorMessage)
        assertTextNotExists(durationErrorMessage)

        /** When **/
        composeRule.runOnIdle {
            contentUiState.value = contentUiState.value.copy(youtubeUrlError = true, durationError = true)
        }

        /** Then **/
        assertTextDisplayed(urlErrorMessage, scrollTo = true)
        assertTextDisplayed(durationErrorMessage, scrollTo = true)
    }

    /**
     * 실행 중이 아니면 지금 시작 버튼만 표시되고 누르면 시작을 요청하는지 검증
     *
     * Given:
     * - running false 상태로 SleepRoutineContent 호스팅
     * When:
     * - start_button 클릭
     * Then:
     * - start_button "지금 시작" 표시, stop_button·end_time_text 없음
     * - onClickStart 1회 호출
     */
    @Test
    fun test_05_notRunningShowsStartButton() {
        /** Given **/
        var startClickCount = 0
        contentUiState.value = loadedUiState.copy(running = false)
        hostSleepRoutineContent(onClickStart = { startClickCount++ })

        /** When **/
        clickTag(SleepRoutineTestTags.START_BUTTON, scrollTo = true)

        /** Then **/
        assertTagTextContains(SleepRoutineTestTags.START_BUTTON, "지금 시작", scrollTo = true)
        assertTagNotExists(SleepRoutineTestTags.STOP_BUTTON)
        assertTagNotExists(SleepRoutineTestTags.END_TIME_TEXT)
        assertEquals(1, startClickCount)
    }

    /**
     * 실행 중이면 종료 시각 문구와 종료 버튼이 표시되고 누르면 종료를 요청하는지 검증
     *
     * Given:
     * - running true, 1시간 뒤 sessionEndTimeMillis 상태로 SleepRoutineContent 호스팅
     * When:
     * - stop_button 클릭
     * Then:
     * - end_time_text에 "에 종료합니다." 포함, stop_button "종료" 표시, start_button 없음
     * - onClickStop 1회 호출
     */
    @Test
    fun test_06_runningShowsEndTimeAndStopButton() {
        /** Given **/
        var stopClickCount = 0
        contentUiState.value = loadedUiState.copy(running = true, sessionEndTimeMillis = futureEndTimeMillis())
        hostSleepRoutineContent(onClickStop = { stopClickCount++ })

        /** When **/
        clickTag(SleepRoutineTestTags.STOP_BUTTON, scrollTo = true)

        /** Then **/
        // 시각 표기는 기기의 시간대·12/24시간 설정을 따라 고정 문구 부분만 검증
        assertTagTextContains(SleepRoutineTestTags.END_TIME_TEXT, "에 종료합니다.", substring = true, scrollTo = true)
        assertTagTextContains(SleepRoutineTestTags.STOP_BUTTON, "종료", scrollTo = true)
        assertTagNotExists(SleepRoutineTestTags.START_BUTTON)
        assertEquals(1, stopClickCount)
    }

    /**
     * 서비스가 멈췄어도 세션이 남아 있으면 종료 시각 문구와 종료 버튼이 표시되고 누르면 종료를 요청하는지 검증
     *
     * Given:
     * - running false, 1시간 뒤 sessionEndTimeMillis 상태(앱 업데이트·강제 중지 뒤 세션 잔류)로 SleepRoutineContent 호스팅
     * When:
     * - stop_button 클릭
     * Then:
     * - end_time_text에 "에 종료합니다." 포함, stop_button "종료" 표시, start_button 없음
     * - onClickStop 1회 호출
     */
    @Test
    fun test_07_leftoverSessionShowsStopButton() {
        /** Given **/
        var stopClickCount = 0
        contentUiState.value = loadedUiState.copy(running = false, sessionEndTimeMillis = futureEndTimeMillis())
        hostSleepRoutineContent(onClickStop = { stopClickCount++ })

        /** When **/
        clickTag(SleepRoutineTestTags.STOP_BUTTON, scrollTo = true)

        /** Then **/
        assertTagTextContains(SleepRoutineTestTags.END_TIME_TEXT, "에 종료합니다.", substring = true, scrollTo = true)
        assertTagTextContains(SleepRoutineTestTags.STOP_BUTTON, "종료", scrollTo = true)
        assertTagNotExists(SleepRoutineTestTags.START_BUTTON)
        assertEquals(1, stopClickCount)
    }

    /**
     * 볼륨 맞추기가 꺼지면 슬라이더가 비활성이고 현재 단계 문구가 표시되는지 검증
     *
     * Given:
     * - volumeEnabled false, volumeLevel 6, maxVolumeLevel 15 상태로 SleepRoutineContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - volume_slider 비활성
     * - volume_level_text "6 / 15"
     */
    @Test
    fun test_08_volumeDisabledDisablesSlider() {
        /** Given **/
        contentUiState.value = loadedUiState.copy(volumeEnabled = false, volumeLevel = 6, maxVolumeLevel = 15)
        hostSleepRoutineContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagMatches(SleepRoutineTestTags.VOLUME_SLIDER, isNotEnabled())
        assertTagText(SleepRoutineTestTags.VOLUME_LEVEL_TEXT, "6 / 15")
    }

    /**
     * 상단 바 뒤로 가기 버튼을 누르면 onNavigateUp이 호출되는지 검증
     *
     * Given:
     * - SleepRoutineScreen 호스팅
     * When:
     * - 뒤로 가기 버튼 클릭
     * Then:
     * - onNavigateUp 1회 호출
     */
    @Test
    fun test_09_clickNavigateUpCallsCallback() {
        /** Given **/
        var navigateUpCount = 0
        hostSleepRoutineScreen(onNavigateUp = { navigateUpCount++ })

        /** When **/
        clickTag(ToolTopBarTestTags.NAVIGATE_UP_BUTTON)

        /** Then **/
        assertEquals(1, navigateUpCount)
    }

    /**
     * URL·정지 시간 입력 칸에 입력하면 입력한 값으로 각 콜백을 호출하는지 검증
     *
     * Given:
     * - 설정을 불러온 상태로 SleepRoutineContent 호스팅
     * - 입력 콜백은 받은 값을 주입 상태에 반영
     * When:
     * - youtube_url_input을 "https://youtu.be/abc"로 교체
     * - duration_input을 "45"로 교체
     * Then:
     * - onYoutubeUrlChange("https://youtu.be/abc") 호출
     * - onDurationChange("45") 호출
     */
    @Test
    fun test_10_inputChangesInvokeCallbacks() {
        /** Given **/
        val youtubeUrlInputs = mutableListOf<String>()
        val durationInputs = mutableListOf<String>()
        contentUiState.value = loadedUiState
        // 값을 상태에 반영하지 않으면 포커스가 옮겨질 때 입력 칸이 이전 값으로 콜백을 다시 불러 ViewModel처럼 상태에 반영
        hostSleepRoutineContent(
            onYoutubeUrlChange = { input ->
                youtubeUrlInputs.add(input)
                contentUiState.value = contentUiState.value.copy(youtubeUrlInput = input)
            },
            onDurationChange = { input ->
                durationInputs.add(input)
                contentUiState.value = contentUiState.value.copy(durationInput = input)
            },
        )

        /** When **/
        replaceTagText(SleepRoutineTestTags.YOUTUBE_URL_INPUT, "https://youtu.be/abc")
        replaceTagText(SleepRoutineTestTags.DURATION_INPUT, "45")

        /** Then **/
        assertEquals(listOf("https://youtu.be/abc"), youtubeUrlInputs)
        assertEquals(listOf("45"), durationInputs)
    }

    /**
     * 볼륨 맞추기가 켜진 상태에서 스위치를 누르면 끄기를 요청하는지 검증
     *
     * Given:
     * - volumeEnabled true 상태로 SleepRoutineContent 호스팅
     * When:
     * - volume_switch 클릭
     * Then:
     * - onVolumeEnabledChange(false) 1회 호출
     */
    @Test
    fun test_11_clickVolumeSwitchRequestsDisable() {
        /** Given **/
        val volumeEnabledChanges = mutableListOf<Boolean>()
        contentUiState.value = loadedUiState.copy(volumeEnabled = true)
        hostSleepRoutineContent(onVolumeEnabledChange = { volumeEnabledChanges.add(it) })

        /** When **/
        clickTag(SleepRoutineTestTags.VOLUME_SWITCH, scrollTo = true)

        /** Then **/
        assertEquals(listOf(false), volumeEnabledChanges)
    }

    /**
     * 저장 버튼을 누르면 저장을 요청하는지 검증
     *
     * Given:
     * - 설정을 불러온 상태로 SleepRoutineContent 호스팅
     * When:
     * - save_button 클릭
     * Then:
     * - onClickSave 1회 호출
     */
    @Test
    fun test_12_clickSaveInvokesCallback() {
        /** Given **/
        var saveClickCount = 0
        contentUiState.value = loadedUiState
        hostSleepRoutineContent(onClickSave = { saveClickCount++ })

        /** When **/
        clickTag(SleepRoutineTestTags.SAVE_BUTTON, scrollTo = true)

        /** Then **/
        assertEquals(1, saveClickCount)
    }

    /**
     * 볼륨 맞추기가 켜지면 슬라이더가 활성이고 TalkBack용 설명·단계 상태를 갖는지 검증
     *
     * Given:
     * - volumeEnabled true, volumeLevel 6, maxVolumeLevel 15 상태로 SleepRoutineContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - volume_slider 활성, 설명 "미디어 볼륨", 상태 설명 "6 / 15"
     * - volume_level_text "6 / 15"
     */
    @Test
    fun test_13_volumeEnabledEnablesSlider() {
        /** Given **/
        contentUiState.value = loadedUiState.copy(volumeEnabled = true, volumeLevel = 6, maxVolumeLevel = 15)
        hostSleepRoutineContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagMatches(SleepRoutineTestTags.VOLUME_SLIDER, isEnabled())
        assertTagMatches(SleepRoutineTestTags.VOLUME_SLIDER, hasContentDescription("미디어 볼륨"))
        assertTagMatches(SleepRoutineTestTags.VOLUME_SLIDER, hasStateDescription("6 / 15"))
        assertTagText(SleepRoutineTestTags.VOLUME_LEVEL_TEXT, "6 / 15")
    }

    /**
     * 서비스는 실행 중인데 세션이 아직 기록되지 않았으면 종료 버튼만 표시되는지 검증
     *
     * Given:
     * - running true, sessionEndTimeMillis null 상태(서비스 시작 직후)로 SleepRoutineContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - stop_button 표시
     * - end_time_text·start_button 없음
     */
    @Test
    fun test_14_runningWithoutSessionShowsOnlyStopButton() {
        /** Given **/
        contentUiState.value = loadedUiState.copy(running = true, sessionEndTimeMillis = null)
        hostSleepRoutineContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagDisplayed(SleepRoutineTestTags.STOP_BUTTON, scrollTo = true)
        assertTagNotExists(SleepRoutineTestTags.END_TIME_TEXT)
        assertTagNotExists(SleepRoutineTestTags.START_BUTTON)
    }

    /**
     * 서비스가 멈추고 종료 시각이 지난 세션만 남았으면 지난 시각 대신 종료로 마무리하라는 안내가 표시되는지 검증
     *
     * Given:
     * - running false, 아주 과거(1970년)의 sessionEndTimeMillis 상태로 SleepRoutineContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - end_time_text "종료 예정 시각이 지났습니다.\n종료를 눌러 마무리해주세요."
     * - "에 종료합니다." 문구 없음, stop_button 표시
     */
    @Test
    fun test_15_passedEndTimeShowsFinishGuide() {
        /** Given **/
        contentUiState.value = loadedUiState.copy(running = false, sessionEndTimeMillis = 1_000L)
        hostSleepRoutineContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagText(SleepRoutineTestTags.END_TIME_TEXT, "종료 예정 시각이 지났습니다.\n종료를 눌러 마무리해주세요.")
        assertTextNotExists("에 종료합니다.", substring = true)
        assertTagDisplayed(SleepRoutineTestTags.STOP_BUTTON, scrollTo = true)
    }

    /**
     * 설정을 불러오기 전에는 입력 칸·스위치·슬라이더·저장·시작 버튼이 비활성이고 눌러도 콜백이 호출되지 않는지 검증
     *
     * Given:
     * - settingsLoaded false, volumeEnabled true, running false 상태로 SleepRoutineContent 호스팅
     * When:
     * - save_button, start_button 클릭
     * Then:
     * - youtube_url_input, duration_input, volume_switch, volume_slider, save_button, start_button 비활성
     * - onClickSave, onClickStart 미호출
     */
    @Test
    fun test_16_settingsNotLoadedDisablesInputsAndButtons() {
        /** Given **/
        var saveClickCount = 0
        var startClickCount = 0
        contentUiState.value = SleepRoutineUiState(settingsLoaded = false, volumeEnabled = true, running = false)
        hostSleepRoutineContent(
            onClickSave = { saveClickCount++ },
            onClickStart = { startClickCount++ },
        )

        /** When **/
        clickTag(SleepRoutineTestTags.SAVE_BUTTON, scrollTo = true)
        clickTag(SleepRoutineTestTags.START_BUTTON, scrollTo = true)

        /** Then **/
        listOf(
            SleepRoutineTestTags.YOUTUBE_URL_INPUT,
            SleepRoutineTestTags.DURATION_INPUT,
            SleepRoutineTestTags.VOLUME_SWITCH,
            SleepRoutineTestTags.VOLUME_SLIDER,
            SleepRoutineTestTags.SAVE_BUTTON,
            SleepRoutineTestTags.START_BUTTON,
        ).forEach { tag ->
            assertTagMatches(tag, isNotEnabled())
        }
        assertEquals(0, saveClickCount)
        assertEquals(0, startClickCount)
    }
}
