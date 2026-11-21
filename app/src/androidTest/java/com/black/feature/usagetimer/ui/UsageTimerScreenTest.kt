package com.black.feature.usagetimer.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.test.espresso.matcher.RootMatchers.isDialog
import com.black.app.testutil.BaseUiTest
import com.black.app.testutil.assertDisplayed
import com.black.core.model.OverlayPosition.HorizontalAnchor
import com.black.core.model.OverlayPosition.VerticalAnchor
import com.black.core.ui.component.OverlayPermissionDialogTestTags
import com.black.core.ui.component.OverlayPositionInput
import com.black.core.ui.component.OverlayPositionTestTags
import com.black.core.ui.component.ToolTopBarTestTags
import com.black.core.ui.selectapp.SelectedAppUiItem
import com.black.core.ui.theme.BlackTheme
import com.black.core.util.PermissionHelper
import com.black.feature.usagetimer.R
import com.black.feature.usagetimer.data.UsageTimerRepository
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [UsageTimerScreen]이 계측 환경에서 정상 동작하는지 검증하는 Compose UI 테스트
 *
 * HiltTestActivity(@AndroidEntryPoint)에 setContent로 호스팅해 hiltViewModel() 주입 동작 확인.
 * 선택 앱은 실제 앱 저장소를 쓰므로 테스트 전 값을 보관했다가 테스트 후 되돌림.
 * 권한 안내 다이얼로그는 테스트가 다른 앱 위에 표시 권한을 거부해 검증하고, 설정 화면 이동은 ViewModel 유닛 테스트로 검증
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerScreenTest : BaseUiTest() {

    private val repository = UsageTimerRepository(context)

    /** 사용자 선택 앱을 테스트 후 복원하도록 보관(실사용 기기의 설정 유지)한 뒤 선택 앱 저장소 초기화 */
    override fun setup() {
        preserveValue(read = { repository.getSelectedApps() }, restore = { repository.saveSelectedApps(it) })
        runBlocking { repository.saveSelectedApps(emptyList()) }
    }

    /**
     * UsageTimerScreen이 핵심 요소를 정상 노출하는지 검증
     *
     * Given:
     * - HiltTestActivity에 UsageTimerScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 타이틀 "앱 사용 타이머" 표시
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
        assertTagDisplayed(UsageTimerTestTags.TITLE)
        assertTagText(UsageTimerTestTags.TITLE, "앱 사용 타이머")
        assertTagExists(UsageTimerTestTags.SHOW_BUTTON)
        assertTagExists(UsageTimerTestTags.PAUSE_DURATION_INPUT)
        assertTagExists(UsageTimerTestTags.SAVE_BUTTON)
        assertTagExists(UsageTimerTestTags.PAUSE_BUTTON)
        assertTagExists(UsageTimerTestTags.CANCEL_BUTTON)
        assertTagExists(UsageTimerTestTags.ACCESSIBILITY_STATUS)
        assertTagExists(UsageTimerTestTags.OVERLAY_STATUS)
        assertTagExists(UsageTimerTestTags.USAGE_ACCESS_STATUS)
        assertTagExists(UsageTimerTestTags.SELECT_APP_BUTTON)
        assertTagNotExists(UsageTimerTestTags.PAUSE_INFO)
    }

    /**
     * 각 조작 버튼이 설계된 라벨 텍스트로 노출되는지 검증
     *
     * Given:
     * - UsageTimerScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 타이머 띄우기/저장/일시정지/일시정지 해제/앱 선택 한국어 라벨 텍스트 존재
     * - 이전 접근성 서비스 설정 버튼 문구 "접근성 서비스 설정" 미존재
     */
    @Test
    fun test_02_buttonsDisplayExpectedLabels() {
        /** Given **/
        hostUsageTimerScreen()

        /** When **/
        // 버튼 라벨은 정적이므로 별도 조작 없이 검증

        /** Then **/
        assertTagText(UsageTimerTestTags.SHOW_BUTTON, "타이머 띄우기")
        assertTagText(UsageTimerTestTags.SAVE_BUTTON, "저장")
        assertTagText(UsageTimerTestTags.PAUSE_BUTTON, "일시정지")
        assertTagText(UsageTimerTestTags.CANCEL_BUTTON, "일시정지 해제")
        assertTagText(UsageTimerTestTags.SELECT_APP_BUTTON, "앱 선택")
        assertTextNotExists("접근성 서비스 설정")
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
        clickTag(UsageTimerTestTags.SELECT_APP_BUTTON, scrollTo = true)

        /** Then **/
        assertDisplayed(R.id.title, rootMatcher = isDialog())
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
        runBlocking { repository.saveSelectedApps(listOf("com.nonexistent.testapp")) }

        /** When **/
        hostUsageTimerScreen()

        /** Then **/
        assertTagExists(UsageTimerTestTags.SELECTED_APPS_LIST)
        assertTextExists("com.nonexistent.testapp")
    }

    /**
     * 선택 앱이 없으면 안내 문구가 표시되고 목록은 노출되지 않는지 검증
     *
     * Given:
     * - 선택 앱 저장소 비어있음(setup에서 초기화)
     * When:
     * - UsageTimerScreen 호스팅
     * Then:
     * - 빈 목록 안내 "선택한 앱이 없습니다." 표시 + 선택 앱 목록 미존재
     */
    @Test
    fun test_05_noAppsSelectedShowsPlaceholder() {
        /** Given **/
        // setup에서 저장소 초기화됨

        /** When **/
        hostUsageTimerScreen()

        /** Then **/
        assertTagText(UsageTimerTestTags.SELECTED_APPS_EMPTY, "선택한 앱이 없습니다.")
        assertTagNotExists(UsageTimerTestTags.SELECTED_APPS_LIST)
    }

    /**
     * 헤더·섹션 캡션이 한국어로 노출되고 이전 영문 캡션은 남지 않았는지 검증
     *
     * Given:
     * - UsageTimerScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 권한/타이머 표시/설정/선택한 앱 캡션 존재
     * - TOOL/OVERLAY/SETTINGS/SELECTED APPS 캡션 미존재
     */
    @Test
    fun test_06_captionsDisplayedInKorean() {
        /** Given **/
        hostUsageTimerScreen()

        /** When **/
        // 캡션은 정적이므로 별도 조작 없이 검증

        /** Then **/
        assertTextExists("권한")
        assertTextExists("타이머 표시")
        assertTextExists("설정")
        assertTextExists("선택한 앱")
        assertTextNotExists("TOOL")
        assertTextNotExists("OVERLAY")
        assertTextNotExists("SETTINGS")
        assertTextNotExists("SELECTED APPS")
    }

    /** UsageTimerScreen을 HiltTestActivity에 setContent로 호스팅 */
    private fun hostUsageTimerScreen(onNavigateUp: () -> Unit = {}) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    UsageTimerScreen(onNavigateUp = onNavigateUp)
                }
            }
        }
    }

    /**
     * 상단 바 뒤로 가기 버튼을 누르면 onNavigateUp이 호출되는지 검증
     *
     * Given:
     * - UsageTimerScreen 호스팅
     * When:
     * - 뒤로 가기 버튼 클릭
     * Then:
     * - onNavigateUp 1회 호출
     */
    @Test
    fun test_07_clickNavigateUpCallsCallback() {
        /** Given **/
        var navigateUpCount = 0
        hostUsageTimerScreen(onNavigateUp = { navigateUpCount++ })

        /** When **/
        clickTag(ToolTopBarTestTags.NAVIGATE_UP_BUTTON)

        /** Then **/
        assertEquals(1, navigateUpCount)
    }

    /**
     * 다른 앱 위에 표시 권한이 없으면 타이머 띄우기를 눌렀을 때 설정 화면 대신 권한 안내 다이얼로그가 표시되는지 검증
     *
     * 권한 상태를 고정하도록 테스트가 권한을 직접 거부(테스트 후 BaseUiTest가 테스트 전 모드로 복원)
     *
     * Given:
     * - 다른 앱 위에 표시 권한 거부, UsageTimerScreen 호스팅
     * When:
     * - show_button 클릭
     * Then:
     * - overlay_permission_dialog에 타이머 안내 문장 표시
     */
    @Test
    fun test_08_clickShowWithoutOverlayPermissionShowsGuideDialog() {
        /** Given **/
        setAppOpMode("SYSTEM_ALERT_WINDOW", "deny")
        hostUsageTimerScreen()

        /** When **/
        clickTag(UsageTimerTestTags.SHOW_BUTTON)

        /** Then **/
        assertTagDisplayed(OverlayPermissionDialogTestTags.DIALOG)
        assertTextDisplayed(context.getString(R.string.usage_timer_overlay_permission_guide))
    }

    /**
     * 권한 안내 다이얼로그에서 취소를 누르면 설정 화면으로 가지 않고 다이얼로그만 닫히는지 검증
     *
     * Given:
     * - 다른 앱 위에 표시 권한 거부, UsageTimerScreen 호스팅, show_button 클릭으로 권한 안내 다이얼로그 표시
     * When:
     * - 취소 버튼 클릭
     * Then:
     * - overlay_permission_dialog 미존재, 화면 제목 표시(설정 화면으로 이동하지 않음)
     */
    @Test
    fun test_09_cancelOverlayPermissionDialogClosesDialog() {
        /** Given **/
        setAppOpMode("SYSTEM_ALERT_WINDOW", "deny")
        hostUsageTimerScreen()
        clickTag(UsageTimerTestTags.SHOW_BUTTON)
        assertTagDisplayed(OverlayPermissionDialogTestTags.DIALOG)

        /** When **/
        clickText(context.getString(R.string.cancel))

        /** Then **/
        assertTagNotExists(OverlayPermissionDialogTestTags.DIALOG)
        assertTagDisplayed(UsageTimerTestTags.TITLE)
    }

    /**
     * 권한 섹션의 두 행이 현재 권한 상태를 표시하는지 검증
     *
     * Given:
     * - UsageTimerScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음, 기기의 현재 권한 상태를 기대값으로 조회)
     * Then:
     * - 접근성 서비스 행에 라벨과 켜짐 여부에 맞는 상태 문구 표시
     * - 다른 앱 위에 표시 행에 라벨과 허용 여부에 맞는 상태 문구 표시
     *
     * 클릭으로 설정 화면을 여는 동작은 ViewModel 유닛 테스트로 검증
     */
    @Test
    fun test_10_permissionRowsShowCurrentStatus() {
        /** Given **/
        hostUsageTimerScreen()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        val accessibilityValue = context.getString(
            if (PermissionHelper.isAccessibilityServiceEnabled(context)) {
                R.string.permission_status_allowed
            } else {
                R.string.permission_status_required
            }
        )
        val overlayValue = context.getString(
            if (PermissionHelper.canDrawOverlays(context)) {
                R.string.permission_status_allowed
            } else {
                R.string.permission_status_required
            }
        )
        assertTagTextContains(UsageTimerTestTags.ACCESSIBILITY_STATUS, "접근성 서비스", accessibilityValue)
        assertTagTextContains(UsageTimerTestTags.OVERLAY_STATUS, "다른 앱 위에 표시", overlayValue)
    }

    /** [uiState]를 주입한 UsageTimerContent를 HiltTestActivity에 setContent로 호스팅 */
    private fun hostUsageTimerContent(
        uiState: UsageTimerUiState,
        onClickAccessibility: () -> Unit = {},
        onClickOverlaySettings: () -> Unit = {},
        onClickUsageAccess: () -> Unit = {},
        onPositionInputChange: (OverlayPositionInput) -> Unit = {},
    ) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    UsageTimerContent(
                        uiState = uiState,
                        onNavigateUp = {},
                        onPauseDurationInputChanged = {},
                        onClickShow = {},
                        onClickSave = {},
                        onClickPause = {},
                        onClickCancelPause = {},
                        onClickAccessibility = onClickAccessibility,
                        onClickOverlaySettings = onClickOverlaySettings,
                        onClickUsageAccess = onClickUsageAccess,
                        onClickSelectApp = {},
                        onPositionInputChange = onPositionInputChange,
                        onFinishTimer = {},
                    )
                }
            }
        }
    }

    /**
     * 권한이 필요한 상태에서 각 권한 행을 누르면 자기 설정 이동 콜백만 호출되는지 검증
     *
     * 기기의 접근성 설정을 바꾸지 않도록 UsageTimerContent에 상태를 직접 주입
     *
     * Given:
     * - 접근성 서비스 꺼짐, 다른 앱 위에 표시 거부 상태의 UsageTimerContent 호스팅
     * When:
     * - 접근성 서비스 행 클릭 후 다른 앱 위에 표시 행 클릭
     * Then:
     * - 두 행 모두 "허용 필요" 표시
     * - 접근성 콜백 1회, 오버레이 콜백 1회
     */
    @Test
    fun test_11_clickRequiredPermissionRowsCallsOwnCallbacks() {
        /** Given **/
        var accessibilityClickCount = 0
        var overlayClickCount = 0
        hostUsageTimerContent(
            uiState = UsageTimerUiState(accessibilityServiceEnabled = false, overlayPermissionGranted = false),
            onClickAccessibility = { accessibilityClickCount++ },
            onClickOverlaySettings = { overlayClickCount++ },
        )

        /** When **/
        clickTag(UsageTimerTestTags.ACCESSIBILITY_STATUS)
        clickTag(UsageTimerTestTags.OVERLAY_STATUS)

        /** Then **/
        val requiredValue = context.getString(R.string.permission_status_required)
        assertTagTextContains(UsageTimerTestTags.ACCESSIBILITY_STATUS, "접근성 서비스", requiredValue)
        assertTagTextContains(UsageTimerTestTags.OVERLAY_STATUS, "다른 앱 위에 표시", requiredValue)
        assertEquals(1, accessibilityClickCount)
        assertEquals(1, overlayClickCount)
    }

    /**
     * 권한이 모두 허용된 상태에서 권한 행이 허용으로 표시되고 누를 수 없는지 검증
     *
     * Given:
     * - 접근성 서비스 켜짐, 다른 앱 위에 표시 허용 상태의 UsageTimerContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 두 행 모두 "허용됨" 표시
     * - 두 행 모두 클릭 동작 없음
     */
    @Test
    fun test_12_allowedPermissionRowsHaveNoClickAction() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(accessibilityServiceEnabled = true, overlayPermissionGranted = true),
        )

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        val allowedValue = context.getString(R.string.permission_status_allowed)
        listOf(UsageTimerTestTags.ACCESSIBILITY_STATUS, UsageTimerTestTags.OVERLAY_STATUS).forEach { tag ->
            assertTagTextContains(tag, allowedValue)
            assertTagMatches(tag, hasNoClickAction())
        }
    }

    /**
     * 표시 위치 설정이 입력 상태의 선택을 표시하는지 검증
     *
     * Given:
     * - 오른쪽·아래 기준 입력 상태의 UsageTimerContent 호스팅
     * When:
     * - 위치 설정 영역으로 스크롤
     * Then:
     * - 가로 오른쪽, 세로 아래 선택됨
     * - 가로 왼쪽, 세로 위쪽 선택 안 됨
     * - 여백 칸이 입력 상태의 값을 표시
     */
    @Test
    fun test_13_positionSettingShowsSelectedAnchors() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                positionInput = OverlayPositionInput(
                    horizontal = HorizontalAnchor.RIGHT,
                    vertical = VerticalAnchor.BOTTOM,
                    marginTop = "11",
                    marginBottom = "22",
                    marginLeft = "33",
                    marginRight = "44",
                ),
            ),
        )

        /** When **/
        assertTagDisplayed(OverlayPositionTestTags.HORIZONTAL_RIGHT, scrollTo = true)

        /** Then **/
        assertTagMatches(OverlayPositionTestTags.HORIZONTAL_RIGHT, isSelected())
        assertTagMatches(OverlayPositionTestTags.HORIZONTAL_LEFT, isNotSelected())
        assertTagMatches(OverlayPositionTestTags.VERTICAL_BOTTOM, isSelected())
        assertTagMatches(OverlayPositionTestTags.VERTICAL_TOP, isNotSelected())
        assertTagEditableText(OverlayPositionTestTags.MARGIN_TOP, "11")
        assertTagEditableText(OverlayPositionTestTags.MARGIN_BOTTOM, "22")
        assertTagEditableText(OverlayPositionTestTags.MARGIN_LEFT, "33")
        assertTagEditableText(OverlayPositionTestTags.MARGIN_RIGHT, "44")
    }

    /**
     * 가로 가운데를 누르면 가운데 기준으로 바뀐 입력이 콜백으로 전달되는지 검증
     *
     * Given:
     * - 기본 입력 상태의 UsageTimerContent 호스팅
     * When:
     * - 가로 가운데 버튼 클릭
     * Then:
     * - 콜백 1회, 가로 기준만 가운데로 바뀐 입력
     */
    @Test
    fun test_14_clickHorizontalCenterCallsCallback() {
        /** Given **/
        val receivedInputs = mutableListOf<OverlayPositionInput>()
        val initialInput = OverlayPositionInput()
        hostUsageTimerContent(
            uiState = UsageTimerUiState(positionInput = initialInput),
            onPositionInputChange = { receivedInputs += it },
        )

        /** When **/
        clickTag(OverlayPositionTestTags.HORIZONTAL_CENTER, scrollTo = true)

        /** Then **/
        assertEquals(listOf(initialInput.copy(horizontal = HorizontalAnchor.CENTER)), receivedInputs)
    }

    /**
     * 여백 칸에 숫자가 아닌 글자를 입력하면 숫자만 남겨 콜백으로 전달되는지 검증
     *
     * Given:
     * - 기본 입력 상태의 UsageTimerContent 호스팅
     * When:
     * - 위쪽 여백 칸의 값을 "a1b2"로 교체
     * Then:
     * - 콜백으로 전달된 위쪽 여백이 "12"
     */
    @Test
    fun test_15_marginInputKeepsOnlyDigits() {
        /** Given **/
        val receivedInputs = mutableListOf<OverlayPositionInput>()
        hostUsageTimerContent(
            uiState = UsageTimerUiState(),
            onPositionInputChange = { receivedInputs += it },
        )
        assertTagDisplayed(OverlayPositionTestTags.MARGIN_TOP, scrollTo = true)

        /** When **/
        replaceTagText(OverlayPositionTestTags.MARGIN_TOP, "a1b2")

        /** Then **/
        assertEquals("12", receivedInputs.last().marginTop)
    }

    /**
     * 사용 시간 표시 검증용 선택 앱
     *
     * 아이콘을 비트맵으로 바꿔 그리므로 고유 크기가 있는 기본 앱 아이콘 사용(크기가 없는 ColorDrawable은 비트맵 생성 실패).
     */
    private val exampleApp = SelectedAppUiItem("com.example.app", "예시 앱", context.packageManager.defaultActivityIcon)

    /**
     * 최근 사용 시간이 있으면 선택 앱 아래에 표시되고 제목에 기간이 드러나는지 검증
     *
     * Given:
     * - 선택 앱 1개와 사용 시간 3,900,000밀리초의 UsageTimerContent 호스팅
     * When:
     * - 선택한 앱 목록으로 스크롤
     * Then:
     * - 사용 시간 줄에 "1시간 5분" 표시
     * - 제목 "선택한 앱 · 최근 24시간 사용 시간" 존재
     */
    @Test
    fun test_16_recentUsageShownUnderSelectedApp() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                selectedApps = listOf(exampleApp),
                recentUsageMillis = mapOf("com.example.app" to 3_900_000L),
            ),
        )

        /** When **/
        assertTagDisplayed(UsageTimerTestTags.SELECTED_APPS_LIST, scrollTo = true)

        /** Then **/
        assertTagText(UsageTimerTestTags.selectedAppUsage("com.example.app"), "1시간 5분")
        assertTextExists("선택한 앱 · 최근 24시간 사용 시간")
    }

    /**
     * 사용 시간을 알 수 없으면 줄과 기간 제목이 표시되지 않는지 검증
     *
     * Given:
     * - 선택 앱 1개, 사용 시간 null의 UsageTimerContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 사용 시간 줄 미존재
     * - 기간 제목 미존재, "선택한 앱" 존재
     */
    @Test
    fun test_17_recentUsageHiddenWhenUnavailable() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(selectedApps = listOf(exampleApp), recentUsageMillis = null),
        )

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagNotExists(UsageTimerTestTags.selectedAppUsage("com.example.app"))
        assertTextNotExists("선택한 앱 · 최근 24시간 사용 시간")
        assertTextExists("선택한 앱")
    }

    /**
     * 선택 앱이 없으면 사용 시간이 비어 있어도 기본 제목을 유지하는지 검증
     *
     * Given:
     * - 선택 앱 없음, 사용 시간 빈 맵의 UsageTimerContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - "선택한 앱" 존재, 기간 제목 미존재
     * - 빈 목록 안내 존재
     */
    @Test
    fun test_18_emptySelectionKeepsPlainTitle() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(selectedApps = emptyList(), recentUsageMillis = emptyMap()),
        )

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTextExists("선택한 앱")
        assertTextNotExists("선택한 앱 · 최근 24시간 사용 시간")
        assertTagExists(UsageTimerTestTags.SELECTED_APPS_EMPTY)
    }

    /**
     * 사용 정보 접근이 필요한 상태에서 행을 누르면 콜백이 호출되는지 검증
     *
     * Given:
     * - 사용 정보 접근 거부 상태의 UsageTimerContent 호스팅
     * When:
     * - 사용 정보 접근 행 클릭
     * Then:
     * - 콜백 1회
     * - 행에 라벨 "사용 정보 접근" 표시
     */
    @Test
    fun test_19_clickRequiredUsageAccessRowCallsCallback() {
        /** Given **/
        var usageAccessClickCount = 0
        hostUsageTimerContent(
            uiState = UsageTimerUiState(usageAccessGranted = false),
            onClickUsageAccess = { usageAccessClickCount++ },
        )

        /** When **/
        clickTag(UsageTimerTestTags.USAGE_ACCESS_STATUS)

        /** Then **/
        assertEquals(1, usageAccessClickCount)
        assertTagTextContains(UsageTimerTestTags.USAGE_ACCESS_STATUS, "사용 정보 접근")
    }
}
