package com.black.feature.floatingbutton.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import com.black.app.testutil.BaseUiTest
import com.black.core.ui.component.OverlayPermissionDialogTestTags
import com.black.core.ui.component.ToolTopBarTestTags
import com.black.core.ui.theme.BlackTheme
import com.black.feature.floatingbutton.R
import com.black.feature.floatingbutton.service.FloatingButtonController
import com.black.feature.floatingbutton.service.FloatingForegroundService
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [FloatingSettingScreen]이 플로팅 버튼 실행 상태와 권한 상태를 표시하고 스위치로 켜고 끄는지 검증하는 Compose UI 테스트
 *
 * 스위치·상태 행의 표시는 기기의 실행·권한 상태에 따라 한 분기만 실행되지 않도록 [FloatingSettingContent]에
 * 테스트가 고른 상태를 주입해 분기마다 고정된 기대값으로 검증.
 * 테스트 기기가 사용자 실사용 기기라 테스트 전 플로팅 버튼 실행 여부를 보관했다가 테스트 후 되돌림.
 * 권한 안내 다이얼로그 표시는 테스트가 권한을 거부해 검증하고, 설정 화면 이동은 테스트 앱을 벗어나므로 ViewModel 유닛 테스트로 검증
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FloatingSettingScreenTest : BaseUiTest() {

    private val controller = FloatingButtonController(context)

    /** setup에서 읽은 테스트 전 사용자의 플로팅 버튼 실행 여부 */
    private var originalRunning = false

    /** 사용자의 플로팅 버튼 실행 여부를 보관하고, 테스트 중 바뀌었으면 테스트 후 복원하도록 등록 */
    override fun setup() {
        originalRunning = preserveValue(
            read = { FloatingForegroundService.running.value },
            restore = { running ->
                if (FloatingForegroundService.running.value != running) {
                    if (running) controller.start() else controller.stop()
                    waitUntilRunning(running)
                }
            },
        )
    }

    /** FloatingSettingScreen을 HiltTestActivity에 setContent로 호스팅 */
    private fun hostFloatingSettingScreen(onNavigateUp: () -> Unit = {}) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    FloatingSettingScreen(onNavigateUp = onNavigateUp)
                }
            }
        }
    }

    /** [hostFloatingSettingContent]로 호스팅한 콘텐츠에 주입하는 화면 상태 */
    private val contentUiState = mutableStateOf(FloatingSettingUiState())

    /** [contentUiState]를 주입한 FloatingSettingContent를 HiltTestActivity에 setContent로 호스팅 */
    private fun hostFloatingSettingContent(
        onClickOverlayPermission: () -> Unit = {},
        onClickAccessibility: () -> Unit = {},
    ) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    FloatingSettingContent(
                        uiState = contentUiState.value,
                        onNavigateUp = {},
                        onRunningChange = {},
                        onClickOverlayPermission = onClickOverlayPermission,
                        onClickAccessibility = onClickAccessibility,
                    )
                }
            }
        }
    }

    /**
     * 제목과 플로팅 버튼 표시 스위치가 표시되는지 검증
     *
     * Given:
     * - 별도 준비 없음
     * When:
     * - FloatingSettingScreen 호스팅
     * Then:
     * - 제목 "플로팅 버튼" 표시
     * - 플로팅 버튼 표시 스위치 표시
     */
    @Test
    fun test_01_displaysTitleAndRunningSwitch() {
        /** Given **/
        // 별도 준비 없음

        /** When **/
        hostFloatingSettingScreen()

        /** Then **/
        assertTagDisplayed(FloatingSettingTestTags.TITLE)
        assertTagText(FloatingSettingTestTags.TITLE, context.getString(R.string.fragment_name_floating))
        assertTagDisplayed(FloatingSettingTestTags.RUNNING_SWITCH)
    }

    /**
     * 스위치가 상태의 플로팅 버튼 실행 여부를 따르는지 검증
     *
     * Given:
     * - 실행 중이 아닌 상태로 FloatingSettingContent 호스팅
     * - 스위치 꺼짐
     * When:
     * - 상태를 실행 중으로 변경
     * Then:
     * - 스위치 켜짐
     */
    @Test
    fun test_02_switchFollowsRunningState() {
        /** Given **/
        contentUiState.value = FloatingSettingUiState(running = false)
        hostFloatingSettingContent()
        assertTagMatches(FloatingSettingTestTags.RUNNING_SWITCH, isOff())

        /** When **/
        composeRule.runOnIdle { contentUiState.value = contentUiState.value.copy(running = true) }

        /** Then **/
        assertTagMatches(FloatingSettingTestTags.RUNNING_SWITCH, isOn())
    }

    /**
     * 권한 없음·접근성 서비스 꺼짐 상태에서 상태 행이 조치 필요로 표시되고 누르면 조치 화면 이동을 요청하는지 검증
     *
     * Given:
     * - 다른 앱 위에 표시 권한 없음, 접근성 서비스 꺼짐 상태로 FloatingSettingContent 호스팅
     * When:
     * - 권한 상태 행 클릭 → 접근성 서비스 상태 행 클릭
     * Then:
     * - overlay_permission_status에 "다른 앱 위에 표시", "허용 필요", 클릭 시 권한 안내 콜백 1회
     * - accessibility_status에 "접근성 서비스", "꺼짐", 클릭 시 접근성 설정 콜백 1회
     */
    @Test
    fun test_03_statusRowsShowRequiredStates() {
        /** Given **/
        var overlayPermissionClickCount = 0
        var accessibilityClickCount = 0
        contentUiState.value = FloatingSettingUiState(
            overlayPermissionGranted = false,
            accessibilityServiceEnabled = false,
        )
        hostFloatingSettingContent(
            onClickOverlayPermission = { overlayPermissionClickCount++ },
            onClickAccessibility = { accessibilityClickCount++ },
        )

        /** When **/
        clickTag(FloatingSettingTestTags.OVERLAY_PERMISSION_STATUS, scrollTo = true)
        clickTag(FloatingSettingTestTags.ACCESSIBILITY_STATUS, scrollTo = true)

        /** Then **/
        assertTagTextContains(
            FloatingSettingTestTags.OVERLAY_PERMISSION_STATUS,
            context.getString(R.string.floating_setting_status_overlay),
            context.getString(R.string.floating_setting_status_required),
            scrollTo = true,
        )
        assertTagTextContains(
            FloatingSettingTestTags.ACCESSIBILITY_STATUS,
            context.getString(R.string.floating_setting_status_accessibility),
            context.getString(R.string.floating_setting_status_disabled),
            scrollTo = true,
        )
        assertEquals(1, overlayPermissionClickCount)
        assertEquals(1, accessibilityClickCount)
    }

    /**
     * 권한 허용·접근성 서비스 켜짐 상태에서 상태 행이 허용·켜짐으로 표시되고 누를 수 없는지 검증
     *
     * Given:
     * - 다른 앱 위에 표시 권한 허용, 접근성 서비스 켜짐 상태로 FloatingSettingContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - overlay_permission_status에 "다른 앱 위에 표시", "허용됨", 클릭 동작 없음
     * - accessibility_status에 "접근성 서비스", "켜짐", 클릭 동작 없음
     */
    @Test
    fun test_04_statusRowsShowAllowedStates() {
        /** Given **/
        contentUiState.value = FloatingSettingUiState(
            overlayPermissionGranted = true,
            accessibilityServiceEnabled = true,
        )
        hostFloatingSettingContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagTextContains(
            FloatingSettingTestTags.OVERLAY_PERMISSION_STATUS,
            context.getString(R.string.floating_setting_status_overlay),
            context.getString(R.string.floating_setting_status_allowed),
            scrollTo = true,
        )
        assertTagMatches(FloatingSettingTestTags.OVERLAY_PERMISSION_STATUS, !hasClickAction())
        assertTagTextContains(
            FloatingSettingTestTags.ACCESSIBILITY_STATUS,
            context.getString(R.string.floating_setting_status_accessibility),
            context.getString(R.string.floating_setting_status_enabled),
            scrollTo = true,
        )
        assertTagMatches(FloatingSettingTestTags.ACCESSIBILITY_STATUS, !hasClickAction())
    }

    /**
     * 스위치를 누를 때마다 플로팅 버튼 서비스가 켜지고 꺼지며 스위치가 그 상태를 따르는지 검증
     *
     * 권한이 없으면 서비스 대신 권한 안내 다이얼로그가 뜨므로 테스트가 권한을 직접 허용(테스트 후 BaseUiTest가 테스트 전 모드로 복원)
     *
     * Given:
     * - 다른 앱 위에 표시 권한 허용, FloatingSettingScreen 호스팅
     * When:
     * - 스위치 클릭 → 다시 클릭
     * Then:
     * - 처음 클릭 후 실행 여부와 스위치가 반대로 바뀜
     * - 다시 클릭 후 처음 상태로 돌아옴
     */
    @Test
    fun test_05_switchStartsAndStopsService() = runBlocking<Unit> {
        /** Given **/
        setAppOpMode("SYSTEM_ALERT_WINDOW", "allow")
        val initialRunning = originalRunning
        hostFloatingSettingScreen()
        waitUntilTagMatches(FloatingSettingTestTags.RUNNING_SWITCH) { if (initialRunning) isOn() else isOff() }

        /** When / Then **/
        clickTag(FloatingSettingTestTags.RUNNING_SWITCH)
        waitUntilRunning(!initialRunning)
        waitUntilTagMatches(FloatingSettingTestTags.RUNNING_SWITCH) { if (initialRunning) isOff() else isOn() }

        clickTag(FloatingSettingTestTags.RUNNING_SWITCH)
        waitUntilRunning(initialRunning)
        waitUntilTagMatches(FloatingSettingTestTags.RUNNING_SWITCH) { if (initialRunning) isOn() else isOff() }
    }

    /** 플로팅 버튼 서비스 실행 여부가 [running]이 될 때까지 대기, 시간 안에 바뀌지 않으면 실패 */
    private suspend fun waitUntilRunning(running: Boolean) {
        assertTrue(
            "플로팅 버튼 서비스 실행 여부가 ${DEFAULT_TIMEOUT_MILLIS}ms 안에 ${running}(으)로 바뀌지 않았습니다",
            waitUntilFlowValue(FloatingForegroundService.running) { it == running },
        )
    }

    /**
     * 상단 바 뒤로 가기 버튼을 누르면 onNavigateUp이 호출되는지 검증
     *
     * Given:
     * - FloatingSettingScreen 호스팅
     * When:
     * - 뒤로 가기 버튼 클릭
     * Then:
     * - onNavigateUp 1회 호출
     */
    @Test
    fun test_06_clickNavigateUpCallsCallback() {
        /** Given **/
        var navigateUpCount = 0
        hostFloatingSettingScreen(onNavigateUp = { navigateUpCount++ })

        /** When **/
        clickTag(ToolTopBarTestTags.NAVIGATE_UP_BUTTON)

        /** Then **/
        assertEquals(1, navigateUpCount)
    }

    /**
     * 다른 앱 위에 표시 권한이 없을 때 권한 상태 행을 누르면 플로팅 버튼 안내 문장의 권한 안내 다이얼로그가 표시되는지 검증
     *
     * 권한 상태를 고정하도록 테스트가 권한을 직접 거부(테스트 후 BaseUiTest가 테스트 전 모드로 복원).
     * 스위치는 실행 중이면 서비스를 멈추므로 누르지 않고 상태 행으로 다이얼로그를 띄움
     *
     * Given:
     * - 다른 앱 위에 표시 권한 거부, FloatingSettingScreen 호스팅
     * When:
     * - overlay_permission_status 클릭
     * Then:
     * - overlay_permission_dialog에 플로팅 버튼 안내 문장 표시
     */
    @Test
    fun test_07_clickOverlayPermissionStatusShowsGuideDialog() {
        /** Given **/
        setAppOpMode("SYSTEM_ALERT_WINDOW", "deny")
        hostFloatingSettingScreen()

        /** When **/
        clickTag(FloatingSettingTestTags.OVERLAY_PERMISSION_STATUS, scrollTo = true)

        /** Then **/
        assertTagDisplayed(OverlayPermissionDialogTestTags.DIALOG)
        assertTextDisplayed(context.getString(R.string.floating_setting_overlay_permission_guide))
    }
}
