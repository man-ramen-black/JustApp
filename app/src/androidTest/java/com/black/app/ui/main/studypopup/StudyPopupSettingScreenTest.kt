package com.black.app.ui.main.studypopup

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.black.app.R
import com.black.app.model.datastore.StudyPopupDataStore
import com.black.app.testutil.BaseUiTest
import com.black.core.ui.component.OverlayPermissionDialogTestTags
import com.black.core.ui.component.ToolTopBarTestTags
import com.black.core.ui.theme.BlackTheme
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [StudyPopupSettingScreen]이 설정·상태·내용 편집을 표시하고 처리하는지 검증하는 Compose UI 테스트
 *
 * 상태 행은 기기의 권한·서비스 상태에 따라 한 분기만 실행되지 않도록 [StudyPopupSettingContent]에
 * 테스트가 고른 상태를 주입해 분기마다 고정된 기대값으로 검증.
 * 테스트 기기가 사용자 실사용 기기라 화면 켜짐 팝업 표시 설정은 테스트 전 값을 보관했다가 테스트 후 되돌리고,
 * 내용 편집은 파일·팝업 DB에 저장하지 않는 범위(추가·삭제 후 저장 안 함)에서만 검증.
 * 파일 선택창·권한 설정 화면 이동은 ViewModel 유닛 테스트로 검증
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StudyPopupSettingScreenTest : BaseUiTest() {

    private val dataStore = StudyPopupDataStore(context)

    /** 테스트 전 사용자의 화면 켜짐 팝업 표시 설정, setup에서 읽기 전에는 null */
    private var originalEnabled: Boolean? = null

    /** 사용자 설정 보관 후 표시 설정을 꺼짐으로 초기화 */
    override fun setup() {
        runBlocking {
            originalEnabled = dataStore.getEnabled()
            dataStore.updateEnabled(false)
        }
    }

    /** 사용자 설정 복원, setup이 읽기 전에 실패했으면 덮어쓰지 않음 */
    override fun teardown() {
        originalEnabled?.let { runBlocking { dataStore.updateEnabled(it) } }
    }

    /** StudyPopupSettingScreen을 HiltTestActivity에 setContent로 호스팅 */
    private fun hostStudyPopupSettingScreen(onNavigateUp: () -> Unit = {}) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    StudyPopupSettingScreen(onNavigateUp = onNavigateUp)
                }
            }
        }
    }

    /** 테스트가 고른 [uiState]를 주입한 StudyPopupSettingContent를 HiltTestActivity에 setContent로 호스팅 */
    private fun hostStudyPopupSettingContent(
        uiState: StudyPopupSettingUiState,
        onClickOverlayPermission: () -> Unit = {},
        onClickServiceStatus: () -> Unit = {},
    ) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    StudyPopupSettingContent(
                        uiState = uiState,
                        onNavigateUp = {},
                        onEnabledChange = {},
                        onClickPreview = {},
                        onClickOverlayPermission = onClickOverlayPermission,
                        onClickServiceStatus = onClickServiceStatus,
                        onClickLoad = {},
                        onClickReload = {},
                        onClickSave = {},
                        onClickContents = {},
                        onClickDelete = {},
                        onClickAdd = {},
                    )
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
     * 권한 없음·서비스 중지 상태에서 상태 행이 조치 필요로 표시되고 누르면 조치 화면 이동을 요청하는지 검증
     *
     * Given:
     * - 다른 앱 위에 표시 권한 없음, 서비스 중지, 팝업에 저장된 내용 0개 상태로 StudyPopupSettingContent 호스팅
     * When:
     * - 권한 상태 행 클릭 → 서비스 상태 행 클릭
     * Then:
     * - overlay_permission_status에 "다른 앱 위에 표시", "허용 필요", 클릭 시 권한 안내 콜백 1회
     * - service_status에 "백그라운드 서비스", "중지됨", 클릭 시 앱 설치 알림 화면 이동 콜백 1회
     * - saved_contents_status에 "팝업에 저장된 내용", "0개"
     */
    @Test
    fun test_02_statusRowsShowRequiredStates() {
        /** Given **/
        var overlayPermissionClickCount = 0
        var serviceStatusClickCount = 0
        hostStudyPopupSettingContent(
            uiState = StudyPopupSettingUiState(
                overlayPermissionGranted = false,
                serviceRunning = false,
                savedContentsCount = 0,
            ),
            onClickOverlayPermission = { overlayPermissionClickCount++ },
            onClickServiceStatus = { serviceStatusClickCount++ },
        )

        /** When **/
        clickTag(StudyPopupSettingTestTags.OVERLAY_PERMISSION_STATUS, scrollTo = true)
        clickTag(StudyPopupSettingTestTags.SERVICE_STATUS, scrollTo = true)

        /** Then **/
        assertTagTextContains(
            StudyPopupSettingTestTags.OVERLAY_PERMISSION_STATUS,
            context.getString(R.string.study_popup_status_overlay),
            context.getString(R.string.study_popup_status_required),
            scrollTo = true,
        )
        assertTagTextContains(
            StudyPopupSettingTestTags.SERVICE_STATUS,
            context.getString(R.string.study_popup_status_service),
            context.getString(R.string.study_popup_status_stopped),
            scrollTo = true,
        )
        assertTagTextContains(
            StudyPopupSettingTestTags.SAVED_CONTENTS_STATUS,
            context.getString(R.string.study_popup_status_saved_contents),
            context.getString(R.string.study_popup_saved_contents_count, 0),
            scrollTo = true,
        )
        assertEquals(1, overlayPermissionClickCount)
        assertEquals(1, serviceStatusClickCount)
    }

    /**
     * 권한 허용·서비스 실행 상태에서 상태 행이 허용·실행 중으로 표시되고 누를 수 없는지 검증
     *
     * Given:
     * - 다른 앱 위에 표시 권한 허용, 서비스 실행 중, 팝업에 저장된 내용 3개 상태로 StudyPopupSettingContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - overlay_permission_status에 "다른 앱 위에 표시", "허용됨", 클릭 동작 없음
     * - service_status에 "백그라운드 서비스", "실행 중", 클릭 동작 없음
     * - saved_contents_status에 "팝업에 저장된 내용", "3개"
     */
    @Test
    fun test_03_statusRowsShowAllowedStates() {
        /** Given **/
        hostStudyPopupSettingContent(
            uiState = StudyPopupSettingUiState(
                overlayPermissionGranted = true,
                serviceRunning = true,
                savedContentsCount = 3,
            ),
        )

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagTextContains(
            StudyPopupSettingTestTags.OVERLAY_PERMISSION_STATUS,
            context.getString(R.string.study_popup_status_overlay),
            context.getString(R.string.study_popup_status_allowed),
            scrollTo = true,
        )
        assertTagMatches(StudyPopupSettingTestTags.OVERLAY_PERMISSION_STATUS, !hasClickAction())
        assertTagTextContains(
            StudyPopupSettingTestTags.SERVICE_STATUS,
            context.getString(R.string.study_popup_status_service),
            context.getString(R.string.study_popup_status_running),
            scrollTo = true,
        )
        assertTagMatches(StudyPopupSettingTestTags.SERVICE_STATUS, !hasClickAction())
        assertTagTextContains(
            StudyPopupSettingTestTags.SAVED_CONTENTS_STATUS,
            context.getString(R.string.study_popup_status_saved_contents),
            context.getString(R.string.study_popup_saved_contents_count, 3),
            scrollTo = true,
        )
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
    fun test_04_switchFollowsAndSavesSetting() {
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
    fun test_05_addContentsThroughDialog() {
        /** Given **/
        hostStudyPopupSettingScreen()

        /** When / Then **/
        clickTag(StudyPopupSettingTestTags.ADD_BUTTON, scrollTo = true)
        waitUntilTagExists(StudyPopupSettingTestTags.EDIT_DIALOG)
        assertTagMatches(StudyPopupSettingTestTags.EDIT_CONFIRM_BUTTON, isNotEnabled())

        inputTagText(StudyPopupSettingTestTags.EDIT_INPUT, ADDED_CONTENTS)
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
    fun test_06_deleteContentsThroughDialog() {
        /** Given **/
        hostStudyPopupSettingScreen()
        clickTag(StudyPopupSettingTestTags.ADD_BUTTON, scrollTo = true)
        waitUntilTagExists(StudyPopupSettingTestTags.EDIT_DIALOG)
        inputTagText(StudyPopupSettingTestTags.EDIT_INPUT, ADDED_CONTENTS)
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
        assertTextNotExists(ADDED_CONTENTS)
    }

    /**
     * 상단 바 뒤로 가기 버튼을 누르면 onNavigateUp이 호출되는지 검증
     *
     * Given:
     * - StudyPopupSettingScreen 호스팅
     * When:
     * - 뒤로 가기 버튼 클릭
     * Then:
     * - onNavigateUp 1회 호출
     */
    @Test
    fun test_07_clickNavigateUpCallsCallback() {
        /** Given **/
        var navigateUpCount = 0
        hostStudyPopupSettingScreen(onNavigateUp = { navigateUpCount++ })

        /** When **/
        clickTag(ToolTopBarTestTags.NAVIGATE_UP_BUTTON)

        /** Then **/
        assertEquals(1, navigateUpCount)
    }

    /**
     * 다른 앱 위에 표시 권한이 없을 때 권한 상태 행을 누르면 스터디 팝업 안내 문장의 권한 안내 다이얼로그가 표시되는지 검증
     *
     * 권한 상태를 고정하도록 테스트가 권한을 직접 거부(테스트 후 BaseUiTest가 테스트 전 모드로 복원)
     *
     * Given:
     * - 다른 앱 위에 표시 권한 거부, StudyPopupSettingScreen 호스팅
     * When:
     * - overlay_permission_status 클릭
     * Then:
     * - overlay_permission_dialog에 스터디 팝업 안내 문장 표시
     */
    @Test
    fun test_08_clickOverlayPermissionStatusShowsGuideDialog() {
        /** Given **/
        setAppOpMode("SYSTEM_ALERT_WINDOW", "deny")
        hostStudyPopupSettingScreen()

        /** When **/
        clickTag(StudyPopupSettingTestTags.OVERLAY_PERMISSION_STATUS, scrollTo = true)

        /** Then **/
        assertTagDisplayed(OverlayPermissionDialogTestTags.DIALOG)
        assertTextDisplayed(context.getString(R.string.study_popup_overlay_permission_guide))
    }

    companion object {
        /** 기기에 저장된 내용과 겹치지 않도록 정한 테스트용 내용 */
        private const val ADDED_CONTENTS = "계측 테스트로 추가한 내용"
    }
}
