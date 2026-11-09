package com.black.app.ui.maintab.main.appinstallnotification

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import com.black.app.R
import com.black.app.broadcast.PackageInstallNotifier
import com.black.app.service.ForegroundService
import com.black.app.service.ForegroundServiceController
import com.black.app.testutil.BaseUiTest
import com.black.core.ui.component.ToolTopBarTestTags
import com.black.core.ui.theme.BlackTheme
import com.black.core.util.PermissionHelper
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [AppInstallNotificationScreen]이 설정·상태·미리보기를 기기 상태대로 표시하는지 검증하는 Compose UI 테스트
 *
 * 테스트 기기가 사용자 실사용 기기라 스위치·테스트 알림 보내기는 누르지 않음(서비스 설정 변경·알림 잔류 방지).
 * 그 동작은 ViewModel·ForegroundServiceController 유닛 테스트와 기기 수동 확인으로 검증.
 * 화면 재개 때 저장된 실행 설정이 켜져 있으면 서비스 시작을 요청하므로 그런 기기에서는 화면을 여는 것만으로 서비스가 시작될 수 있음
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppInstallNotificationScreenTest : BaseUiTest() {

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

    /**
     * 제목과 저장된 설정을 따르는 스위치가 표시되는지 검증
     *
     * Given:
     * - HiltTestActivity에 AppInstallNotificationScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 제목 "앱 설치 알림" 표시
     * - 스위치가 저장된 실행 설정과 같은 켜짐 상태
     */
    @Test
    fun test_01_displaysTitleAndSwitchMatchingSavedSetting() {
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
        val activated = runBlocking { ForegroundServiceController(context).activated.first() }
        // 스위치는 DataStore 실행 설정 Flow를 따르고 Compose 유휴 대기는 DataStore 읽기를 기다리지 않아 반영까지 대기
        waitUntilTagMatches(AppInstallNotificationTestTags.ENABLED_SWITCH) {
            if (activated) isOn() else isOff()
        }
    }

    /**
     * 상태 행이 기기의 배터리 최적화·알림 허용 상태를 표시하는지 검증
     *
     * Given:
     * - HiltTestActivity에 AppInstallNotificationScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - service_status에 서비스 라벨과 실제 실행 상태대로 실행 중 또는 중지됨
     * - battery_optimization_status에 기기 상태대로 허용됨 또는 필요
     * - notification_status에 설치 알림 표시 가능 여부대로 허용됨 또는 꺼짐
     */
    @Test
    fun test_02_statusRowsReflectDeviceState() {
        /** Given **/
        hostAppInstallNotificationScreen()
        val allowedValue = context.getString(R.string.app_install_notification_status_allowed)
        val batteryValue = if (PermissionHelper.isIgnoringBatteryOptimizations(context)) {
            allowedValue
        } else {
            context.getString(R.string.app_install_notification_status_required)
        }
        val notificationValue = if (PackageInstallNotifier.canShow(context)) {
            allowedValue
        } else {
            context.getString(R.string.app_install_notification_status_blocked)
        }

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagTextContains(
            AppInstallNotificationTestTags.SERVICE_STATUS,
            context.getString(R.string.app_install_notification_status_service),
            scrollTo = true,
        )
        // 화면 재개가 서비스 시작을 요청할 수 있어, 앱과 같은 프로세스의 ForegroundService.running과 행이 일치할 때까지 대기
        waitUntilTagMatches(AppInstallNotificationTestTags.SERVICE_STATUS) {
            val serviceValue = context.getString(
                if (ForegroundService.running.value) {
                    R.string.app_install_notification_status_running
                } else {
                    R.string.app_install_notification_status_stopped
                }
            )
            hasText(serviceValue, substring = true)
        }
        assertTagTextContains(
            AppInstallNotificationTestTags.BATTERY_OPTIMIZATION_STATUS,
            batteryValue,
            substring = true,
            scrollTo = true,
        )
        assertTagTextContains(
            AppInstallNotificationTestTags.NOTIFICATION_STATUS,
            notificationValue,
            substring = true,
            scrollTo = true,
        )
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
    fun test_03_previewCardsShowAppNameAndMessages() {
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
    fun test_04_statusBarIconPreviewDisplayed() {
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
    fun test_05_clickNavigateUpCallsCallback() {
        /** Given **/
        var navigateUpCount = 0
        hostAppInstallNotificationScreen(onNavigateUp = { navigateUpCount++ })

        /** When **/
        clickTag(ToolTopBarTestTags.NAVIGATE_UP_BUTTON)

        /** Then **/
        assertEquals(1, navigateUpCount)
    }
}
