package com.black.app.ui.maintab.main.texteditor

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.black.app.model.datastore.TextEditorDataStore
import com.black.app.testutil.BaseUiTest
import com.black.app.ui.theme.BlackTheme
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [TextEditorScreen]이 계측 환경에서 정상 동작하는지 검증하는 Compose UI 테스트
 *
 * HiltTestActivity(@AndroidEntryPoint)에 setContent로 호스팅해 hiltViewModel() 주입 동작 확인.
 * 화면이 쓰는 [TextEditorDataStore]를 테스트 전용 저장소로 바꿔 사용자 최근 문서와 분리.
 * 화면이 닫힐 때(ON_PAUSE) 자동 저장은 BaseUiTest가 teardown 뒤에 scenario를 닫을 때 비동기로 실행되므로,
 * 사용자 최근 문서를 백업·복구하는 방식은 복구가 자동 저장보다 먼저 끝나면 입력 내용이 사용자 문서에 저장됨
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TextEditorScreenTest : BaseUiTest() {

    /** 화면에 주입할 테스트 전용 최근 문서 저장소 */
    @BindValue
    @JvmField
    val textEditorDataStore = TextEditorDataStore(context, context.testTextEditorDataStore)

    /** 최근 문서 없는 상태에서 시작(이전 테스트가 남긴 최근 문서 제거) */
    override fun setup() {
        runBlocking { textEditorDataStore.removeLatestFileUri() }
    }

    /** TextEditorScreen을 HiltTestActivity에 setContent로 호스팅 */
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
     * - text_editor_title, save_button, load_button, new_button, editor_input 표시
     */
    @Test
    fun test_01_textEditorScreenDisplaysCoreElements() {
        /** Given **/
        hostTextEditorScreen()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        composeRule.onNodeWithTag("text_editor_title").assertIsDisplayed()
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

    /**
     * 영구 권한이 없는 문서가 최근 문서로 남아 있을 때 크래시 없이 권한을 요청하고, 다시 선택하면 그 문서를 불러오는지 검증
     *
     * Given:
     * - 앱에 권한이 없는 Download 텍스트 문서를 최근 문서로 저장(재설치로 영구 권한이 사라진 상황)
     * - TextEditorScreen 호스팅
     * When:
     * - 권한 요청 다이얼로그의 문서 선택 클릭 후 문서 선택창에서 같은 문서 선택
     * Then:
     * - 진입 시 크래시 없이 permission_request_dialog 표시
     * - 선택 후 문서 내용과 파일명 표시
     */
    @Test
    fun test_05_permissionDeniedLatestFileRequestsPermissionAndReloads() {
        /** Given **/
        val mediaUri = createDownloadTextFile()
        val documentUri = DocumentsContract.buildDocumentUri(MEDIA_DOCUMENTS_AUTHORITY, "document:${ContentUris.parseId(mediaUri)}")
        try {
            runBlocking { textEditorDataStore.updateLatestFileUri(documentUri) }
            hostTextEditorScreen()
            composeRule.waitUntil(TIMEOUT_MS) {
                composeRule.onAllNodesWithTag("permission_request_dialog").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("permission_request_dialog").assertIsDisplayed()

            /** When **/
            composeRule.onNodeWithTag("select_document_button").performClick()
            // 문서 선택창(시스템 앱)에서 같은 문서 선택
            val fileItem = device.wait(Until.findObject(By.text(TEST_FILE_NAME)), TIMEOUT_MS)
            assertNotNull("문서 선택창에 $TEST_FILE_NAME 항목이 표시되지 않았습니다", fileItem)
            fileItem.click()

            /** Then **/
            composeRule.waitUntil(TIMEOUT_MS) {
                composeRule.onAllNodesWithText(TEST_FILE_CONTENT).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("file_name").assertTextEquals(TEST_FILE_NAME)
        } finally {
            // 임시 문서와 그 영구 권한 정리
            context.contentResolver.persistedUriPermissions
                .filter { it.uri == documentUri }
                .forEach {
                    context.contentResolver.releasePersistableUriPermission(
                        it.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            context.contentResolver.delete(mediaUri, null, null)
        }
    }

    /** MediaStore Download에 [TEST_FILE_NAME] 텍스트 문서 생성 후 media uri 반환 */
    private fun createDownloadTextFile(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, TEST_FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        }
        val mediaUri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        context.contentResolver.openOutputStream(mediaUri)!!.use { it.write(TEST_FILE_CONTENT.toByteArray()) }
        return mediaUri
    }

    companion object {
        /** 사용자 최근 문서("textEditor")와 다른 파일의 테스트 전용 DataStore(프로세스당 하나만 생성) */
        private val Context.testTextEditorDataStore: DataStore<Preferences> by preferencesDataStore(name = "textEditorTest")

        /** 문서 선택창이 media 문서에 발급하는 uri 권한자 */
        private const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"
        private const val TEST_FILE_NAME = "justapp-permission-test.txt"
        private const val TEST_FILE_CONTENT = "permission test"
        private const val TIMEOUT_MS = 10_000L
    }
}
