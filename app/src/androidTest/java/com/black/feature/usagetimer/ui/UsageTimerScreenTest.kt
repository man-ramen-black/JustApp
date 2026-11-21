package com.black.feature.usagetimer.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.unit.Density
import androidx.test.espresso.matcher.RootMatchers.isDialog
import com.black.app.testutil.BaseUiTest
import com.black.app.testutil.assertDisplayed
import com.black.core.model.OverlayPosition.HorizontalAnchor
import com.black.core.model.OverlayPosition.VerticalAnchor
import com.black.core.system.AppUsageSession
import com.black.core.system.RecentAppUsage
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
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours

/**
 * [UsageTimerScreen]이 계측 환경에서 정상 동작하는지 검증하는 Compose UI 테스트
 *
 * HiltTestActivity(@AndroidEntryPoint)에 setContent로 호스팅해 hiltViewModel() 주입 동작 확인.
 * 선택 앱은 실제 앱 저장소를 쓰므로 테스트 전 값을 보관했다가 테스트 후 되돌림.
 * 일시정지·사용 기록처럼 저장소·권한 상태로 표시가 갈리는 영역은 UsageTimerContent에 상태를 주입해 검증.
 * 권한 안내 다이얼로그는 테스트가 다른 앱 위에 표시 권한을 거부해 검증하고, 설정 화면 이동은 ViewModel 유닛 테스트로 검증
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerScreenTest : BaseUiTest() {

    private val repository = UsageTimerRepository(context)

    /** [hostUsageTimerContent]로 호스팅한 콘텐츠에 주입하는 화면 상태(호스팅 뒤 값을 바꾸면 화면에 반영) */
    private val contentUiState = mutableStateOf(UsageTimerUiState())

    /** 사용자 선택 앱을 테스트 후 복원하도록 보관(실사용 기기의 설정 유지)한 뒤 선택 앱 저장소 초기화 */
    override fun setup() {
        preserveValue(read = { repository.getSelectedApps() }, restore = { repository.saveSelectedApps(it) })
        runBlocking { repository.saveSelectedApps(emptyList()) }
    }

    /**
     * 화면의 핵심 요소가 노출되는지 검증
     *
     * 기기 저장소가 일시정지 중일 수 있어 일시정지 아님 상태를 주입한 UsageTimerContent로 검증
     *
     * Given:
     * - 일시정지 값을 읽었고 일시정지 중이 아닌 UsageTimerContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 타이틀 "앱 사용 타이머" 표시
     * - 핵심 조작 요소 모두 존재
     * - 일시정지 해제 버튼, 카운트다운 영역 미존재
     * - 표시 위치가 접혀 있어 타이머 띄우기 버튼 미존재
     */
    @Test
    fun test_01_usageTimerScreenDisplaysCoreElements() {
        /** Given **/
        hostUsageTimerContent(uiState = UsageTimerUiState(pauseStateLoaded = true))

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagDisplayed(UsageTimerTestTags.TITLE)
        assertTagText(UsageTimerTestTags.TITLE, "앱 사용 타이머")
        assertTagExists(UsageTimerTestTags.PAUSE_DURATION_INPUT)
        assertTagExists(UsageTimerTestTags.PAUSE_BUTTON)
        assertTagExists(UsageTimerTestTags.ACCESSIBILITY_STATUS)
        assertTagExists(UsageTimerTestTags.OVERLAY_STATUS)
        assertTagExists(UsageTimerTestTags.USAGE_ACCESS_STATUS)
        assertTagExists(UsageTimerTestTags.POSITION_HEADER)
        assertTagExists(UsageTimerTestTags.SELECT_APP_BUTTON)
        assertTagNotExists(UsageTimerTestTags.CANCEL_BUTTON)
        assertTagNotExists(UsageTimerTestTags.PAUSE_INFO)
        assertTagNotExists(UsageTimerTestTags.SHOW_BUTTON)
    }

    /**
     * 각 조작 버튼이 설계된 라벨 텍스트로 노출되는지 검증
     *
     * Given:
     * - 일시정지 값을 읽었고 일시정지 중이 아닌 UsageTimerContent 호스팅
     * When:
     * - 표시 위치 머리를 눌러 펼침
     * Then:
     * - 타이머 띄우기/일시정지/앱 선택 한국어 라벨 텍스트 존재
     * - 이전 접근성 서비스 설정 버튼 문구 "접근성 서비스 설정" 미존재
     */
    @Test
    fun test_02_buttonsDisplayExpectedLabels() {
        /** Given **/
        hostUsageTimerContent(uiState = UsageTimerUiState(pauseStateLoaded = true))

        /** When **/
        clickTag(UsageTimerTestTags.POSITION_HEADER, scrollTo = true)

        /** Then **/
        assertTagText(UsageTimerTestTags.SHOW_BUTTON, "타이머 띄우기")
        assertTagText(UsageTimerTestTags.PAUSE_BUTTON, "일시정지")
        assertTagText(UsageTimerTestTags.SELECT_APP_BUTTON, "앱 선택")
        assertTextNotExists("접근성 서비스 설정")
    }

    /**
     * 앱 선택 타일을 누르면 앱 선택 다이얼로그가 표시되는지 검증
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
     * 선택 앱이 저장된 상태로 진입하면 타일 격자에 해당 앱이 표시되는지 검증
     *
     * Given:
     * - 존재하지 않는 packageName을 선택 앱으로 저장(라벨 조회 실패 시 packageName 그대로 표시되어 검증이 결정적)
     * When:
     * - UsageTimerScreen 호스팅
     * Then:
     * - 타일 격자 노출 + 저장한 packageName 라벨 표시
     */
    @Test
    fun test_04_selectedAppsDisplayedFromStorage() {
        /** Given **/
        runBlocking { repository.saveSelectedApps(listOf("com.nonexistent.testapp")) }

        /** When **/
        hostUsageTimerScreen()

        /** Then **/
        // 타일 격자는 선택 앱을 읽기 전에도 있으므로 저장소에서 읽은 앱 이름이 나올 때까지 대기
        waitUntilTextExists("com.nonexistent.testapp")
        assertTagExists(UsageTimerTestTags.SELECTED_APPS_LIST)
        assertTextExists("com.nonexistent.testapp")
    }

    /**
     * 선택 앱이 없으면 안내 문구와 앱 선택 타일만 표시되는지 검증
     *
     * Given:
     * - 선택 앱 저장소 비어있음(setup에서 초기화)
     * When:
     * - UsageTimerScreen 호스팅
     * Then:
     * - 빈 목록 안내 "선택한 앱이 없습니다." 표시
     * - 격자에는 앱 선택 타일이 늘 있으므로 select_app_button 존재
     */
    @Test
    fun test_05_noAppsSelectedShowsPlaceholder() {
        /** Given **/
        // setup에서 저장소 초기화됨

        /** When **/
        hostUsageTimerScreen()

        /** Then **/
        assertTagText(UsageTimerTestTags.SELECTED_APPS_EMPTY, "선택한 앱이 없습니다.")
        assertTagExists(UsageTimerTestTags.SELECT_APP_BUTTON)
    }

    /**
     * 섹션 캡션이 한국어로 노출되고 없앤 섹션과 이전 영문 캡션은 남지 않았는지 검증
     *
     * 일시정지 캡션은 같은 글자의 일시정지 버튼이 함께 있어 글자로 찾지 않음
     *
     * Given:
     * - UsageTimerScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 권한/표시 위치/타이머 대상 앱 캡션 존재
     * - 없앤 타이머 표시/설정/선택한 앱 캡션 미존재
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
        assertTextExists("표시 위치")
        assertTextExists("타이머 대상 앱")
        assertTextNotExists("타이머 표시")
        assertTextNotExists("설정")
        assertTextNotExists("선택한 앱")
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
     * - 다른 앱 위에 표시 권한 거부, UsageTimerScreen 호스팅, 표시 위치 머리를 눌러 펼침
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
        clickTag(UsageTimerTestTags.POSITION_HEADER, scrollTo = true)

        /** When **/
        clickTag(UsageTimerTestTags.SHOW_BUTTON, scrollTo = true)

        /** Then **/
        assertTagDisplayed(OverlayPermissionDialogTestTags.DIALOG)
        assertTextDisplayed(context.getString(R.string.usage_timer_overlay_permission_guide))
    }

    /**
     * 권한 안내 다이얼로그에서 취소를 누르면 설정 화면으로 가지 않고 다이얼로그만 닫히는지 검증
     *
     * Given:
     * - 다른 앱 위에 표시 권한 거부, UsageTimerScreen 호스팅, 표시 위치를 펼친 뒤 show_button 클릭으로 권한 안내 다이얼로그 표시
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
        clickTag(UsageTimerTestTags.POSITION_HEADER, scrollTo = true)
        clickTag(UsageTimerTestTags.SHOW_BUTTON, scrollTo = true)
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

    /**
     * [uiState]를 주입한 UsageTimerContent를 HiltTestActivity에 setContent로 호스팅
     *
     * [uiState]는 [contentUiState]에 넣어 호스팅 뒤에도 바꿀 수 있음. [fontScale]은 기기 글꼴 설정 대신 쓰는 글꼴 배율
     */
    private fun hostUsageTimerContent(
        uiState: UsageTimerUiState,
        fontScale: Float = 1f,
        onPauseDurationChange: (String) -> Unit = {},
        onClickPause: () -> Unit = {},
        onClickCancelPause: () -> Unit = {},
        onClickAccessibility: () -> Unit = {},
        onClickOverlaySettings: () -> Unit = {},
        onClickUsageAccess: () -> Unit = {},
        onClickAppTile: (String) -> Unit = {},
        onPositionInputChange: (OverlayPositionInput) -> Unit = {},
    ) {
        contentUiState.value = uiState
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                        UsageTimerContent(
                            uiState = contentUiState.value,
                            onNavigateUp = {},
                            onPauseDurationChange = onPauseDurationChange,
                            onClickShow = {},
                            onClickPause = onClickPause,
                            onClickCancelPause = onClickCancelPause,
                            onClickAccessibility = onClickAccessibility,
                            onClickOverlaySettings = onClickOverlaySettings,
                            onClickUsageAccess = onClickUsageAccess,
                            onClickSelectApp = {},
                            onClickAppTile = onClickAppTile,
                            onPositionInputChange = onPositionInputChange,
                            onFinishTimer = {},
                        )
                    }
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
     * - 오른쪽·아래 기준 입력 상태의 UsageTimerContent 호스팅, 표시 위치 머리를 눌러 펼침
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
        clickTag(UsageTimerTestTags.POSITION_HEADER, scrollTo = true)

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
     * - 기본 입력 상태의 UsageTimerContent 호스팅, 표시 위치 머리를 눌러 펼침
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
        clickTag(UsageTimerTestTags.POSITION_HEADER, scrollTo = true)

        /** When **/
        clickTag(OverlayPositionTestTags.HORIZONTAL_CENTER, scrollTo = true)

        /** Then **/
        assertEquals(listOf(initialInput.copy(horizontal = HorizontalAnchor.CENTER)), receivedInputs)
    }

    /**
     * 여백 칸에 숫자가 아닌 글자를 입력하면 숫자만 남겨 콜백으로 전달되는지 검증
     *
     * Given:
     * - 기본 입력 상태의 UsageTimerContent 호스팅, 표시 위치 머리를 눌러 펼침
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
        clickTag(UsageTimerTestTags.POSITION_HEADER, scrollTo = true)
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

    /** 타일에서 두 줄을 넘겨 말줄임되는 이름의 선택 앱 */
    private val longNameApp = SelectedAppUiItem(
        "com.example.long",
        "아주 긴 이름을 가진 예시 애플리케이션",
        context.packageManager.defaultActivityIcon,
    )

    /**
     * 줄이 다른 타일끼리 크기를 비교하기 위한 선택 앱 20개
     *
     * 같은 줄 타일은 가장 높은 타일에 높이를 맞추므로, 같은 줄끼리 비교하면 이름 두 줄 자리가 빠져도 통과함.
     * 열이 10개를 넘는 가로 태블릿에서도 격자가 여러 줄이 되도록 앱 선택 타일까지 21칸(19열까지 두 줄 이상)으로 채우고, 긴 이름 앱을 첫 칸에, 짧은 이름의
     * [exampleApp]을 끝 칸에 둬 마지막 줄이 짧은 이름 타일과 앱 선택 타일로만 이뤄지도록 구성.
     */
    private val multiRowApps: List<SelectedAppUiItem> =
        listOf(longNameApp) +
            (1..18).map { number ->
                SelectedAppUiItem("com.example.filler$number", "앱 $number", context.packageManager.defaultActivityIcon)
            } +
            exampleApp

    /**
     * 오늘부터 [daysAgo]일 전 날짜의 [hour]시 [minute]분 [second]초 시각(밀리초)
     *
     * 화면이 기기 시간대로 날짜를 묶고 시각을 표시하므로 기기 시간대 기준으로 변환
     */
    private fun at(hour: Int, minute: Int, second: Int = 0, daysAgo: Long = 0): Long =
        LocalDate.now().minusDays(daysAgo).atTime(hour, minute, second)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * 최근 사용 시간이 있으면 선택 앱 타일에 표시되고 제목에 기간이 드러나는지 검증
     *
     * Given:
     * - 사용 정보 접근 허용, 선택 앱 1개와 오늘 10:00~11:05 사용 구간의 UsageTimerContent 호스팅
     * When:
     * - 타일 격자로 스크롤
     * Then:
     * - 타일 사용 시간 줄에 "1시간 5분" 표시
     * - 제목 "타이머 대상 앱 · 최근 24시간" 존재
     */
    @Test
    fun test_16_recentUsageShownUnderSelectedApp() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                usageAccessGranted = true,
                selectedApps = listOf(exampleApp),
                recentUsage = RecentAppUsage(
                    queriedAtMillis = at(23, 50),
                    sessions = mapOf("com.example.app" to listOf(AppUsageSession(at(10, 0), at(11, 5)))),
                ),
            ),
        )

        /** When **/
        assertTagDisplayed(UsageTimerTestTags.SELECTED_APPS_LIST, scrollTo = true)

        /** Then **/
        assertTagText(UsageTimerTestTags.selectedAppUsage("com.example.app"), "1시간 5분", useUnmergedTree = true)
        assertTextExists("타이머 대상 앱 · 최근 24시간")
    }

    /**
     * 권한이 있는데 조회에 실패하면 사용 시간 줄은 자리를 지킨 채 "–"를 보이고, 제목에서 기간을 빼고 실패 안내를 표시하는지 검증
     *
     * Given:
     * - 사용 정보 접근 허용, 선택 앱 1개, 조회 불가 상태의 UsageTimerContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 사용 시간 줄에 "–" 표시
     * - 기간 제목 미존재, "타이머 대상 앱" 존재
     * - 조회 실패 안내 존재
     */
    @Test
    fun test_17_failedUsageShowsDashAndPlainTitle() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                usageAccessGranted = true,
                selectedApps = listOf(exampleApp),
                recentUsageUnavailable = true,
            ),
        )

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagText(UsageTimerTestTags.selectedAppUsage("com.example.app"), "–", useUnmergedTree = true)
        assertTextNotExists("타이머 대상 앱 · 최근 24시간")
        assertTextExists("타이머 대상 앱")
        assertTagText(UsageTimerTestTags.USAGE_SESSIONS_FAILED, "사용 기록을 불러오지 못했습니다.")
    }

    /**
     * 선택 앱이 없으면 사용 기록을 조회했어도 기본 제목을 유지하는지 검증
     *
     * Given:
     * - 사용 정보 접근 허용, 선택 앱 없음, 빈 조회 결과의 UsageTimerContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - "타이머 대상 앱" 존재, 기간 제목 미존재
     * - 빈 목록 안내 존재
     */
    @Test
    fun test_18_emptySelectionKeepsPlainTitle() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                usageAccessGranted = true,
                selectedApps = emptyList(),
                recentUsage = RecentAppUsage(queriedAtMillis = at(23, 50), sessions = emptyMap()),
            ),
        )

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTextExists("타이머 대상 앱")
        assertTextNotExists("타이머 대상 앱 · 최근 24시간")
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

    /**
     * 이름 길이와 조회 상태가 달라도 줄이 다른 대상 앱 타일과 앱 선택 타일의 크기가 같은지 검증
     *
     * 같은 줄 타일은 높이가 늘 같으므로 [multiRowApps]로 긴 이름 앱과 짧은 이름만 있는 마지막 줄을 비교해, 이름 두 줄 자리가
     * 빠지면 마지막 줄이 낮아져 실패. 앱 선택 타일의 사용 시간 줄 자리는 사용 시간 줄이 있는 앱 타일과 같은 줄에서는
     * 드러나지 않아, 선택 앱을 모두 빼 앱 선택 타일만 한 줄에 남긴 뒤 다시 비교
     *
     * Given:
     * - 사용 정보 접근 허용, 긴 이름 앱(조회 중)을 첫 칸, 조회 결과가 있는 짧은 이름 앱을 끝 칸에 둔 앱 20개의 UsageTimerContent 호스팅
     * When:
     * - 타일 격자로 스크롤
     * - 이어서 선택 앱을 모두 뺀 상태로 변경
     * Then:
     * - 변경 전: 긴 이름 앱 타일이 짧은 이름 앱 타일, 앱 선택 타일보다 위 줄
     * - 변경 전: 두 앱 타일과 앱 선택 타일의 너비·높이가 모두 같음, 조회 중인 앱의 사용 시간 줄에 "–" 표시
     * - 변경 뒤: 혼자 남은 앱 선택 타일의 너비·높이가 변경 전 앱 타일과 같음
     */
    @Test
    fun test_20_tilesHaveSameSize() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                pauseStateLoaded = true,
                usageAccessGranted = true,
                selectedApps = multiRowApps,
                recentUsage = RecentAppUsage(
                    queriedAtMillis = at(23, 50),
                    sessions = mapOf("com.example.app" to listOf(AppUsageSession(at(10, 0), at(10, 30)))),
                ),
            ),
        )

        /** When **/
        assertTagDisplayed(UsageTimerTestTags.SELECTED_APPS_LIST, scrollTo = true)

        /** Then **/
        assertTilesBelowRowOf(
            UsageTimerTestTags.appTile("com.example.long"),
            UsageTimerTestTags.appTile("com.example.app"),
            UsageTimerTestTags.SELECT_APP_BUTTON,
        )
        val longNameTileSize = tagSize(UsageTimerTestTags.appTile("com.example.long"))
        assertEquals(longNameTileSize, tagSize(UsageTimerTestTags.appTile("com.example.app")))
        assertEquals(longNameTileSize, tagSize(UsageTimerTestTags.SELECT_APP_BUTTON))
        assertTagText(UsageTimerTestTags.selectedAppUsage("com.example.long"), "–", useUnmergedTree = true)

        /** When **/
        contentUiState.value = contentUiState.value.copy(selectedApps = emptyList())

        /** Then **/
        assertTagExists(UsageTimerTestTags.SELECTED_APPS_EMPTY)
        assertEquals(longNameTileSize, tagSize(UsageTimerTestTags.SELECT_APP_BUTTON))
    }

    /**
     * [tileTags] 타일이 모두 [upperTileTag] 타일보다 아래 줄에 있는지 검증
     *
     * 줄이 다른 타일끼리 크기를 비교하는 전제 확인. 기기가 넓어 격자가 한 줄이 되면 같은 줄끼리 비교하게 되므로 여기서 실패시킴
     */
    private fun assertTilesBelowRowOf(upperTileTag: String, vararg tileTags: String) {
        val upperTileTop = tagTop(upperTileTag)
        tileTags.forEach { tag ->
            assertTrue("$tag 타일이 $upperTileTag 타일과 같은 줄", upperTileTop < tagTop(tag))
        }
    }

    /**
     * 글꼴 배율 1.3에서도 가장 긴 사용 시간 문구가 잘리지 않는지 검증
     *
     * Given:
     * - 글꼴 배율 1.3, 사용 정보 접근 허용, 오늘 0:00~23:59 사용 구간 앱의 UsageTimerContent 호스팅
     * When:
     * - 타일 격자로 스크롤
     * Then:
     * - 사용 시간 줄이 "23시간 59분"이고 말줄임·넘침 없음
     */
    @Test
    fun test_21_usageTextNotClippedAtLargeFont() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                pauseStateLoaded = true,
                usageAccessGranted = true,
                selectedApps = listOf(exampleApp),
                recentUsage = RecentAppUsage(
                    queriedAtMillis = at(23, 59, 30),
                    sessions = mapOf("com.example.app" to listOf(AppUsageSession(at(0, 0), at(23, 59)))),
                ),
            ),
            fontScale = 1.3f,
        )

        /** When **/
        assertTagDisplayed(UsageTimerTestTags.SELECTED_APPS_LIST, scrollTo = true)

        /** Then **/
        val usageTag = UsageTimerTestTags.selectedAppUsage("com.example.app")
        assertTagText(usageTag, "23시간 59분", useUnmergedTree = true)
        assertTagTextNotClipped(usageTag, useUnmergedTree = true)
    }

    /**
     * 기록을 볼 앱의 타일이 선택 상태로 표시되고 그 앱 기록 머리 줄이 나오며, 다른 타일을 누르면 그 앱이 콜백으로 전달되는지 검증
     *
     * Given:
     * - 사용 정보 접근 허용, 앱 2개, 기록을 볼 앱이 긴 이름 앱인 UsageTimerContent 호스팅
     * When:
     * - 짧은 이름 앱 타일 클릭
     * Then:
     * - 기록 머리 줄 "아주 긴 이름을 가진 예시 애플리케이션 사용 기록"
     * - 긴 이름 앱 타일은 선택됨, 짧은 이름 앱 타일은 선택 안 됨
     * - onClickAppTile("com.example.app") 1회
     */
    @Test
    fun test_22_tileShowsViewedAppSessions() {
        /** Given **/
        val clickedPackageNames = mutableListOf<String>()
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                pauseStateLoaded = true,
                usageAccessGranted = true,
                selectedApps = listOf(exampleApp, longNameApp),
                recentUsage = RecentAppUsage(
                    queriedAtMillis = at(23, 50),
                    sessions = mapOf(
                        "com.example.app" to listOf(AppUsageSession(at(10, 0), at(10, 30))),
                        "com.example.long" to emptyList(),
                    ),
                ),
                usageSessionsPackageName = "com.example.long",
            ),
            onClickAppTile = { clickedPackageNames += it },
        )

        /** When **/
        clickTag(UsageTimerTestTags.appTile("com.example.app"), scrollTo = true)

        /** Then **/
        assertTagText(UsageTimerTestTags.USAGE_SESSIONS_TITLE, "아주 긴 이름을 가진 예시 애플리케이션 사용 기록")
        assertTagMatches(UsageTimerTestTags.appTile("com.example.long"), isSelected())
        assertTagMatches(UsageTimerTestTags.appTile("com.example.app"), isNotSelected())
        assertEquals(listOf("com.example.app"), clickedPackageNames)
    }

    /**
     * 구간 줄 문구와 날짜 소계, 타일 합계가 보이는 시각 기준으로 서로 맞는지 검증
     *
     * Given:
     * - 오늘 21:40:50~22:15:10, 오늘 22:30:10~22:30:50, 어제 23:10~오늘 00:12 구간 앱의 UsageTimerContent 호스팅
     * When:
     * - 구간 목록으로 스크롤
     * Then:
     * - 0번 줄 "22:30" / "1분 미만", 1번 줄 "21:40 ~ 22:15" / "35분", 2번 줄 "23:10 ~ 00:12" / "1시간 2분"
     * - 날짜 머리 "오늘 · 35분", "어제 · 1시간 2분"
     * - 타일 사용 시간 "1시간 37분"
     */
    @Test
    fun test_23_sessionRowsAndDayTotals() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                pauseStateLoaded = true,
                usageAccessGranted = true,
                selectedApps = listOf(exampleApp),
                recentUsage = RecentAppUsage(
                    queriedAtMillis = at(23, 50),
                    sessions = mapOf(
                        "com.example.app" to listOf(
                            AppUsageSession(at(23, 10, daysAgo = 1), at(0, 12)),
                            AppUsageSession(at(21, 40, 50), at(22, 15, 10)),
                            AppUsageSession(at(22, 30, 10), at(22, 30, 50)),
                        ),
                    ),
                ),
            ),
        )

        /** When **/
        assertTagDisplayed(UsageTimerTestTags.usageSessionRow(0), scrollTo = true)

        /** Then **/
        assertTagTextContains(UsageTimerTestTags.usageSessionRow(0), "22:30", "1분 미만")
        assertTagTextContains(UsageTimerTestTags.usageSessionRow(1), "21:40 ~ 22:15", "35분")
        assertTagTextContains(UsageTimerTestTags.usageSessionRow(2), "23:10 ~ 00:12", "1시간 2분")
        assertTagText(UsageTimerTestTags.usageDayHeader(UsageDay.TODAY), "오늘 · 35분")
        assertTagText(UsageTimerTestTags.usageDayHeader(UsageDay.YESTERDAY), "어제 · 1시간 2분")
        assertTagText(UsageTimerTestTags.selectedAppUsage("com.example.app"), "1시간 37분", useUnmergedTree = true)
    }

    /**
     * 기간 경계에서 잘린 구간은 "이전", 아직 쓰는 구간은 "사용 중"으로 표시되는지 검증
     *
     * 잘린 구간의 시작은 계산기가 기간 시작으로 바꾸므로 조회 시각 − 24시간으로 둠
     *
     * Given:
     * - 오늘 23:50 조회, 기간 시작(어제 23:50)~어제 23:59 잘린 구간과 오늘 23:30~23:50 진행 중 구간 앱의 UsageTimerContent 호스팅
     * When:
     * - 구간 목록으로 스크롤
     * Then:
     * - 0번 줄 "23:30 ~ 사용 중" / "20분"
     * - 1번 줄 "23:50 이전 ~ 23:59" / "9분"
     */
    @Test
    fun test_24_clippedAndInProgressRows() {
        /** Given **/
        val queriedAtMillis = at(23, 50)
        val periodStartMillis = queriedAtMillis - 24.hours.inWholeMilliseconds
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                pauseStateLoaded = true,
                usageAccessGranted = true,
                selectedApps = listOf(exampleApp),
                recentUsage = RecentAppUsage(
                    queriedAtMillis = queriedAtMillis,
                    sessions = mapOf(
                        "com.example.app" to listOf(
                            AppUsageSession(periodStartMillis, at(23, 59, daysAgo = 1), startsBeforePeriod = true),
                            AppUsageSession(at(23, 30), at(23, 50), inProgress = true),
                        ),
                    ),
                ),
            ),
        )

        /** When **/
        assertTagDisplayed(UsageTimerTestTags.usageSessionRow(0), scrollTo = true)

        /** Then **/
        assertTagTextContains(UsageTimerTestTags.usageSessionRow(0), "23:30 ~ 사용 중", "20분")
        assertTagTextContains(UsageTimerTestTags.usageSessionRow(1), "23:50 이전 ~ 23:59", "9분")
    }

    /**
     * 구간이 5개를 넘으면 최신 5줄만 보이고, 더 보기로 모두 펼친 뒤 접기 문구로 바뀌며, 접으면 5줄로 돌아가는지 검증
     *
     * Given:
     * - 오늘 10시부터 16시까지 매시 10분씩 7구간 앱의 UsageTimerContent 호스팅
     * - 처음에는 4번 줄까지만 있고 5번 줄 미존재, 더 보기 문구 "이전 기록 2개 더 보기"
     * When:
     * - 더 보기 클릭, 이어서 접기 클릭
     * Then:
     * - 더 보기 클릭 뒤 6번 줄 존재, 문구 "접기"
     * - 접기 클릭 뒤 4번 줄 존재, 5번 줄 미존재, 문구 "이전 기록 2개 더 보기"
     */
    @Test
    fun test_25_moreAndCollapse() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                pauseStateLoaded = true,
                usageAccessGranted = true,
                selectedApps = listOf(exampleApp),
                recentUsage = RecentAppUsage(
                    queriedAtMillis = at(23, 50),
                    sessions = mapOf(
                        "com.example.app" to (10..16).map { hour -> AppUsageSession(at(hour, 0), at(hour, 10)) },
                    ),
                ),
            ),
        )
        assertTagExists(UsageTimerTestTags.usageSessionRow(4))
        assertTagNotExists(UsageTimerTestTags.usageSessionRow(5))
        assertTagText(UsageTimerTestTags.USAGE_SESSIONS_MORE, "이전 기록 2개 더 보기")

        /** When **/
        clickTag(UsageTimerTestTags.USAGE_SESSIONS_MORE, scrollTo = true)

        /** Then **/
        assertTagExists(UsageTimerTestTags.usageSessionRow(6))
        assertTagText(UsageTimerTestTags.USAGE_SESSIONS_MORE, "접기")

        /** When **/
        clickTag(UsageTimerTestTags.USAGE_SESSIONS_MORE, scrollTo = true)

        /** Then **/
        assertTagExists(UsageTimerTestTags.usageSessionRow(4))
        assertTagNotExists(UsageTimerTestTags.usageSessionRow(5))
        assertTagText(UsageTimerTestTags.USAGE_SESSIONS_MORE, "이전 기록 2개 더 보기")
    }

    /**
     * 기록을 볼 앱에 구간이 없으면 기록 없음 안내와 "0분"이 표시되는지 검증
     *
     * Given:
     * - 사용 정보 접근 허용, 조회 결과가 빈 구간 목록인 앱의 UsageTimerContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 기록 머리 줄 "예시 앱 사용 기록"
     * - 안내 "최근 24시간 동안 사용 기록이 없습니다."
     * - 타일 사용 시간 "0분"
     */
    @Test
    fun test_26_emptySessionsShowsEmptyText() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                pauseStateLoaded = true,
                usageAccessGranted = true,
                selectedApps = listOf(exampleApp),
                recentUsage = RecentAppUsage(
                    queriedAtMillis = at(23, 50),
                    sessions = mapOf("com.example.app" to emptyList()),
                ),
            ),
        )

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagText(UsageTimerTestTags.USAGE_SESSIONS_TITLE, "예시 앱 사용 기록")
        assertTagText(UsageTimerTestTags.USAGE_SESSIONS_EMPTY, "최근 24시간 동안 사용 기록이 없습니다.")
        assertTagText(UsageTimerTestTags.selectedAppUsage("com.example.app"), "0분", useUnmergedTree = true)
    }

    /**
     * 사용 정보 접근 권한이 없으면 타일 아래에 권한 안내가 나오고 모든 타일에서 사용 시간 줄이 빠지는지 검증
     *
     * 타일 크기는 [test_20_tilesHaveSameSize]와 같은 이유로 [multiRowApps]를 써 줄이 다른 타일끼리 비교
     *
     * Given:
     * - 사용 정보 접근 거부, 조회 불가 상태, 긴 이름 앱을 첫 칸, 짧은 이름 앱을 끝 칸에 둔 앱 20개의 UsageTimerContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 권한 안내 "사용 정보 접근을 허용하면 사용 기록을 볼 수 있습니다." 표시
     * - 두 앱의 사용 시간 줄, 조회 실패 안내 미존재
     * - 긴 이름 앱 타일이 짧은 이름 앱 타일, 앱 선택 타일보다 위 줄
     * - 두 앱 타일과 앱 선택 타일의 너비·높이가 모두 같음
     */
    @Test
    fun test_27_noUsageAccessShowsPermissionHint() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                pauseStateLoaded = true,
                usageAccessGranted = false,
                selectedApps = multiRowApps,
                recentUsageUnavailable = true,
            ),
        )

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagText(UsageTimerTestTags.USAGE_PERMISSION_HINT, "사용 정보 접근을 허용하면 사용 기록을 볼 수 있습니다.")
        assertTagNotExists(UsageTimerTestTags.selectedAppUsage("com.example.app"), useUnmergedTree = true)
        assertTagNotExists(UsageTimerTestTags.selectedAppUsage("com.example.long"), useUnmergedTree = true)
        assertTagNotExists(UsageTimerTestTags.USAGE_SESSIONS_FAILED)
        assertTilesBelowRowOf(
            UsageTimerTestTags.appTile("com.example.long"),
            UsageTimerTestTags.appTile("com.example.app"),
            UsageTimerTestTags.SELECT_APP_BUTTON,
        )
        val longNameTileSize = tagSize(UsageTimerTestTags.appTile("com.example.long"))
        assertEquals(longNameTileSize, tagSize(UsageTimerTestTags.appTile("com.example.app")))
        assertEquals(longNameTileSize, tagSize(UsageTimerTestTags.SELECT_APP_BUTTON))
    }

    /**
     * 기록을 볼 앱의 조회 결과가 아직 없으면 구간 목록 영역에 아무것도 그리지 않는지 검증
     *
     * Given:
     * - 사용 정보 접근 허용, 조회 결과 맵에 선택 앱이 없는(막 추가해 조회 중) UsageTimerContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 기록 머리 줄, 기록 없음 안내, 조회 실패 안내 미존재
     * - 타일 사용 시간 "–"
     */
    @Test
    fun test_28_loadingAppShowsNoSessionList() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(
                pauseStateLoaded = true,
                usageAccessGranted = true,
                selectedApps = listOf(exampleApp),
                recentUsage = RecentAppUsage(queriedAtMillis = at(23, 50), sessions = emptyMap()),
            ),
        )

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagNotExists(UsageTimerTestTags.USAGE_SESSIONS_TITLE)
        assertTagNotExists(UsageTimerTestTags.USAGE_SESSIONS_EMPTY)
        assertTagNotExists(UsageTimerTestTags.USAGE_SESSIONS_FAILED)
        assertTagText(UsageTimerTestTags.selectedAppUsage("com.example.app"), "–", useUnmergedTree = true)
    }

    /**
     * 일시정지 중이면 남은 시간과 해제 버튼만 보이고 일시정지 버튼은 없는지 검증
     *
     * Given:
     * - 남은 일시정지 시간 1분인 UsageTimerContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 해제 버튼 "일시정지 해제", 카운트다운 영역 존재
     * - 일시정지 버튼 미존재
     */
    @Test
    fun test_29_pauseButtonsByState() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(pauseStateLoaded = true, pauseRemainTimeMillis = 60_000L),
        )

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagText(UsageTimerTestTags.CANCEL_BUTTON, "일시정지 해제")
        assertTagExists(UsageTimerTestTags.PAUSE_INFO)
        assertTagNotExists(UsageTimerTestTags.PAUSE_BUTTON)
    }

    /**
     * 저장된 일시정지 값을 읽기 전에는 입력 칸과 버튼을 그리지 않는지 검증
     *
     * Given:
     * - 일시정지 값을 아직 읽지 않은 UsageTimerContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 입력 칸, 일시정지 버튼, 해제 버튼 미존재
     */
    @Test
    fun test_30_pauseAreaHiddenBeforeLoad() {
        /** Given **/
        hostUsageTimerContent(uiState = UsageTimerUiState(pauseStateLoaded = false))

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagNotExists(UsageTimerTestTags.PAUSE_DURATION_INPUT)
        assertTagNotExists(UsageTimerTestTags.PAUSE_BUTTON)
        assertTagNotExists(UsageTimerTestTags.CANCEL_BUTTON)
    }

    /**
     * 일시정지 시간 입력 오류는 사용자가 입력을 고친 뒤에만 표시되는지 검증
     *
     * Given:
     * - 입력이 비었지만 고친 적 없는 UsageTimerContent 호스팅, 오류 문구 미존재
     * When:
     * - 입력을 고친 상태로 변경
     * Then:
     * - 오류 문구 "1분 이상으로 입력해주세요." 표시
     */
    @Test
    fun test_31_pauseErrorOnlyAfterEdit() {
        /** Given **/
        hostUsageTimerContent(
            uiState = UsageTimerUiState(pauseStateLoaded = true, pauseDurationInput = "", pauseDurationEdited = false),
        )
        // 입력 칸이 라벨·안내 글자를 병합하므로 병합되지 않은 트리에서 확인
        assertTagNotExists(UsageTimerTestTags.PAUSE_DURATION_ERROR, useUnmergedTree = true)

        /** When **/
        contentUiState.value = contentUiState.value.copy(pauseDurationEdited = true)

        /** Then **/
        assertTagText(UsageTimerTestTags.PAUSE_DURATION_ERROR, "1분 이상으로 입력해주세요.", useUnmergedTree = true)
    }

    /**
     * 표시 위치 섹션이 기본으로 접혀 있고 머리를 누를 때마다 펼침·접힘이 바뀌는지 검증
     *
     * Given:
     * - 기본 상태의 UsageTimerContent 호스팅, 타이머 띄우기 버튼 미존재
     * When:
     * - 표시 위치 머리 클릭, 다시 클릭
     * Then:
     * - 첫 클릭 뒤 타이머 띄우기 버튼 존재, 두 번째 클릭 뒤 미존재
     */
    @Test
    fun test_32_positionHeaderToggles() {
        /** Given **/
        hostUsageTimerContent(uiState = UsageTimerUiState(pauseStateLoaded = true))
        assertTagNotExists(UsageTimerTestTags.SHOW_BUTTON)

        /** When **/
        clickTag(UsageTimerTestTags.POSITION_HEADER, scrollTo = true)

        /** Then **/
        assertTagExists(UsageTimerTestTags.SHOW_BUTTON)

        /** When **/
        clickTag(UsageTimerTestTags.POSITION_HEADER, scrollTo = true)

        /** Then **/
        assertTagNotExists(UsageTimerTestTags.SHOW_BUTTON)
    }
}
