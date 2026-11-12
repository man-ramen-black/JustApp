package com.black.app.ui.main.appinstallnotification

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import com.black.app.R
import com.black.app.testutil.BaseUiTest
import com.black.core.ui.component.ToolTopBarTestTags
import com.black.core.ui.theme.BlackTheme
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [AppInstallNotificationScreen]이 설정·상태·미리보기를 표시하는지 검증하는 Compose UI 테스트
 *
 * 스위치·상태 행은 기기의 실행 설정·권한 상태에 따라 한 분기만 실행되지 않도록 [AppInstallNotificationContent]에
 * 테스트가 고른 상태를 주입해 분기마다 고정된 기대값으로 검증.
 * 테스트 기기가 사용자 실사용 기기라 화면의 스위치·테스트 알림 보내기는 누르지 않음(서비스 설정 변경·알림 잔류 방지).
 * 그 동작은 ViewModel·ForegroundServiceController 유닛 테스트와 기기 수동 확인으로 검증.
 * 화면 재개 때 저장된 실행 설정이 켜져 있으면 서비스 시작을 요청하므로 그런 기기에서는 화면을 여는 것만으로 서비스가 시작될 수 있음
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppInstallNotificationScreenTest : BaseUiTest() {

    /** [hostAppInstallNotificationContent]로 호스팅한 콘텐츠에 주입하는 화면 상태 */
    private val contentUiState = mutableStateOf(AppInstallNotificationUiState())

    /** AppInstallNotificationScreen을 HiltTestActivity에 setContent로 호스팅 */
    private fun hostAppInstallNotificationScreen(onNavigateUp: () -> Unit = {}) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    AppInstallNotificationScreen(onNavigateUp = onNavigateUp)
                }
            }
        }
    }

    /** [contentUiState]를 주입한 AppInstallNotificationContent를 HiltTestActivity에 setContent로 호스팅 */
    private fun hostAppInstallNotificationContent(
        onClickBatteryOptimization: () -> Unit = {},
        onClickNotificationSettings: () -> Unit = {},
    ) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    AppInstallNotificationContent(
                        uiState = contentUiState.value,
                        onNavigateUp = {},
                        onEnabledChange = {},
                        onClickBatteryOptimization = onClickBatteryOptimization,
                        onClickNotificationSettings = onClickNotificationSettings,
                        onClickSendTest = {},
                    )
                }
            }
        }
    }

    /**
     * 제목과 실행 설정 스위치가 표시되는지 검증
     *
     * Given:
     * - HiltTestActivity에 AppInstallNotificationScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 제목 "앱 설치 알림" 표시
     * - 실행 설정 스위치 표시
     */
    @Test
    fun test_01_displaysTitleAndSwitch() {
        /** Given **/
        hostAppInstallNotificationScreen()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagDisplayed(AppInstallNotificationTestTags.TITLE)
        assertTagText(
            AppInstallNotificationTestTags.TITLE,
            context.getString(R.string.fragment_name_app_install_notification),
        )
        assertTagDisplayed(AppInstallNotificationTestTags.ENABLED_SWITCH)
    }

    /**
     * 스위치가 상태의 실행 설정 켜짐 여부를 따르는지 검증
     *
     * Given:
     * - 실행 설정 꺼짐 상태로 AppInstallNotificationContent 호스팅
     * - 스위치 꺼짐
     * When:
     * - 상태를 실행 설정 켜짐으로 변경
     * Then:
     * - 스위치 켜짐
     */
    @Test
    fun test_02_switchFollowsActivatedState() {
        /** Given **/
        contentUiState.value = AppInstallNotificationUiState(serviceActivated = false)
        hostAppInstallNotificationContent()
        assertTagMatches(AppInstallNotificationTestTags.ENABLED_SWITCH, isOff())

        /** When **/
        composeRule.runOnIdle { contentUiState.value = contentUiState.value.copy(serviceActivated = true) }

        /** Then **/
        assertTagMatches(AppInstallNotificationTestTags.ENABLED_SWITCH, isOn())
    }

    /**
     * 서비스 중지·배터리 최적화 예외 없음·알림 차단 상태에서 상태 행이 조치 필요로 표시되고 누르면 조치 화면 이동을 요청하는지 검증
     *
     * Given:
     * - 서비스 중지, 배터리 최적화 예외 없음, 설치 알림 표시 불가 상태로 AppInstallNotificationContent 호스팅
     * When:
     * - 배터리 최적화 상태 행 클릭 → 알림 상태 행 클릭
     * Then:
     * - service_status에 "서비스", "중지됨"
     * - battery_optimization_status에 "배터리 최적화 예외", "필요", 클릭 시 배터리 최적화 콜백 1회
     * - notification_status에 "알림", "꺼짐", 클릭 시 알림 설정 콜백 1회
     */
    @Test
    fun test_03_statusRowsShowRequiredStates() {
        /** Given **/
        var batteryOptimizationClickCount = 0
        var notificationSettingsClickCount = 0
        contentUiState.value = AppInstallNotificationUiState(
            serviceRunning = false,
            batteryOptimizationIgnored = false,
            notificationsEnabled = false,
        )
        hostAppInstallNotificationContent(
            onClickBatteryOptimization = { batteryOptimizationClickCount++ },
            onClickNotificationSettings = { notificationSettingsClickCount++ },
        )

        /** When **/
        clickTag(AppInstallNotificationTestTags.BATTERY_OPTIMIZATION_STATUS, scrollTo = true)
        clickTag(AppInstallNotificationTestTags.NOTIFICATION_STATUS, scrollTo = true)

        /** Then **/
        assertTagTextContains(
            AppInstallNotificationTestTags.SERVICE_STATUS,
            context.getString(R.string.app_install_notification_status_service),
            context.getString(R.string.app_install_notification_status_stopped),
            scrollTo = true,
        )
        assertTagTextContains(
            AppInstallNotificationTestTags.BATTERY_OPTIMIZATION_STATUS,
            context.getString(R.string.app_install_notification_status_battery),
            context.getString(R.string.app_install_notification_status_required),
            scrollTo = true,
        )
        assertTagTextContains(
            AppInstallNotificationTestTags.NOTIFICATION_STATUS,
            context.getString(R.string.app_install_notification_status_notification),
            context.getString(R.string.app_install_notification_status_blocked),
            scrollTo = true,
        )
        assertEquals(1, batteryOptimizationClickCount)
        assertEquals(1, notificationSettingsClickCount)
    }

    /**
     * 서비스 실행·배터리 최적화 예외·알림 허용 상태에서 상태 행이 허용으로 표시되고 누를 수 없는지 검증
     *
     * Given:
     * - 서비스 실행 중, 배터리 최적화 예외, 설치 알림 표시 가능 상태로 AppInstallNotificationContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - service_status에 "서비스", "실행 중"
     * - battery_optimization_status에 "배터리 최적화 예외", "허용됨", 클릭 동작 없음
     * - notification_status에 "알림", "허용됨", 클릭 동작 없음
     */
    @Test
    fun test_04_statusRowsShowAllowedStates() {
        /** Given **/
        contentUiState.value = AppInstallNotificationUiState(
            serviceRunning = true,
            batteryOptimizationIgnored = true,
            notificationsEnabled = true,
        )
        hostAppInstallNotificationContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        val allowedValue = context.getString(R.string.app_install_notification_status_allowed)
        assertTagTextContains(
            AppInstallNotificationTestTags.SERVICE_STATUS,
            context.getString(R.string.app_install_notification_status_service),
            context.getString(R.string.app_install_notification_status_running),
            scrollTo = true,
        )
        assertTagTextContains(
            AppInstallNotificationTestTags.BATTERY_OPTIMIZATION_STATUS,
            context.getString(R.string.app_install_notification_status_battery),
            allowedValue,
            scrollTo = true,
        )
        assertTagMatches(AppInstallNotificationTestTags.BATTERY_OPTIMIZATION_STATUS, !hasClickAction())
        assertTagTextContains(
            AppInstallNotificationTestTags.NOTIFICATION_STATUS,
            context.getString(R.string.app_install_notification_status_notification),
            allowedValue,
            scrollTo = true,
        )
        assertTagMatches(AppInstallNotificationTestTags.NOTIFICATION_STATUS, !hasClickAction())
    }

    /**
     * 설치·업데이트 알림 모형이 예시 앱 이름과 실제 알림 문구로 표시되는지 검증
     *
     * Given:
     * - HiltTestActivity에 AppInstallNotificationScreen 호스팅
     * When:
     * - 각 모형 카드까지 스크롤
     * Then:
     * - install_preview에 JustApp 앱 이름, "설치되었습니다."
     * - update_preview에 JustApp 앱 이름, "업데이트되었습니다."
     * - 두 테스트 알림 보내기 버튼 존재
     */
    @Test
    fun test_05_previewCardsShowAppNameAndMessages() {
        /** Given **/
        hostAppInstallNotificationScreen()
        val appLabel = context.applicationInfo.loadLabel(context.packageManager).toString()

        /** When / Then **/
        assertTagTextContains(AppInstallNotificationTestTags.INSTALL_PREVIEW, appLabel, "설치되었습니다.", scrollTo = true)
        assertTagTextContains(AppInstallNotificationTestTags.UPDATE_PREVIEW, appLabel, "업데이트되었습니다.", scrollTo = true)
        assertTagExists(AppInstallNotificationTestTags.SEND_INSTALL_TEST_BUTTON)
        assertTagExists(AppInstallNotificationTestTags.SEND_UPDATE_TEST_BUTTON)
    }

    /**
     * 상단바 아이콘 미리보기와 공통 아이콘 안내가 표시되는지 검증
     *
     * Given:
     * - HiltTestActivity에 AppInstallNotificationScreen 호스팅
     * When:
     * - 상단바 아이콘 섹션까지 스크롤
     * Then:
     * - status_bar_icon_preview 표시
     * - "설치·업데이트 알림 공통 아이콘" 표시
     */
    @Test
    fun test_06_statusBarIconPreviewDisplayed() {
        /** Given **/
        hostAppInstallNotificationScreen()

        /** When / Then **/
        assertTagDisplayed(AppInstallNotificationTestTags.STATUS_BAR_ICON_PREVIEW, scrollTo = true)
        assertTextDisplayed(context.getString(R.string.app_install_notification_icon_shared), scrollTo = true)
    }

    /**
     * 상단 바 뒤로 가기 버튼을 누르면 onNavigateUp이 호출되는지 검증
     *
     * Given:
     * - AppInstallNotificationScreen 호스팅
     * When:
     * - 뒤로 가기 버튼 클릭
     * Then:
     * - onNavigateUp 1회 호출
     */
    @Test
    fun test_07_clickNavigateUpCallsCallback() {
        /** Given **/
        var navigateUpCount = 0
        hostAppInstallNotificationScreen(onNavigateUp = { navigateUpCount++ })

        /** When **/
        clickTag(ToolTopBarTestTags.NAVIGATE_UP_BUTTON)

        /** Then **/
        assertEquals(1, navigateUpCount)
    }
}
