package com.black.app.ui.maintab.main.usagetimer

import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelStore
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.black.app.R
import com.black.app.model.preferences.ForegroundServicePreference
import com.black.app.testutil.BaseUiTest
import com.black.app.testutil.assertDisplayed
import com.black.app.testutil.assertEffectiveGone
import com.black.app.testutil.assertEffectiveVisible
import com.black.app.testutil.assertText
import com.black.app.testutil.clickView
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [UsageTimerFragment]가 실제 기기(계측 환경)에서 정상 동작하는지 검증하는 Espresso UI 테스트
 *
 * @AndroidEntryPoint Fragment이므로 [BaseUiTest.onHostActivity]가 띄우는 HiltTestActivity로 호스팅하고,
 * onViewCreated에서 findNavController()를 사용하므로 컨테이너에 [TestNavHostController] 미리 부착
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerFragmentTest : BaseUiTest() {

    private lateinit var navController: TestNavHostController

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
     * UsageTimerFragment가 계측 환경에서 핵심 View들을 정상 노출하는지 검증
     *
     * Given:
     * - HiltTestActivity에 NavController 부착 후 UsageTimerFragment 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 타이틀 "UsageTimer" 표시
     * - 핵심 조작 View 모두 노출
     * - 일시정지 잔여 시간 0이므로 일시정지 정보 영역 숨김
     */
    @Test
    fun test_01_usageTimerFragmentDisplaysCoreViews() {
        /** Given **/
        hostUsageTimerFragment()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        // 화면 렌더링 확인: 타이틀 표시 + 텍스트 일치
        assertDisplayed(R.id.title)
        assertText(R.id.title, "UsageTimer")

        // 핵심 조작 View 노출(VISIBLE) 상태 검증 (스크롤 위치와 무관하게 검증하려고 effectiveVisibility 사용)
        assertEffectiveVisible(R.id.show)
        assertEffectiveVisible(R.id.pause_input)
        assertEffectiveVisible(R.id.save_button)
        assertEffectiveVisible(R.id.pause_button)
        assertEffectiveVisible(R.id.cancel_button)
        assertEffectiveVisible(R.id.accessibility_button)
        assertEffectiveVisible(R.id.select_app_button)

        // 일시정지 잔여 시간 0이므로 일시정지 정보 영역 숨김
        assertEffectiveGone(R.id.pause_info)
    }

    /**
     * 각 조작 버튼이 설계된 라벨 텍스트로 노출되는지 검증
     *
     * Given:
     * - UsageTimerFragment 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - show/save/pause/cancel/accessibility/select app 버튼이 각각 정해진 라벨 텍스트 표시
     */
    @Test
    fun test_02_buttonsDisplayExpectedLabels() {
        /** Given **/
        hostUsageTimerFragment()

        /** When **/
        // 버튼 라벨은 정적이므로 별도 조작 없이 검증

        /** Then **/
        assertText(R.id.show, "Show")
        assertText(R.id.save_button, "Save")
        assertText(R.id.pause_button, "Pause")
        assertText(R.id.cancel_button, "Cancel")
        assertText(R.id.accessibility_button, "Accessibility")
        assertText(R.id.select_app_button, "Select app")
    }

    /**
     * Select app 버튼을 누르면 앱 선택 다이얼로그로 네비게이션되는지 검증
     *
     * Given:
     * - UsageTimerFragment 호스팅
     * - 현재 목적지는 usage_timer_fragment
     * When:
     * - select_app_button 클릭
     * Then:
     * - NavController의 현재 목적지가 select_app_dialog로 이동
     */
    @Test
    fun test_03_clickSelectAppNavigatesToSelectAppDialog() {
        /** Given **/
        hostUsageTimerFragment()

        /** When **/
        // 버튼이 화면 하단에 있을 수 있어 보이도록 스크롤 후 클릭
        clickView(R.id.select_app_button, scrollTo = true)

        /** Then **/
        assertEquals(
            "select_app_button 클릭 후 select_app_dialog로 네비게이션되지 않았습니다",
            R.id.select_app_dialog,
            navController.currentDestination?.id,
        )
    }

    /**
     * 선택 앱이 저장된 상태로 진입하면 아이콘 목록에 해당 앱이 표시되는지 검증
     *
     * Given:
     * - 존재하지 않는 packageName을 선택 앱으로 저장(라벨 조회 실패 시 packageName 그대로 표시되어 검증이 결정적)
     * When:
     * - UsageTimerFragment 호스팅
     * Then:
     * - 선택 앱 RecyclerView 노출 + 저장한 packageName 라벨 표시
     */
    @Test
    fun test_04_selectedAppsDisplayedFromStorage() {
        /** Given **/
        ForegroundServicePreference(context).putUsageTimerSelectedApps(listOf("com.nonexistent.testapp"))

        /** When **/
        hostUsageTimerFragment()

        /** Then **/
        assertDisplayed(R.id.selected_apps_recycler)
        // 라벨 조회 실패 시 packageName이 그대로 라벨로 노출되므로 결정적으로 검증
        onView(withText("com.nonexistent.testapp")).check(matches(isDisplayed()))
    }

    /**
     * 선택 앱이 없으면 안내 문구가 표시되고 목록은 숨겨지는지 검증
     *
     * Given:
     * - 선택 앱 저장소 비어있음(setup에서 초기화)
     * When:
     * - UsageTimerFragment 호스팅
     * Then:
     * - 안내 문구 "No apps selected" 표시 + 선택 앱 RecyclerView 숨김
     */
    @Test
    fun test_05_noAppsSelectedShowsPlaceholder() {
        /** Given **/
        // setup에서 저장소 초기화됨

        /** When **/
        hostUsageTimerFragment()

        /** Then **/
        assertText(R.id.selected_apps_empty, "No apps selected")
        assertEffectiveGone(R.id.selected_apps_recycler)
    }

    /**
     * UsageTimerFragment를 HiltTestActivity에 호스팅하고 [navController] 부착
     *
     * Fragment가 onViewCreated에서 findNavController()를 사용하므로, View 생성 전에 컨테이너에 NavController 부착 필요
     */
    private fun hostUsageTimerFragment() {
        onHostActivity { activity ->
            navController = TestNavHostController(activity).apply {
                // savedStateHandle(popBackStack 결과 observe)에 접근하려면 ViewModelStore가 필요
                setViewModelStore(ViewModelStore())
                setGraph(R.navigation.root_navigation)
                setCurrentDestination(R.id.usage_timer_fragment)
            }
            val container = FrameLayout(activity).apply { id = View.generateViewId() }
            activity.setContentView(container)
            Navigation.setViewNavController(container, navController)

            activity.supportFragmentManager.beginTransaction()
                .add(container.id, UsageTimerFragment(), "usageTimer")
                .commitNow()
        }
    }
}
