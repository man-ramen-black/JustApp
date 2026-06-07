package com.black.app.ui.maintab.main

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.black.app.R
import com.black.app.testutil.BaseUiTest
import com.black.app.ui.theme.BlackTheme
import com.black.feature.pokerogue.R as PokeRogueR
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [MainScreen]이 헤더·기능 리스트·푸터를 설계대로 노출하는지 검증하는 Compose UI 테스트
 *
 * HiltTestActivity에 setContent로 호스팅(ViewModel 없는 순수 Composable이라 임의 itemList 주입)
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MainScreenTest : BaseUiTest() {

    /**
     * 헤더(날짜·타이틀)와 푸터가 정상 노출되는지 검증
     *
     * Given:
     * - 5개 항목으로 MainScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 날짜 텍스트 영역 표시
     * - 타이틀 "JustApp" 표시
     * - 푸터 "5 TOOLS · DARK ALWAYS" 표시
     */
    @Test
    fun test_01_headerAndFooterDisplayed() {
        /** Given **/
        hostMainScreen(defaultItemList())

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        composeRule.onNodeWithTag("main_date").assertIsDisplayed()
        composeRule.onNodeWithText("JustApp").assertIsDisplayed()
        composeRule.onNodeWithText("5 TOOLS · DARK ALWAYS").assertIsDisplayed()
    }

    /**
     * 노출 항목 5개가 모두 표시되는지 검증
     *
     * Given:
     * - 실제 노출 구성과 동일한 이름의 5개 항목으로 MainScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 5개 항목 이름 모두 표시
     */
    @Test
    fun test_02_allItemsDisplayed() {
        /** Given **/
        hostMainScreen(defaultItemList())

        /** When **/
        // 항목은 정적이므로 별도 조작 없이 검증

        /** Then **/
        composeRule.onNodeWithText("앱 사용 타이머").assertIsDisplayed()
        composeRule.onNodeWithText("메모장").assertIsDisplayed()
        composeRule.onNodeWithText("PokéRogue").assertIsDisplayed()
        composeRule.onNodeWithText("플로팅 버튼").assertIsDisplayed()
        composeRule.onNodeWithText("Blackout").assertIsDisplayed()
    }

    /**
     * 항목 row 클릭 시 해당 항목의 onClick이 호출되는지 검증
     *
     * Given:
     * - 클릭 여부를 기록하는 항목으로 MainScreen 호스팅
     * When:
     * - "앱 사용 타이머" row 클릭
     * Then:
     * - 해당 항목 onClick 1회 호출, 다른 항목 onClick 미호출
     */
    @Test
    fun test_03_clickItemInvokesOnClick() {
        /** Given **/
        val clickedNames = mutableListOf<String>()
        val itemList = defaultItemList { clickedNames.add(it) }
        hostMainScreen(itemList)

        /** When **/
        composeRule.onNodeWithText("앱 사용 타이머").performClick()

        /** Then **/
        assertEquals(listOf("앱 사용 타이머"), clickedNames)
    }

    /**
     * 푸터의 항목 수가 리스트 크기에서 파생되는지 검증
     *
     * Given:
     * - 3개 항목으로 MainScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 푸터 "3 TOOLS · DARK ALWAYS" 표시
     */
    @Test
    fun test_04_footerCountDerivedFromListSize() {
        /** Given **/
        hostMainScreen(defaultItemList().take(3))

        /** When **/
        // 푸터는 정적이므로 별도 조작 없이 검증

        /** Then **/
        composeRule.onNodeWithText("3 TOOLS · DARK ALWAYS").assertIsDisplayed()
    }

    /**
     * 실제 노출 구성과 동일한 5개 항목 리스트 생성. [onClick]으로 클릭된 항목 이름 수집
     */
    private fun defaultItemList(onClick: (String) -> Unit = {}): List<MainItem> {
        return listOf(
            "앱 사용 타이머" to R.drawable.ic_timer,
            "메모장" to R.drawable.ic_editor,
            "PokéRogue" to PokeRogueR.drawable.ic_poke_rogue,
            "플로팅 버튼" to R.drawable.ic_floating,
            "Blackout" to R.drawable.ic_invisible,
        ).map { (name, iconResId) ->
            MainItem(name, iconResId) { onClick(name) }
        }
    }

    /**
     * MainScreen을 HiltTestActivity에 setContent로 호스팅
     */
    private fun hostMainScreen(itemList: List<MainItem>) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    MainScreen(itemList)
                }
            }
        }
    }
}
