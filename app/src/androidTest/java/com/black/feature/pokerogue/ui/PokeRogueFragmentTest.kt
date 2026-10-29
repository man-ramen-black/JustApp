package com.black.feature.pokerogue.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import com.black.app.testutil.BaseUiTest
import com.black.app.testutil.HiltTestActivity
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [PokeRogueFragment]가 Compose NavHost 호스팅(AndroidFragment)에서 이탈할 때 화면 방향을 되돌리는지 검증하는 계측 테스트
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
}
