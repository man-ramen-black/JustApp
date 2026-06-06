package com.black.app.ui.maintab.main.usagetimer

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.black.app.R
import com.black.app.model.preferences.ForegroundServicePreference
import com.black.app.testutil.BaseUiTest
import com.black.app.ui.theme.BlackTheme
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [UsageTimerScreen]이 계측 환경에서 정상 동작하는지 검증하는 Compose UI 테스트
 *
 * HiltTestActivity(@AndroidEntryPoint)에 setContent로 호스팅해 hiltViewModel() 주입 동작 확인
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerScreenTest : BaseUiTest() {

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    /**
     * 각 테스트 전 선택 앱 저장소 초기화
     */
    override fun setup() {
        ForegroundServicePreference(context).putUsageTimerSelectedApps(emptyList())
    }

    /**
     * 각 테스트 후 선택 앱 저장소 초기화(다른 테스트 격리)
     */
    override fun teardown() {
        ForegroundServicePreference(context).putUsageTimerSelectedApps(emptyList())
    }

    /**
     * UsageTimerScreen이 핵심 요소를 정상 노출하는지 검증
     *
     * Given:
     * - HiltTestActivity에 UsageTimerScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 타이틀 "UsageTimer" 표시
     * - 핵심 조작 요소 모두 존재
     * - 일시정지 잔여 시간 0이므로 카운트다운 영역 미존재
     */
    @Test
    fun test_01_usageTimerScreenDisplaysCoreElements() {
        /** Given **/
        hostUsageTimerScreen()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        composeRule.onNodeWithTag("usage_timer_title").assertIsDisplayed()
        composeRule.onNodeWithTag("usage_timer_title").assertTextEquals("UsageTimer")
        composeRule.onNodeWithTag("show_button").assertExists()
        composeRule.onNodeWithTag("pause_duration_input").assertExists()
        composeRule.onNodeWithTag("save_button").assertExists()
        composeRule.onNodeWithTag("pause_button").assertExists()
        composeRule.onNodeWithTag("cancel_button").assertExists()
        composeRule.onNodeWithTag("accessibility_button").assertExists()
        composeRule.onNodeWithTag("select_app_button").assertExists()
        composeRule.onNodeWithTag("pause_info").assertDoesNotExist()
    }

    /**
     * 각 조작 버튼이 설계된 라벨 텍스트로 노출되는지 검증
     *
     * Given:
     * - UsageTimerScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - Show/Save/Pause/Cancel/Accessibility/Select app 라벨 텍스트 존재
     */
    @Test
    fun test_02_buttonsDisplayExpectedLabels() {
        /** Given **/
        hostUsageTimerScreen()

        /** When **/
        // 버튼 라벨은 정적이므로 별도 조작 없이 검증

        /** Then **/
        composeRule.onNodeWithText("Show").assertExists()
        composeRule.onNodeWithText("Save").assertExists()
        composeRule.onNodeWithText("Pause").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists()
        composeRule.onNodeWithText("Accessibility").assertExists()
        composeRule.onNodeWithText("Select app").assertExists()
    }

    /**
     * Select app 버튼을 누르면 앱 선택 다이얼로그가 표시되는지 검증
     *
     * Given:
     * - UsageTimerScreen 호스팅
     * When:
     * - select_app_button 클릭
     * Then:
     * - SelectAppDialogFragment 다이얼로그 윈도우 표시
     */
    @Test
    fun test_03_clickSelectAppShowsSelectAppDialog() {
        /** Given **/
        hostUsageTimerScreen()

        /** When **/
        composeRule.onNodeWithTag("select_app_button").performScrollTo().performClick()

        /** Then **/
        onView(withId(R.id.title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    /**
     * 선택 앱이 저장된 상태로 진입하면 목록에 해당 앱이 표시되는지 검증
     *
     * Given:
     * - 존재하지 않는 packageName을 선택 앱으로 저장(라벨 조회 실패 시 packageName 그대로 표시되어 검증이 결정적)
     * When:
     * - UsageTimerScreen 호스팅
     * Then:
     * - 선택 앱 목록 노출 + 저장한 packageName 라벨 표시
     */
    @Test
    fun test_04_selectedAppsDisplayedFromStorage() {
        /** Given **/
        ForegroundServicePreference(context).putUsageTimerSelectedApps(listOf("com.nonexistent.testapp"))

        /** When **/
        hostUsageTimerScreen()

        /** Then **/
        composeRule.onNodeWithTag("selected_apps_list").assertExists()
        composeRule.onNodeWithText("com.nonexistent.testapp").assertExists()
    }

    /**
     * 선택 앱이 없으면 안내 문구가 표시되고 목록은 노출되지 않는지 검증
     *
     * Given:
     * - 선택 앱 저장소 비어있음(setup에서 초기화)
     * When:
     * - UsageTimerScreen 호스팅
     * Then:
     * - 빈 목록 안내 영역 표시 + 선택 앱 목록 미존재
     */
    @Test
    fun test_05_noAppsSelectedShowsPlaceholder() {
        /** Given **/
        // setup에서 저장소 초기화됨

        /** When **/
        hostUsageTimerScreen()

        /** Then **/
        composeRule.onNodeWithTag("selected_apps_empty").assertExists()
        composeRule.onNodeWithTag("selected_apps_list").assertDoesNotExist()
    }

    /**
     * UsageTimerScreen을 HiltTestActivity에 setContent로 호스팅
     */
    private fun hostUsageTimerScreen() {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    UsageTimerScreen()
                }
            }
        }
    }
}
