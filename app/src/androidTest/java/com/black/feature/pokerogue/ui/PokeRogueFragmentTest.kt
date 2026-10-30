package com.black.feature.pokerogue.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import com.black.app.testutil.BaseUiTest
import com.black.app.testutil.HiltTestActivity
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [PokeRogueFragment]가 Compose NavHost 호스팅(AndroidFragment)에서 이탈할 때 화면 방향을 되돌리고, 뒤로가기를 NavHost보다 먼저 처리하는지 검증하는 계측 테스트
 *
 * 실제 앱과 같이 AndroidFragment로 호스팅하고, 컴포지션에서 빼는 방식으로 뒤로가기 이탈을 재현.
 * AndroidFragment는 폐기 시 saveFragmentInstanceState를 호출한 뒤 Fragment를 제거하므로 상태 저장과 제거가 연달아 발생.
 * HiltTestActivity는 회전 시 재생성되지 않도록 debug 매니페스트에서 configChanges를 처리
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PokeRogueFragmentTest : BaseUiTest() {

    /** PokeRogueFragment를 컴포지션에 둘지 여부 */
    private val isPokeRogueShowing = mutableStateOf(true)

    private lateinit var hostActivity: HiltTestActivity

    /** [hostPokeRogueInNavHost]로 호스팅한 NavHost의 NavController */
    private lateinit var navController: NavHostController

    /**
     * PokeRogueFragment 진입 시 가로 고정되고, 이탈하면 방향 고정이 해제되는지 검증
     *
     * Given:
     * - AndroidFragment로 PokeRogueFragment 호스팅(가로 고정 요청)
     * When:
     * - PokeRogueFragment를 컴포지션에서 제거(뒤로가기 이탈과 같은 AndroidFragment 폐기 경로)
     * Then:
     * - 진입 상태의 요청 방향이 LANDSCAPE
     * - 이탈 후 요청 방향이 USER로 복구
     */
    @Test
    fun test_01_leavingPokeRogueRestoresOrientation() {
        /** Given **/
        hostPokeRogueFragment()
        assertEquals(
            "PokeRogueFragment 진입 후 가로 고정이 요청되지 않았습니다",
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            hostActivity.requestedOrientation,
        )

        /** When **/
        composeRule.runOnIdle { isPokeRogueShowing.value = false }
        composeRule.waitForIdle()

        /** Then **/
        assertEquals(
            "PokeRogueFragment 이탈 후 가로 고정이 해제되지 않았습니다",
            ActivityInfo.SCREEN_ORIENTATION_USER,
            hostActivity.requestedOrientation,
        )
    }

    /**
     * 백그라운드에 갔다 돌아온 뒤에도 뒤로가기를 PokeRogueFragment가 먼저 처리하는지 검증
     *
     * Activity가 다시 시작되면 Fragment 수명주기로 등록한 콜백이 NavHost 뒤로가기 콜백보다 먼저 다시 등록되어 NavHost가 우선함.
     *
     * Given:
     * - NavHost 안에 AndroidFragment로 호스팅한 PokeRogue 화면으로 이동, 매치업 창 열림
     * - Activity를 CREATED로 내렸다가 RESUMED로 복귀(홈 이동 후 복귀)
     * When:
     * - 뒤로가기
     * Then:
     * - 매치업 창 닫힘
     * - PokeRogue 화면 유지
     */
    @Test
    fun test_02_backPressAfterRestartIsHandledByPokeRogue() {
        /** Given **/
        val scenario = launchHost()
        navigateToPokeRogue(scenario)
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        val viewModel = openMatchUp(scenario)

        /** When **/
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        /** Then **/
        assertFalse("뒤로가기로 매치업 창이 닫히지 않았습니다", viewModel.isMatchUpOpen.value)
        assertEquals("뒤로가기 한 번에 PokeRogue 화면을 벗어났습니다", ROUTE_POKE_ROGUE, navController.currentDestination?.route)
    }

    /**
     * Activity가 다시 만들어진 뒤에도 뒤로가기를 PokeRogueFragment가 먼저 처리하는지 검증
     *
     * PokeRogue 진입 시 가로 회전으로 Activity가 다시 만들어지는 상황 재현.
     * 복원된 Fragment는 콜백을 NavHost 컴포지션보다 먼저 등록해 NavHost 뒤로가기 콜백이 우선함.
     *
     * Given:
     * - NavHost 안에 AndroidFragment로 호스팅한 PokeRogue 화면으로 이동
     * - Activity 재생성 후 같은 NavHost 다시 호스팅, 매치업 창 열림
     * When:
     * - 뒤로가기
     * Then:
     * - 매치업 창 닫힘
     * - PokeRogue 화면 유지
     */
    @Test
    fun test_03_backPressAfterRecreationIsHandledByPokeRogue() {
        /** Given **/
        val scenario = launchHost()
        navigateToPokeRogue(scenario)
        scenario.recreate()
        // 재생성된 Activity에 콘텐츠를 다시 설정해 저장된 NavHost 백스택과 복원된 Fragment를 이어받음
        scenario.onActivity { hostPokeRogueInNavHost(it) }
        composeRule.waitForIdle()
        assertEquals("재생성 후 PokeRogue 화면이 복원되지 않았습니다", ROUTE_POKE_ROGUE, navController.currentDestination?.route)
        val viewModel = openMatchUp(scenario)

        /** When **/
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        /** Then **/
        assertFalse("뒤로가기로 매치업 창이 닫히지 않았습니다", viewModel.isMatchUpOpen.value)
        assertEquals("뒤로가기 한 번에 PokeRogue 화면을 벗어났습니다", ROUTE_POKE_ROGUE, navController.currentDestination?.route)
    }

    /** 앱의 AppNavHost와 같이 AndroidFragment로 PokeRogueFragment를 HiltTestActivity에 호스팅 */
    private fun hostPokeRogueFragment() {
        onHostActivity { activity ->
            hostActivity = activity
            activity.setContent {
                if (isPokeRogueShowing.value) {
                    AndroidFragment<PokeRogueFragment>(Modifier.fillMaxSize())
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** 앱의 AppNavHost와 같이 NavHost 안에 AndroidFragment로 PokeRogueFragment를 호스팅 */
    private fun hostPokeRogueInNavHost(activity: HiltTestActivity) {
        activity.setContent {
            val controller = rememberNavController().also { navController = it }
            NavHost(controller, startDestination = ROUTE_START) {
                composable(ROUTE_START) {}
                composable(ROUTE_POKE_ROGUE) { AndroidFragment<PokeRogueFragment>(Modifier.fillMaxSize()) }
            }
        }
    }

    /** NavHost를 호스팅하고 시작 화면에서 PokeRogue 화면으로 이동 */
    private fun navigateToPokeRogue(scenario: ActivityScenario<HiltTestActivity>) {
        scenario.onActivity { hostPokeRogueInNavHost(it) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { navController.navigate(ROUTE_POKE_ROGUE) }
        composeRule.waitForIdle()
    }

    /** 표시 중인 PokeRogueFragment의 매치업 창을 열고 그 ViewModel 반환 */
    private fun openMatchUp(scenario: ActivityScenario<HiltTestActivity>): PokeRogueViewModel {
        lateinit var viewModel: PokeRogueViewModel
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.fragments.filterIsInstance<PokeRogueFragment>().single()
            viewModel = ViewModelProvider(fragment)[PokeRogueViewModel::class.java]
            viewModel.onClickMatchUp()
        }
        composeRule.waitForIdle()
        assertTrue("매치업 창이 열리지 않았습니다", viewModel.isMatchUpOpen.value)
        return viewModel
    }

    companion object {
        private const val ROUTE_START = "start"
        private const val ROUTE_POKE_ROGUE = "pokeRogue"
    }
}
