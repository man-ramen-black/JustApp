package com.black.app.ui.maintab.main.studypopup

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.black.app.R
import com.black.app.model.StudyPopupRepository
import com.black.app.model.datastore.StudyPopupDataStore
import com.black.app.service.ForegroundService
import com.black.app.testutil.BaseUiTest
import com.black.app.ui.theme.BlackTheme
import com.black.core.util.PermissionHelper
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [StudyPopupSettingScreen]이 설정·상태·내용 편집을 표시하고 처리하는지 검증하는 Compose UI 테스트
 *
 * 테스트 기기가 사용자 실사용 기기라 화면 켜짐 팝업 표시 설정은 테스트 전 값을 보관했다가 테스트 후 되돌리고,
 * 내용 편집은 파일·팝업 DB에 저장하지 않는 범위(추가·삭제 후 저장 안 함)에서만 검증.
 * 파일 선택창·권한 설정 화면 이동은 ViewModel 유닛 테스트로 검증
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StudyPopupSettingScreenTest : BaseUiTest() {

    private val dataStore = StudyPopupDataStore(context)

    /** 테스트 전 사용자의 화면 켜짐 팝업 표시 설정 */
    private var originalEnabled = false

    /** 사용자 설정 보관 후 표시 설정을 꺼짐으로 초기화 */
    override fun setup() {
        runBlocking {
            originalEnabled = dataStore.getEnabled()
            dataStore.updateEnabled(false)
        }
    }

    /** 사용자 설정 복원 */
    override fun teardown() {
        runBlocking { dataStore.updateEnabled(originalEnabled) }
    }

    /** StudyPopupSettingScreen을 HiltTestActivity에 setContent로 호스팅 */
    private fun hostStudyPopupSettingScreen() {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    StudyPopupSettingScreen()
                }
            }
        }
    }

    /**
     * 제목·표시 설정 스위치·조작 버튼이 표시되는지 검증
     *
     * Given:
     * - 표시 설정 꺼짐 저장
     * When:
     * - StudyPopupSettingScreen 호스팅
     * Then:
     * - 제목 "스터디 팝업" 표시, 스위치 꺼짐
     * - 미리보기·불러오기·저장·내용 추가 버튼 존재
     */
    @Test
    fun test_01_displaysTitleSwitchAndButtons() {
        /** Given **/
        // setup에서 표시 설정 꺼짐 저장

        /** When **/
        hostStudyPopupSettingScreen()

        /** Then **/
        assertTagDisplayed(StudyPopupSettingTestTags.TITLE)
        assertTagText(StudyPopupSettingTestTags.TITLE, context.getString(R.string.fragment_name_study_popup))
        // 스위치는 DataStore Flow를 따르고 Compose 유휴 대기는 DataStore 읽기를 기다리지 않아 반영까지 대기
        waitUntilTagMatches(StudyPopupSettingTestTags.ENABLED_SWITCH) { isOff() }
        assertTagExists(StudyPopupSettingTestTags.PREVIEW_BUTTON)
        assertTagExists(StudyPopupSettingTestTags.LOAD_BUTTON)
        assertTagExists(StudyPopupSettingTestTags.SAVE_BUTTON)
        assertTagExists(StudyPopupSettingTestTags.ADD_BUTTON)
    }

    /**
     * 상태 행이 기기의 권한·서비스·팝업에 저장된 내용 개수를 표시하는지 검증
     *
     * Given:
     * - 기기의 다른 앱 위에 표시 권한, ForegroundService 실행 여부, 팝업 DB 내용 개수
     * When:
     * - StudyPopupSettingScreen 호스팅
     * Then:
     * - 각 상태 행이 기기 상태와 같은 값 표시
     */
    @Test
    fun test_02_statusRowsMatchDevice() {
        /** Given **/
        val overlayValue = if (PermissionHelper.canDrawOverlays(context)) {
            context.getString(R.string.study_popup_status_allowed)
        } else {
            context.getString(R.string.study_popup_status_required)
        }
        val serviceValue = if (ForegroundService.running.value) {
            context.getString(R.string.study_popup_status_running)
        } else {
            context.getString(R.string.study_popup_status_stopped)
        }
        val savedCount = runBlocking { StudyPopupRepository(context).countContents() }
        val savedValue = context.getString(R.string.study_popup_saved_contents_count, savedCount)

        /** When **/
        hostStudyPopupSettingScreen()

        /** Then **/
        assertTagTextContains(StudyPopupSettingTestTags.OVERLAY_PERMISSION_STATUS, overlayValue, scrollTo = true)
        assertTagTextContains(StudyPopupSettingTestTags.SERVICE_STATUS, serviceValue, scrollTo = true)
        // 내용 개수는 DB 조회가 끝난 뒤 반영되므로 반영까지 대기
        waitUntilTagMatches(StudyPopupSettingTestTags.SAVED_CONTENTS_STATUS) { hasText(savedValue, substring = true) }
    }

    /**
     * 저장된 표시 설정을 스위치가 따르고, 스위치를 누르면 꺼짐으로 저장되는지 검증
     *
     * 켜는 방향은 권한이 없는 기기에서 권한 안내 다이얼로그가 떠 테스트 화면을 가리므로 끄는 방향으로 검증
     *
     * Given:
     * - 표시 설정 켜짐 저장, 스위치 켜짐 반영
     * When:
     * - 스위치 클릭
     * Then:
     * - 스위치 꺼짐 + 저장된 표시 설정 꺼짐
     */
    @Test
    fun test_03_switchFollowsAndSavesSetting() {
        /** Given **/
        runBlocking { dataStore.updateEnabled(true) }
        hostStudyPopupSettingScreen()
        waitUntilTagMatches(StudyPopupSettingTestTags.ENABLED_SWITCH) { isOn() }

        /** When **/
        clickTag(StudyPopupSettingTestTags.ENABLED_SWITCH)

        /** Then **/
        waitUntilTagMatches(StudyPopupSettingTestTags.ENABLED_SWITCH) { isOff() }
        // 저장은 코루틴에서 끝나므로 꺼짐이 저장될 때까지 대기, 시간 안에 저장되지 않으면 실패
        runBlocking {
            withTimeout(DEFAULT_TIMEOUT_MILLIS) { dataStore.getEnabledFlow().first { !it } }
        }
    }

    /**
     * 내용 추가 다이얼로그는 빈 내용을 확인할 수 없고, 입력한 내용은 목록에 추가되는지 검증
     *
     * Given:
     * - StudyPopupSettingScreen 호스팅
     * When:
     * - 내용 추가 클릭 → 빈 상태 확인 버튼 확인 → 내용 입력 후 확인
     * Then:
     * - 빈 상태에서 확인 버튼 비활성
     * - 입력한 내용이 목록에 표시되고 다이얼로그 닫힘
     */
    @Test
    fun test_04_addContentsThroughDialog() {
        /** Given **/
        hostStudyPopupSettingScreen()

        /** When / Then **/
        clickTag(StudyPopupSettingTestTags.ADD_BUTTON, scrollTo = true)
        waitUntilTagExists(StudyPopupSettingTestTags.EDIT_DIALOG)
        composeRule.onNodeWithTag(StudyPopupSettingTestTags.EDIT_CONFIRM_BUTTON).assertIsNotEnabled()

        composeRule.onNodeWithTag(StudyPopupSettingTestTags.EDIT_INPUT).performTextInput(ADDED_CONTENTS)
        clickTag(StudyPopupSettingTestTags.EDIT_CONFIRM_BUTTON)

        assertTagNotExists(StudyPopupSettingTestTags.EDIT_DIALOG)
        assertTextDisplayed(ADDED_CONTENTS, scrollTo = true)
    }

    /**
     * 내용 행의 삭제 버튼을 누르고 확인하면 그 내용이 목록에서 사라지는지 검증
     *
     * Given:
     * - 내용 추가 다이얼로그로 내용 하나 추가
     * When:
     * - 추가한 내용 행의 삭제 버튼 클릭 → 삭제 확인
     * Then:
     * - 추가한 내용이 목록에서 사라짐
     */
    @Test
    fun test_05_deleteContentsThroughDialog() {
        /** Given **/
        hostStudyPopupSettingScreen()
        clickTag(StudyPopupSettingTestTags.ADD_BUTTON, scrollTo = true)
        waitUntilTagExists(StudyPopupSettingTestTags.EDIT_DIALOG)
        composeRule.onNodeWithTag(StudyPopupSettingTestTags.EDIT_INPUT).performTextInput(ADDED_CONTENTS)
        clickTag(StudyPopupSettingTestTags.EDIT_CONFIRM_BUTTON)

        /** When **/
        // 기기에 불러온 내용이 있을 수 있어 위치 대신 추가한 내용 행의 삭제 버튼을 찾음
        // 행이 클릭 가능해 내용 텍스트는 행 노드에 병합되므로 형제가 아니라 부모로 찾음
        val deleteDescription = context.getString(R.string.study_popup_delete)
        composeRule.onNode(hasContentDescription(deleteDescription) and hasParent(hasText(ADDED_CONTENTS)))
            .performScrollTo()
            .performClick()
        waitUntilTagExists(StudyPopupSettingTestTags.DELETE_DIALOG)
        clickTag(StudyPopupSettingTestTags.DELETE_CONFIRM_BUTTON)

        /** Then **/
        assertTagNotExists(StudyPopupSettingTestTags.DELETE_DIALOG)
        composeRule.onNode(hasText(ADDED_CONTENTS)).assertDoesNotExist()
    }

    companion object {
        /** 기기에 저장된 내용과 겹치지 않도록 정한 테스트용 내용 */
        private const val ADDED_CONTENTS = "계측 테스트로 추가한 내용"
    }
}
