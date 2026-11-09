package com.black.feature.floatingbutton.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import com.black.app.testutil.BaseUiTest
import com.black.core.ui.component.ToolTopBarTestTags
import com.black.core.ui.theme.BlackTheme
import com.black.core.util.PermissionHelper
import com.black.feature.floatingbutton.R
import com.black.feature.floatingbutton.service.FloatingAccessibilityService
import com.black.feature.floatingbutton.service.FloatingButtonController
import com.black.feature.floatingbutton.service.FloatingForegroundService
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [FloatingSettingScreen]이 플로팅 버튼 실행 상태와 권한 상태를 표시하고 스위치로 켜고 끄는지 검증하는 Compose UI 테스트
 *
 * 테스트 기기가 사용자 실사용 기기라 테스트 전 플로팅 버튼 실행 여부를 보관했다가 테스트 후 되돌림.
 * 권한 안내 다이얼로그·설정 화면 이동은 기기 권한 상태에 따라 갈려 ViewModel 유닛 테스트로 검증
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FloatingSettingScreenTest : BaseUiTest() {

    private val controller = FloatingButtonController(context)

    /** 테스트 전 사용자의 플로팅 버튼 실행 여부 */
    private var originalRunning = false

    /** 사용자의 플로팅 버튼 실행 여부 보관 */
    override fun setup() {
        originalRunning = FloatingForegroundService.running.value
    }

    /** 테스트 중 바뀐 플로팅 버튼 실행 여부 복원 */
    override fun teardown() {
        if (FloatingForegroundService.running.value == originalRunning) return
        if (originalRunning) controller.start() else controller.stop()
        waitUntilRunning(originalRunning)
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

    /**
     * 제목이 표시되고 스위치가 플로팅 버튼 실행 여부를 따르는지 검증
     *
     * Given:
     * - 기기의 플로팅 버튼 실행 여부
     * When:
     * - FloatingSettingScreen 호스팅
     * Then:
     * - 제목 "플로팅 버튼" 표시
     * - 스위치가 실행 여부와 같은 상태
     */
    @Test
    fun test_01_displaysTitleAndRunningSwitch() {
        /** Given **/
        val running = FloatingForegroundService.running.value

        /** When **/
        hostFloatingSettingScreen()

        /** Then **/
        assertTagDisplayed(FloatingSettingTestTags.TITLE)
        assertTagText(FloatingSettingTestTags.TITLE, context.getString(R.string.fragment_name_floating))
        waitUntilTagMatches(FloatingSettingTestTags.RUNNING_SWITCH) { if (running) isOn() else isOff() }
    }

    /**
     * 상태 행이 기기의 다른 앱 위에 표시 권한과 접근성 서비스 상태를 표시하는지 검증
     *
     * Given:
     * - 기기의 다른 앱 위에 표시 권한, 플로팅 버튼 접근성 서비스 사용 여부
     * When:
     * - FloatingSettingScreen 호스팅
     * Then:
     * - 각 상태 행이 기기 상태와 같은 값 표시
     */
    @Test
    fun test_02_statusRowsMatchDevice() {
        /** Given **/
        val overlayValue = if (PermissionHelper.canDrawOverlays(context)) {
            context.getString(R.string.floating_setting_status_allowed)
        } else {
            context.getString(R.string.floating_setting_status_required)
        }
        val accessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(
            context,
            FloatingAccessibilityService::class.java,
        )
        val accessibilityValue = if (accessibilityEnabled) {
            context.getString(R.string.floating_setting_status_enabled)
        } else {
            context.getString(R.string.floating_setting_status_disabled)
        }

        /** When **/
        hostFloatingSettingScreen()

        /** Then **/
        assertTagTextContains(FloatingSettingTestTags.OVERLAY_PERMISSION_STATUS, overlayValue, scrollTo = true)
        assertTagTextContains(FloatingSettingTestTags.ACCESSIBILITY_STATUS, accessibilityValue, scrollTo = true)
    }

    /**
     * 스위치를 누를 때마다 플로팅 버튼 서비스가 켜지고 꺼지며 스위치가 그 상태를 따르는지 검증
     *
     * 권한이 없으면 서비스 대신 권한 안내 다이얼로그가 떠 건너뜀
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
    fun test_03_switchStartsAndStopsService() {
        /** Given **/
        assumeTrue(PermissionHelper.canDrawOverlays(context))
        hostFloatingSettingScreen()
        waitUntilTagMatches(FloatingSettingTestTags.RUNNING_SWITCH) { if (originalRunning) isOn() else isOff() }

        /** When / Then **/
        clickTag(FloatingSettingTestTags.RUNNING_SWITCH)
        waitUntilRunning(!originalRunning)
        waitUntilTagMatches(FloatingSettingTestTags.RUNNING_SWITCH) { if (originalRunning) isOff() else isOn() }

        clickTag(FloatingSettingTestTags.RUNNING_SWITCH)
        waitUntilRunning(originalRunning)
        waitUntilTagMatches(FloatingSettingTestTags.RUNNING_SWITCH) { if (originalRunning) isOn() else isOff() }
    }

    /** 플로팅 버튼 서비스 실행 여부가 [running]이 될 때까지 대기, 시간 안에 바뀌지 않으면 실패 */
    private fun waitUntilRunning(running: Boolean) {
        runBlocking {
            withTimeout(DEFAULT_TIMEOUT_MILLIS) { FloatingForegroundService.running.first { it == running } }
        }
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
    fun test_04_clickNavigateUpCallsCallback() {
        /** Given **/
        var navigateUpCount = 0
        hostFloatingSettingScreen(onNavigateUp = { navigateUpCount++ })

        /** When **/
        clickTag(ToolTopBarTestTags.NAVIGATE_UP_BUTTON)

        /** Then **/
        assertEquals(1, navigateUpCount)
    }
}
