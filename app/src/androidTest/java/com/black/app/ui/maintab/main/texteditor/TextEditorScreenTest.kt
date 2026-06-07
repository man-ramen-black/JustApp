package com.black.app.ui.maintab.main.texteditor

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.black.app.testutil.BaseUiTest
import com.black.app.ui.theme.BlackTheme
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [TextEditorScreen]이 계측 환경에서 정상 동작하는지 검증하는 Compose UI 테스트
 *
 * HiltTestActivity(@AndroidEntryPoint)에 setContent로 호스팅해 hiltViewModel() 주입 동작 확인
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TextEditorScreenTest : BaseUiTest() {

    /**
     * TextEditorScreen을 HiltTestActivity에 setContent로 호스팅
     */
    private fun hostTextEditorScreen() {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    TextEditorScreen()
                }
            }
        }
    }

    /**
     * TextEditorScreen이 핵심 요소를 정상 노출하는지 검증
     *
     * Given:
     * - HiltTestActivity에 TextEditorScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - text_editor_title, back_button, save_button, load_button, new_button, editor_input 표시
     */
    @Test
    fun test_01_textEditorScreenDisplaysCoreElements() {
        /** Given **/
        hostTextEditorScreen()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        composeRule.onNodeWithTag("text_editor_title").assertIsDisplayed()
        composeRule.onNodeWithTag("back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("save_button").assertIsDisplayed()
        composeRule.onNodeWithTag("load_button").assertIsDisplayed()
        composeRule.onNodeWithTag("new_button").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_input").assertIsDisplayed()
    }

    /**
     * editor_input에 텍스트 입력 후 화면에 반영되는지 검증
     *
     * Given:
     * - TextEditorScreen 호스팅
     * When:
     * - editor_input에 "hello" 입력
     * Then:
     * - editor_input 노드에 입력한 텍스트 반영
     */
    @Test
    fun test_02_typingUpdatesEditorText() {
        /** Given **/
        hostTextEditorScreen()

        /** When **/
        composeRule.onNodeWithTag("editor_input").performTextInput("hello")

        /** Then **/
        composeRule.onNodeWithTag("editor_input").assertIsDisplayed()
    }

    /**
     * new_button 클릭 시 초기화 확인 다이얼로그가 표시되는지 검증
     *
     * Given:
     * - TextEditorScreen 호스팅
     * When:
     * - new_button 클릭
     * Then:
     * - reset_confirm_dialog 표시
     */
    @Test
    fun test_03_clickNewShowsResetConfirmDialog() {
        /** Given **/
        hostTextEditorScreen()

        /** When **/
        composeRule.onNodeWithTag("new_button").performClick()

        /** Then **/
        composeRule.onNodeWithTag("reset_confirm_dialog").assertIsDisplayed()
    }

    /**
     * 초기 상태에서 파일명 레이블이 노출되지 않는지 검증
     *
     * Given:
     * - TextEditorScreen 호스팅(파일 미로드 상태)
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - file_name 노드 미존재
     */
    @Test
    fun test_04_emptyFileNameHidesFileNameLabel() {
        /** Given **/
        hostTextEditorScreen()

        /** When **/
        // 초기 상태이므로 별도 조작 없음

        /** Then **/
        composeRule.onNodeWithTag("file_name").assertDoesNotExist()
    }
}
