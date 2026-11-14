package com.black.app.ui.main.usagelog

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import com.black.app.R
import com.black.app.testutil.BaseUiTest
import com.black.app.usagelog.record.NotificationPostedRecord
import com.black.app.usagelog.record.ScreenOnRecord
import com.black.app.usagelog.record.WifiConnectedRecord
import com.black.app.usagelog.upload.LastSendResult
import com.black.core.ui.component.ToolTopBarTestTags
import com.black.core.ui.theme.BlackTheme
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [UsageLogScreen]이 수집 설정·권한 상태·집 Wi-Fi·최근 기록을 표시하는지 검증하는 Compose UI 테스트
 *
 * 권한·기록은 기기 상태와 저장된 실제 기록에 따라 한 분기만 실행되지 않도록 [UsageLogContent]에
 * 테스트가 고른 가짜 상태를 주입해 분기마다 고정된 기대값으로 검증.
 * 테스트 기기가 사용자 실사용 기기라 화면의 스위치·등록·삭제는 Screen에서 누르지 않음(실제 수집 설정·집 Wi-Fi 변경 방지).
 * 그 동작은 ViewModel 유닛 테스트와 기기 수동 확인으로 검증.
 * Screen을 여는 것만으로 재개 때 수집이 한 번 실행될 수 있음
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogScreenTest : BaseUiTest() {

    /** [hostUsageLogContent]로 호스팅한 콘텐츠에 주입하는 화면 상태 */
    private val contentUiState = mutableStateOf(UsageLogUiState())

    /** UsageLogScreen을 HiltTestActivity에 setContent로 호스팅 */
    private fun hostUsageLogScreen() {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    UsageLogScreen(onNavigateUp = {})
                }
            }
        }
    }

    /** [contentUiState]를 주입한 UsageLogContent를 HiltTestActivity에 setContent로 호스팅 */
    private fun hostUsageLogContent(
        onNavigateUp: () -> Unit = {},
        onClickUsageAccess: () -> Unit = {},
        onClickNotificationAccess: () -> Unit = {},
        onClickLocation: () -> Unit = {},
        onClickBatteryOptimization: () -> Unit = {},
        onClickRegisterWifi: () -> Unit = {},
        onClickDeleteWifi: (String) -> Unit = {},
        onClickRegenerateKey: () -> Unit = {},
        onConfirmRegenerateKey: () -> Unit = {},
        onDismissRegenerateKey: () -> Unit = {},
        onClickSendNow: () -> Unit = {},
    ) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    UsageLogContent(
                        uiState = contentUiState.value,
                        onNavigateUp = onNavigateUp,
                        onCollectionEnabledChange = {},
                        onClickUsageAccess = onClickUsageAccess,
                        onClickNotificationAccess = onClickNotificationAccess,
                        onClickLocation = onClickLocation,
                        onClickBatteryOptimization = onClickBatteryOptimization,
                        onClickRegisterWifi = onClickRegisterWifi,
                        onClickDeleteWifi = onClickDeleteWifi,
                        onWebhookUrlChange = {},
                        onClickCopyKey = {},
                        onClickRegenerateKey = onClickRegenerateKey,
                        onConfirmRegenerateKey = onConfirmRegenerateKey,
                        onDismissRegenerateKey = onDismissRegenerateKey,
                        onClickSendNow = onClickSendNow,
                    )
                }
            }
        }
    }

    /**
     * 제목과 섹션 캡션이 표시되는지 검증
     *
     * Given:
     * - HiltTestActivity에 UsageLogScreen 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 제목 "기기 사용 기록" 표시
     * - 섹션 "수집", "권한", "집 Wi-Fi", "전송", "최근 기록" 표시
     */
    @Test
    fun test_01_titleAndSectionsDisplayed() {
        /** Given **/
        hostUsageLogScreen()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagText(UsageLogTestTags.TITLE, context.getString(R.string.fragment_name_usage_log))
        assertTextDisplayed(context.getString(R.string.usage_log_section_collection))
        assertTextDisplayed(context.getString(R.string.usage_log_section_permission), scrollTo = true)
        assertTextDisplayed(context.getString(R.string.usage_log_section_home_wifi), scrollTo = true)
        assertTextDisplayed(context.getString(R.string.usage_log_section_send), scrollTo = true)
        assertTextDisplayed(context.getString(R.string.usage_log_section_recent), scrollTo = true)
    }

    /**
     * 권한 행이 허용 여부에 따라 "허용됨"·"허용 필요"로 표시되는지 검증
     *
     * Given:
     * - 사용 정보 접근 허용, 알림 접근 미허용 상태로 UsageLogContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - usage_access_status에 "사용 정보 접근", "허용됨"
     * - notification_access_status에 "알림 접근", "허용 필요"
     */
    @Test
    fun test_02_permissionRowsShowGrantedAndRequired() {
        /** Given **/
        contentUiState.value = UsageLogUiState(usageAccessGranted = true, notificationAccessGranted = false)
        hostUsageLogContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagTextContains(
            UsageLogTestTags.USAGE_ACCESS_STATUS,
            context.getString(R.string.usage_log_permission_usage_access),
            context.getString(R.string.usage_log_status_allowed),
            scrollTo = true,
        )
        assertTagTextContains(
            UsageLogTestTags.NOTIFICATION_ACCESS_STATUS,
            context.getString(R.string.usage_log_permission_notification_access),
            context.getString(R.string.usage_log_status_required),
            scrollTo = true,
        )
    }

    /**
     * 허용 필요 상태의 권한 행을 누르면 각 조치 콜백이 호출되는지 검증
     *
     * Given:
     * - 모든 권한 미허용 상태로 UsageLogContent 호스팅
     * When:
     * - 사용 정보 접근, 알림 접근, 위치, 배터리 행을 차례로 클릭
     * Then:
     * - 각 콜백 1회 호출
     * - 위치는 "허용 필요" 표시
     */
    @Test
    fun test_03_clickRequiredPermissionInvokesCallback() {
        /** Given **/
        val clickCounts = IntArray(4)
        contentUiState.value = UsageLogUiState()
        hostUsageLogContent(
            onClickUsageAccess = { clickCounts[0]++ },
            onClickNotificationAccess = { clickCounts[1]++ },
            onClickLocation = { clickCounts[2]++ },
            onClickBatteryOptimization = { clickCounts[3]++ },
        )

        /** When **/
        clickTag(UsageLogTestTags.USAGE_ACCESS_STATUS, scrollTo = true)
        clickTag(UsageLogTestTags.NOTIFICATION_ACCESS_STATUS, scrollTo = true)
        clickTag(UsageLogTestTags.LOCATION_STATUS, scrollTo = true)
        clickTag(UsageLogTestTags.BATTERY_STATUS, scrollTo = true)

        /** Then **/
        assertEquals(listOf(1, 1, 1, 1), clickCounts.toList())
        assertTagTextContains(
            UsageLogTestTags.LOCATION_STATUS,
            context.getString(R.string.usage_log_permission_location),
            context.getString(R.string.usage_log_status_required),
            scrollTo = true,
        )
    }

    /**
     * 등록된 집 Wi-Fi가 표시되고 삭제 버튼이 이름을 넘겨 콜백을 호출하는지 검증
     *
     * Given:
     * - 집 Wi-Fi "MyHome_5G" 한 개로 UsageLogContent 호스팅
     * When:
     * - "MyHome_5G" 삭제 버튼 클릭
     * Then:
     * - "MyHome_5G" 표시
     * - 삭제 콜백이 "MyHome_5G"로 1회 호출
     */
    @Test
    fun test_04_homeWifiListAndDelete() {
        /** Given **/
        val deletedSsids = mutableListOf<String>()
        contentUiState.value = UsageLogUiState(homeSsids = listOf("MyHome_5G"))
        hostUsageLogContent(onClickDeleteWifi = { deletedSsids.add(it) })

        /** When **/
        clickTag(UsageLogTestTags.homeWifiDeleteButton("MyHome_5G"), scrollTo = true)

        /** Then **/
        assertTextDisplayed("MyHome_5G", scrollTo = true)
        assertEquals(listOf("MyHome_5G"), deletedSsids)
    }

    /**
     * 등록 버튼이 현재 연결된 Wi-Fi 이름을 표시하는지 검증
     *
     * Given:
     * - 현재 SSID "MyHome_2G"로 UsageLogContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 등록 버튼 "지금 연결된 MyHome_2G 등록"
     */
    @Test
    fun test_05_registerButtonShowsCurrentSsid() {
        /** Given **/
        contentUiState.value = UsageLogUiState(currentSsid = "MyHome_2G")
        hostUsageLogContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagTextContains(
            UsageLogTestTags.REGISTER_WIFI_BUTTON,
            context.getString(R.string.usage_log_register_wifi, "MyHome_2G"),
            scrollTo = true,
        )
    }

    /**
     * 최근 기록이 종류별 설명으로 표시되는지 검증
     *
     * Given:
     * - 화면 켜짐, 알림 게시(제목·본문 있음), 집 Wi-Fi 연결 가짜 기록으로 UsageLogContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 최근 기록 영역과 "화면 켜짐" 표시
     * - 알림은 "앱 이름 · 제목 · 본문", Wi-Fi는 "Wi-Fi 연결 TestHome (집)" 표시
     */
    @Test
    fun test_06_recentRecordsDisplayed() {
        /** Given **/
        contentUiState.value = UsageLogUiState(
            recentRecords = listOf(
                UsageLogRecordItem(1L, ScreenOnRecord(1_700_000_000_000L)),
                UsageLogRecordItem(
                    2L,
                    NotificationPostedRecord(
                        key = "fake_key",
                        packageName = "com.fake.app",
                        appName = "가짜앱",
                        time = 1_700_000_001_000L,
                        title = "가짜 제목",
                        text = "가짜 본문",
                    ),
                ),
                UsageLogRecordItem(
                    3L,
                    WifiConnectedRecord(time = 1_700_000_002_000L, ssid = "TestHome", home = true, inferred = false),
                ),
            ),
        )
        hostUsageLogContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagDisplayed(UsageLogTestTags.RECENT_RECORDS, scrollTo = true)
        assertTextDisplayed(context.getString(R.string.usage_log_record_screen_on), scrollTo = true)
        assertTextDisplayed("가짜앱 · 가짜 제목 · 가짜 본문", scrollTo = true)
        assertTextDisplayed("Wi-Fi 연결 TestHome (집)", scrollTo = true)
    }

    /**
     * 집 Wi-Fi와 기록이 없을 때 빈 상태 문구가 표시되는지 검증
     *
     * Given:
     * - 집 Wi-Fi와 최근 기록이 없는 상태로 UsageLogContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - "등록된 Wi-Fi 없음", "기록 없음" 표시
     */
    @Test
    fun test_07_emptyStatesDisplayed() {
        /** Given **/
        contentUiState.value = UsageLogUiState()
        hostUsageLogContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagText(UsageLogTestTags.EMPTY_HOME_WIFI, context.getString(R.string.usage_log_empty_home_wifi))
        assertTagText(UsageLogTestTags.EMPTY_RECORDS, context.getString(R.string.usage_log_empty_records))
    }

    /**
     * 뒤로 가기 버튼이 콜백을 호출하는지 검증
     *
     * Given:
     * - 호출 횟수를 세는 콜백으로 UsageLogContent 호스팅
     * When:
     * - 뒤로 가기 버튼 클릭
     * Then:
     * - 콜백 1회 호출
     */
    @Test
    fun test_08_navigateUpInvokesCallback() {
        /** Given **/
        var navigateUpCount = 0
        hostUsageLogContent(onNavigateUp = { navigateUpCount++ })

        /** When **/
        clickTag(ToolTopBarTestTags.NAVIGATE_UP_BUTTON)

        /** Then **/
        assertEquals(1, navigateUpCount)
    }

    /**
     * 전송 섹션이 가려진 키와 대기 건수를 표시하고 키 원문은 표시하지 않는지 검증
     *
     * Given:
     * - 암호화 키 "abcdEFGHijklMNOPqrstUVWXyz0123456789+/AB=", 대기 23건, 마지막 전송 없음으로 UsageLogContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 암호화 키 행에 "abcd…AB=" 표시
     * - 마지막 전송 행에 "대기 23건" 표시
     * - 키 원문은 어디에도 없음
     */
    @Test
    fun test_09_sendSectionShowsMaskedKeyAndPending() {
        /** Given **/
        val fullKey = "abcdEFGHijklMNOPqrstUVWXyz0123456789+/AB="
        contentUiState.value = UsageLogUiState(encryptionKey = fullKey, pendingCount = 23)
        hostUsageLogContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagTextContains(UsageLogTestTags.ENCRYPTION_KEY, "abcd…AB=", scrollTo = true)
        assertTagTextContains(UsageLogTestTags.LAST_SEND_STATUS, "대기 23건", substring = true, scrollTo = true)
        assertTextNotExists(fullKey, substring = true)
    }

    /**
     * 키 새로 만들기 확인 대화상자가 표시되고 확인 클릭이 콜백을 호출하는지 검증
     *
     * Given:
     * - showRegenerateKeyConfirm true로 UsageLogContent 호스팅
     * When:
     * - 대화상자의 확인 버튼 클릭
     * Then:
     * - 대화상자 표시(표시 여부는 주입한 상태라 클릭 뒤에도 유지)
     * - 확인 콜백 1회 호출
     */
    @Test
    fun test_10_regenerateConfirmDialog() {
        /** Given **/
        var confirmCount = 0
        contentUiState.value = UsageLogUiState(showRegenerateKeyConfirm = true)
        hostUsageLogContent(onConfirmRegenerateKey = { confirmCount++ })

        /** When **/
        clickTag(UsageLogTestTags.REGENERATE_KEY_CONFIRM)

        /** Then **/
        assertTagDisplayed(UsageLogTestTags.REGENERATE_KEY_DIALOG)
        assertEquals(1, confirmCount)
    }

    /**
     * 지금 보내기 버튼이 콜백을 호출하는지 검증
     *
     * Given:
     * - 호출 횟수를 세는 콜백으로 UsageLogContent 호스팅
     * When:
     * - 지금 보내기 버튼 클릭
     * Then:
     * - 콜백 1회 호출
     */
    @Test
    fun test_11_sendNowClickInvokesCallback() {
        /** Given **/
        var sendNowCount = 0
        hostUsageLogContent(onClickSendNow = { sendNowCount++ })

        /** When **/
        clickTag(UsageLogTestTags.SEND_NOW_BUTTON, scrollTo = true)

        /** Then **/
        assertEquals(1, sendNowCount)
    }

    /**
     * 키가 없으면 "없음"과 비활성 복사 버튼, 마지막 전송이 있으면 시각 뒤에 결과를 표시하는지 검증
     *
     * Given:
     * - 키 없음, 마지막 전송 실패, 대기 5건으로 UsageLogContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 암호화 키 행에 "없음", 복사 버튼은 눌리지 않음, 새로 만들기 버튼은 눌림
     * - 마지막 전송 행에 "실패 · 대기 5건"
     */
    @Test
    fun test_12_noKeyDisablesCopyAndShowsLastFailure() {
        /** Given **/
        contentUiState.value = UsageLogUiState(lastSendResult = LastSendResult(1_700_000_000_000L, false), pendingCount = 5)
        hostUsageLogContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagTextContains(UsageLogTestTags.ENCRYPTION_KEY, context.getString(R.string.usage_log_key_none), scrollTo = true)
        assertTagMatches(UsageLogTestTags.COPY_KEY_BUTTON, isNotEnabled())
        assertTagMatches(UsageLogTestTags.REGENERATE_KEY_BUTTON, isEnabled())
        assertTagTextContains(UsageLogTestTags.LAST_SEND_STATUS, "실패 · 대기 5건", substring = true, scrollTo = true)
    }

    /**
     * 비활성 버튼은 글자뿐 아니라 배경까지 흐리게 그려지는지 검증
     *
     * Given:
     * - 키 없음(복사 버튼 비활성, 새로 만들기 버튼 활성)으로 UsageLogContent 호스팅
     * When:
     * - 두 버튼을 이미지로 캡처
     * Then:
     * - 글자가 없는 왼쪽 가장자리 안쪽 배경 픽셀 색이 두 버튼에서 다름
     */
    @Test
    fun test_13_disabledButtonDimsBackground() {
        /** Given **/
        contentUiState.value = UsageLogUiState()
        hostUsageLogContent()

        /** When **/
        val disabledImage = captureTag(UsageLogTestTags.COPY_KEY_BUTTON, scrollTo = true).toPixelMap()
        val enabledImage = captureTag(UsageLogTestTags.REGENERATE_KEY_BUTTON, scrollTo = true).toPixelMap()

        /** Then **/
        // 테두리(1dp)와 가운데 글자를 피한 배경 위치
        val disabledBackground = disabledImage[disabledImage.width / 20, disabledImage.height / 2]
        val enabledBackground = enabledImage[enabledImage.width / 20, enabledImage.height / 2]
        assertNotEquals(enabledBackground, disabledBackground)
    }

    /**
     * 위치 권한이 모두 있어도 기기 위치 설정이 꺼져 있으면 "위치 꺼짐"으로 표시하고 누를 수 있는지 검증
     *
     * Given:
     * - 정확한 위치와 백그라운드 위치 허용, 기기 위치 설정 꺼짐으로 UsageLogContent 호스팅
     * When:
     * - 위치 행 클릭
     * Then:
     * - 위치 행에 "위치 항상 허용", "위치 꺼짐"
     * - 위치 콜백 1회 호출
     */
    @Test
    fun test_14_locationOffShownAndClickable() {
        /** Given **/
        var locationClickCount = 0
        contentUiState.value = UsageLogUiState(
            fineLocationGranted = true,
            backgroundLocationGranted = true,
            locationEnabled = false,
        )
        hostUsageLogContent(onClickLocation = { locationClickCount++ })

        /** When **/
        clickTag(UsageLogTestTags.LOCATION_STATUS, scrollTo = true)

        /** Then **/
        assertTagTextContains(
            UsageLogTestTags.LOCATION_STATUS,
            context.getString(R.string.usage_log_permission_location),
            context.getString(R.string.usage_log_status_location_off),
            scrollTo = true,
        )
        assertEquals(1, locationClickCount)
    }

    /**
     * 대략적인 위치만 허용했으면 "정확한 위치 필요"로 표시하는지 검증
     *
     * Given:
     * - 대략적인 위치만 허용, 백그라운드 위치 허용, 기기 위치 설정 켜짐으로 UsageLogContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 위치 행에 "정확한 위치 필요"
     */
    @Test
    fun test_15_approximateLocationOnlyShowsPreciseRequired() {
        /** Given **/
        contentUiState.value = UsageLogUiState(
            approximateLocationOnly = true,
            backgroundLocationGranted = true,
            locationEnabled = true,
        )
        hostUsageLogContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagTextContains(
            UsageLogTestTags.LOCATION_STATUS,
            context.getString(R.string.usage_log_status_precise_location_required),
            scrollTo = true,
        )
    }

    /**
     * 정확한 위치만 있고 백그라운드 위치가 없으면 "허용 필요"로 표시하는지 검증
     *
     * Given:
     * - 정확한 위치 허용, 백그라운드 위치 미허용, 기기 위치 설정 켜짐으로 UsageLogContent 호스팅
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 위치 행에 "허용 필요"
     */
    @Test
    fun test_16_fineWithoutBackgroundShowsRequired() {
        /** Given **/
        contentUiState.value = UsageLogUiState(
            fineLocationGranted = true,
            backgroundLocationGranted = false,
            locationEnabled = true,
        )
        hostUsageLogContent()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagTextContains(
            UsageLogTestTags.LOCATION_STATUS,
            context.getString(R.string.usage_log_status_required),
            scrollTo = true,
        )
    }

    /**
     * 허용된 권한 행은 "허용됨"으로 표시되고 눌리지 않는지 검증
     *
     * Given:
     * - 모든 권한 허용, 기기 위치 설정 켜짐으로 UsageLogContent 호스팅
     * When:
     * - 사용 정보 접근, 알림 접근, 위치, 배터리 행을 차례로 클릭
     * Then:
     * - 네 행 모두 "허용됨", 클릭 동작 없음
     * - 콜백 호출 없음
     */
    @Test
    fun test_17_grantedRowsNotClickable() {
        /** Given **/
        val clickCounts = IntArray(4)
        contentUiState.value = UsageLogUiState(
            usageAccessGranted = true,
            notificationAccessGranted = true,
            fineLocationGranted = true,
            backgroundLocationGranted = true,
            locationEnabled = true,
            batteryOptimizationIgnored = true,
        )
        hostUsageLogContent(
            onClickUsageAccess = { clickCounts[0]++ },
            onClickNotificationAccess = { clickCounts[1]++ },
            onClickLocation = { clickCounts[2]++ },
            onClickBatteryOptimization = { clickCounts[3]++ },
        )
        val rowTags = listOf(
            UsageLogTestTags.USAGE_ACCESS_STATUS,
            UsageLogTestTags.NOTIFICATION_ACCESS_STATUS,
            UsageLogTestTags.LOCATION_STATUS,
            UsageLogTestTags.BATTERY_STATUS,
        )

        /** When **/
        rowTags.forEach { clickTag(it, scrollTo = true) }

        /** Then **/
        rowTags.forEach { tag ->
            assertTagTextContains(tag, context.getString(R.string.usage_log_status_allowed), scrollTo = true)
            assertTagMatches(tag, hasNoClickAction())
        }
        assertEquals(listOf(0, 0, 0, 0), clickCounts.toList())
    }

    /**
     * 등록되지 않은 Wi-Fi면 등록 버튼이 눌려 콜백을 호출하는지 검증
     *
     * Given:
     * - 현재 SSID "Cafe", 집 Wi-Fi "MyHome_5G"로 UsageLogContent 호스팅
     * When:
     * - 등록 버튼 클릭
     * Then:
     * - 등록 콜백 1회 호출
     */
    @Test
    fun test_18_registerButtonInvokesCallback() {
        /** Given **/
        var registerCount = 0
        contentUiState.value = UsageLogUiState(currentSsid = "Cafe", homeSsids = listOf("MyHome_5G"))
        hostUsageLogContent(onClickRegisterWifi = { registerCount++ })

        /** When **/
        clickTag(UsageLogTestTags.REGISTER_WIFI_BUTTON, scrollTo = true)

        /** Then **/
        assertEquals(1, registerCount)
    }

    /**
     * 지금 연결된 Wi-Fi가 이미 집 Wi-Fi로 등록돼 있으면 등록 버튼이 비활성인지 검증
     *
     * Given:
     * - 현재 SSID "MyHome_5G", 집 Wi-Fi "MyHome_5G"로 UsageLogContent 호스팅
     * When:
     * - 등록 버튼 클릭
     * Then:
     * - 등록 버튼 비활성
     * - 등록 콜백 호출 없음
     */
    @Test
    fun test_19_registerButtonDisabledWhenAlreadyRegistered() {
        /** Given **/
        var registerCount = 0
        contentUiState.value = UsageLogUiState(currentSsid = "MyHome_5G", homeSsids = listOf("MyHome_5G"))
        hostUsageLogContent(onClickRegisterWifi = { registerCount++ })

        /** When **/
        clickTag(UsageLogTestTags.REGISTER_WIFI_BUTTON, scrollTo = true)

        /** Then **/
        assertTagMatches(UsageLogTestTags.REGISTER_WIFI_BUTTON, isNotEnabled())
        assertEquals(0, registerCount)
    }

    /**
     * 키 새로 만들기 버튼이 콜백을 호출하는지 검증
     *
     * Given:
     * - 호출 횟수를 세는 콜백으로 UsageLogContent 호스팅
     * When:
     * - 새로 만들기 버튼 클릭
     * Then:
     * - 새로 만들기 콜백 1회 호출
     */
    @Test
    fun test_20_regenerateKeyButtonInvokesCallback() {
        /** Given **/
        var regenerateCount = 0
        hostUsageLogContent(onClickRegenerateKey = { regenerateCount++ })

        /** When **/
        clickTag(UsageLogTestTags.REGENERATE_KEY_BUTTON, scrollTo = true)

        /** Then **/
        assertEquals(1, regenerateCount)
    }

    /**
     * 키 새로 만들기 대화상자의 취소가 닫기 콜백만 호출하는지 검증
     *
     * Given:
     * - showRegenerateKeyConfirm true로 UsageLogContent 호스팅
     * When:
     * - 대화상자의 "취소" 클릭
     * Then:
     * - 닫기 콜백 1회 호출
     * - 확인 콜백 호출 없음
     */
    @Test
    fun test_21_regenerateDialogCancelInvokesDismiss() {
        /** Given **/
        var dismissCount = 0
        var confirmCount = 0
        contentUiState.value = UsageLogUiState(showRegenerateKeyConfirm = true)
        hostUsageLogContent(
            onConfirmRegenerateKey = { confirmCount++ },
            onDismissRegenerateKey = { dismissCount++ },
        )

        /** When **/
        clickText(context.getString(R.string.cancel))

        /** Then **/
        assertEquals(1, dismissCount)
        assertEquals(0, confirmCount)
    }
}
