package com.black.app.ui.main.usagelog

import com.black.app.R
import com.black.app.model.StoredUsageLogRecord
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.ui.common.selectapp.InstalledAppResolver
import com.black.app.usagelog.UsageLogCollectionController
import com.black.app.usagelog.collect.UsageStatsCollector
import com.black.app.usagelog.upload.LastSendResult
import com.black.app.usagelog.upload.SendReason
import com.black.app.usagelog.upload.UsageLogCipher
import com.black.app.usagelog.work.UsageLogScheduler
import com.black.app.usagelog.record.NotificationPostedRecord
import com.black.app.usagelog.record.NotificationRemovedRecord
import com.black.app.usagelog.record.RemovalReason
import com.black.app.usagelog.record.ScreenOnRecord
import com.black.core.viewmodel.ViewModelEvent
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.IOException

/** [UsageLogViewModel]의 상태 구성, 수집 켜기·끄기, 집 Wi-Fi 등록·삭제, 권한 요청 분기와 이벤트 발송 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogViewModelTest : BaseTest() {

    @MockK
    lateinit var settings: UsageLogSettingsRepository

    @MockK
    lateinit var repository: UsageLogRepository

    @MockK
    lateinit var controller: UsageLogCollectionController

    @MockK
    lateinit var collector: UsageStatsCollector

    @MockK
    lateinit var status: UsageLogSystemStatus

    @MockK
    lateinit var installedAppResolver: InstalledAppResolver

    @MockK
    lateinit var scheduler: UsageLogScheduler

    private val collectionEnabled = MutableStateFlow(false)
    private val homeSsids = MutableStateFlow<Set<String>>(emptySet())
    private val recentRecords = MutableStateFlow<List<StoredUsageLogRecord>>(emptyList())
    private val webhookUrl = MutableStateFlow("")
    private val encryptionKey = MutableStateFlow("")
    private val lastSendResult = MutableStateFlow<LastSendResult?>(null)
    private val unsentCount = MutableStateFlow(0)

    @Before
    fun setupViewModel() {
        collectionEnabled.value = false
        homeSsids.value = emptySet()
        recentRecords.value = emptyList()
        webhookUrl.value = ""
        encryptionKey.value = ""
        lastSendResult.value = null
        unsentCount.value = 0
        every { settings.webhookUrl } returns webhookUrl
        every { settings.encryptionKey } returns encryptionKey
        every { settings.lastSendResult } returns lastSendResult
        every { repository.unsentCount() } returns unsentCount
        coEvery { settings.setWebhookUrl(any()) } answers {
            webhookUrl.value = firstArg()
            Result.success(Unit)
        }
        coEvery { settings.setEncryptionKey(any()) } answers {
            encryptionKey.value = firstArg()
            Result.success(Unit)
        }
        every { scheduler.enqueueSend(any()) } just Runs
        every { settings.collectionEnabled } returns collectionEnabled
        every { settings.homeSsids } returns homeSsids
        coEvery { settings.addHomeSsid(any()) } returns Result.success(Unit)
        coEvery { settings.removeHomeSsid(any()) } returns Result.success(Unit)
        every { repository.recentRecords(100) } returns recentRecords
        coEvery { controller.ensureScheduled() } returns Unit
        coEvery { controller.enable() } returns Result.success(Unit)
        coEvery { controller.disable() } returns Result.success(Unit)
        coEvery { collector.collect() } returns Unit
        every { status.isUsageAccessGranted() } returns false
        every { status.isNotificationAccessGranted() } returns false
        every { status.isFineLocationGranted() } returns false
        every { status.isBackgroundLocationGranted() } returns false
        every { status.isBatteryOptimizationIgnored() } returns false
        every { status.currentSsid() } returns null
        every { installedAppResolver.resolveLabel(any()) } answers { "라벨_" + firstArg<String>() }
    }

    /** ViewModel 생성 후 init의 Flow 수집 시작까지 대기 */
    private fun createViewModel() = UsageLogViewModel(settings, repository, controller, collector, status, installedAppResolver, scheduler)
        .also { waitCoroutine() }

    /** IO 디스패처 변환이 끝나 [expected]가 UiState에 반영될 때까지 대기 */
    private suspend fun awaitRecentRecords(viewModel: UsageLogViewModel, expected: List<UsageLogRecordItem>) {
        repeat(200) {
            waitCoroutine()
            if (viewModel.uiState.value.recentRecords == expected) return
            withContext(Dispatchers.Default) { delay(10) }
        }
    }

    /** 동작 전에 이벤트 구독을 시작해 발송된 이벤트 전부(없음 포함)를 수집 */
    private fun TestScope.collectEvents(viewModel: UsageLogViewModel): List<ViewModelEvent> {
        val receivedEvents = mutableListOf<ViewModelEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(receivedEvents)
        }
        return receivedEvents
    }

    /**
     * 설정과 시스템 상태가 UiState에 반영되고 집 Wi-Fi가 이름순으로 정렬되는지 검증
     *
     * Given: 수집 켜짐, 집 SSID "B", "A", 사용 정보 접근과 현재 SSID가 있는 상태
     * When: ViewModel 생성
     * Then: collectionEnabled true, homeSsids ["A", "B"], 사용 정보·위치(정확한·백그라운드)·배터리 권한 값과 currentSsid 반영, 주기 작업 예약 확인
     */
    @Test
    fun test_01_initialStateReflectsSettingsAndStatus() = runTest {
        /** Given **/
        collectionEnabled.value = true
        homeSsids.value = setOf("B", "A")
        every { status.isUsageAccessGranted() } returns true
        every { status.isFineLocationGranted() } returns true
        every { status.isBackgroundLocationGranted() } returns true
        every { status.isBatteryOptimizationIgnored() } returns true
        every { status.currentSsid() } returns "MyHome_2G"

        /** When **/
        val viewModel = createViewModel()

        /** Then **/
        val uiState = viewModel.uiState.value
        assertTrue(uiState.collectionEnabled)
        assertEquals(listOf("A", "B"), uiState.homeSsids)
        assertTrue(uiState.usageAccessGranted)
        assertFalse(uiState.notificationAccessGranted)
        assertTrue(uiState.fineLocationGranted)
        assertTrue(uiState.backgroundLocationGranted)
        assertTrue(uiState.batteryOptimizationIgnored)
        assertEquals("MyHome_2G", uiState.currentSsid)
        coVerify { controller.ensureScheduled() }
    }

    /**
     * 스위치 변경이 켜기·끄기 컨트롤러 호출로 이어지는지 검증
     *
     * Given: ViewModel 생성
     * When: setCollectionEnabled(true), 이어서 false
     * Then: enable, disable 각 1회 호출
     */
    @Test
    fun test_02_setCollectionEnabledCallsController() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.setCollectionEnabled(true)
        waitCoroutine()
        viewModel.setCollectionEnabled(false)
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) {
            controller.enable()
            controller.disable()
        }
    }

    /**
     * 현재 연결된 Wi-Fi 등록이 집 SSID 추가로 이어지는지 검증
     *
     * Given: 현재 SSID "MyHome_2G"
     * When: registerCurrentWifi 호출
     * Then: addHomeSsid("MyHome_2G") 호출, 이벤트 없음
     */
    @Test
    fun test_03_registerCurrentWifiAddsSsid() = runTest {
        /** Given **/
        every { status.currentSsid() } returns "MyHome_2G"
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.registerCurrentWifi()
        waitCoroutine()

        /** Then **/
        coVerify { settings.addHomeSsid("MyHome_2G") }
        assertEquals(emptyList<ViewModelEvent>(), receivedEvents)
    }

    /**
     * 연결된 Wi-Fi 이름을 읽을 수 없으면 등록하지 않고 안내 토스트를 보내는지 검증
     *
     * Given: 현재 SSID null
     * When: registerCurrentWifi 호출
     * Then: EventShowToast(usage_log_wifi_unknown), addHomeSsid 미호출
     */
    @Test
    fun test_04_registerWithoutSsidShowsToast() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.registerCurrentWifi()
        waitCoroutine()

        /** Then **/
        assertEquals(
            listOf<ViewModelEvent>(UsageLogViewModel.EventShowToast(R.string.usage_log_wifi_unknown)),
            receivedEvents,
        )
        coVerify(exactly = 0) { settings.addHomeSsid(any()) }
    }

    /**
     * 집 Wi-Fi 삭제가 저장소 삭제로 이어지는지 검증
     *
     * Given: ViewModel 생성
     * When: removeHomeWifi("MyHome_5G") 호출
     * Then: removeHomeSsid("MyHome_5G") 호출
     */
    @Test
    fun test_05_removeHomeWifiRemoves() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.removeHomeWifi("MyHome_5G")
        waitCoroutine()

        /** Then **/
        coVerify { settings.removeHomeSsid("MyHome_5G") }
    }

    /**
     * 위치 항상 허용이 아니면 권한 창 없이 설정 화면으로 이동하는지 검증
     *
     * Given: 정확한 위치 없음
     * When: openLocationSettings 호출, 정확한 위치만 있는 상태로 다시 호출, 항상 허용 상태로 다시 호출
     * Then: EventOpenLocationSettings 두 번, 항상 허용 상태에서는 이벤트 없음
     */
    @Test
    fun test_06_openLocationSettingsUntilAlwaysAllowed() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.openLocationSettings()
        every { status.isFineLocationGranted() } returns true
        viewModel.openLocationSettings()
        every { status.isBackgroundLocationGranted() } returns true
        viewModel.openLocationSettings()

        /** Then **/
        assertEquals(
            listOf<ViewModelEvent>(
                UsageLogViewModel.EventOpenLocationSettings,
                UsageLogViewModel.EventOpenLocationSettings,
            ),
            receivedEvents,
        )
    }

    /**
     * 상태 갱신이 권한과 현재 SSID를 다시 읽고 수집을 실행하는지 검증
     *
     * Given: 생성 때 알림 접근 없음
     * When: 알림 접근이 허용된 뒤 refreshStatus 호출
     * Then: notificationAccessGranted true, collect 호출
     */
    @Test
    fun test_07_refreshStatusCollects() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        every { status.isNotificationAccessGranted() } returns true

        /** When **/
        viewModel.refreshStatus()
        waitCoroutine()

        /** Then **/
        assertTrue(viewModel.uiState.value.notificationAccessGranted)
        coVerify { collector.collect() }
    }

    /**
     * 최근 기록이 저장소 Flow를 따르는지 검증
     *
     * Given: ViewModel 생성
     * When: 저장소가 기록 한 건을 내보냄
     * Then: recentRecords가 그 기록
     */
    @Test
    fun test_08_recentRecordsFollowRepository() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val expected = listOf(UsageLogRecordItem(1L, ScreenOnRecord(1_000L)))

        /** When **/
        recentRecords.value = listOf(StoredUsageLogRecord(1L, ScreenOnRecord(1_000L)))
        awaitRecentRecords(viewModel, expected)

        /** Then **/
        assertEquals(expected, viewModel.uiState.value.recentRecords)
    }

    /**
     * 수집 켜기가 실패하면 저장 실패 토스트를 보내는지 검증
     *
     * Given: enable이 실패 결과 반환
     * When: setCollectionEnabled(true) 호출
     * Then: EventShowToast(usage_log_save_failed)
     */
    @Test
    fun test_09_enableFailureShowsToast() = runTest {
        /** Given **/
        coEvery { controller.enable() } returns Result.failure(IOException("disk"))
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.setCollectionEnabled(true)
        waitCoroutine()

        /** Then **/
        assertEquals(
            listOf<ViewModelEvent>(UsageLogViewModel.EventShowToast(R.string.usage_log_save_failed)),
            receivedEvents,
        )
    }

    /**
     * 켜기가 진행 중일 때 들어온 스위치 요청을 무시하는지 검증
     *
     * Given: enable이 끝나지 않는 상태
     * When: setCollectionEnabled(true)를 두 번 연속 호출
     * Then: enable 1회만 호출
     */
    @Test
    fun test_10_toggleWhileInProgressIgnored() = runTest {
        /** Given **/
        coEvery { controller.enable() } coAnswers {
            awaitCancellation()
        }
        val viewModel = createViewModel()

        /** When **/
        viewModel.setCollectionEnabled(true)
        waitCoroutine()
        viewModel.setCollectionEnabled(true)
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 1) { controller.enable() }
    }

    /**
     * 수집 중 예외가 나도 앱이 종료되지 않고 이후 호출이 이어지는지 검증
     *
     * Given: collect가 IOException을 던짐
     * When: refreshStatus를 두 번 호출
     * Then: 예외 없이 collect 2회 호출
     */
    @Test
    fun test_11_collectFailureDoesNotCrash() = runTest {
        /** Given **/
        coEvery { collector.collect() } throws IOException("disk")
        val viewModel = createViewModel()

        /** When **/
        viewModel.refreshStatus()
        waitCoroutine()
        viewModel.refreshStatus()
        waitCoroutine()

        /** Then **/
        coVerify(exactly = 2) { collector.collect() }
    }

    /**
     * 같은 목록의 같은 key 알림 게시 기록이 있으면 그 앱 이름을 쓰고 조회하지 않는지 검증
     *
     * Given: 알림 제거 기록과 같은 key의 알림 게시 기록(appName "게시앱")
     * When: 저장소가 두 기록을 내보냄
     * Then: 제거 기록 항목의 appLabel이 "게시앱", resolveLabel 미호출
     */
    @Test
    fun test_12_removedRecordReusesPostedAppName() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val posted = NotificationPostedRecord(key = "k1", packageName = "com.fake", appName = "게시앱", time = 1_000L)
        val removed = NotificationRemovedRecord(key = "k1", packageName = "com.fake", time = 2_000L, reason = RemovalReason.CLICK)
        val expected = listOf(
            UsageLogRecordItem(2L, removed, "게시앱"),
            UsageLogRecordItem(1L, posted),
        )

        /** When **/
        recentRecords.value = listOf(StoredUsageLogRecord(2L, removed), StoredUsageLogRecord(1L, posted))
        awaitRecentRecords(viewModel, expected)

        /** Then **/
        assertEquals(expected, viewModel.uiState.value.recentRecords)
        verify(exactly = 0) { installedAppResolver.resolveLabel(any()) }
    }

    /**
     * 게시 기록이 없는 알림 제거 기록은 패키지별로 한 번만 조회하는지 검증
     *
     * Given: 같은 패키지의 알림 제거 기록 두 건(key는 게시 기록 없음)
     * When: 저장소가 기록을 내보내고, 이어서 목록이 갱신됨
     * Then: appLabel이 "라벨_com.fake", resolveLabel은 패키지당 1회
     */
    @Test
    fun test_13_removedRecordResolvesLabelOncePerPackage() = runTest {
        /** Given **/
        val viewModel = createViewModel()
        val first = NotificationRemovedRecord(key = "a", packageName = "com.fake", time = 1_000L, reason = RemovalReason.DISMISS)
        val second = NotificationRemovedRecord(key = "b", packageName = "com.fake", time = 2_000L, reason = RemovalReason.OTHER)
        val expected = listOf(
            UsageLogRecordItem(2L, second, "라벨_com.fake"),
            UsageLogRecordItem(1L, first, "라벨_com.fake"),
        )

        /** When **/
        recentRecords.value = listOf(StoredUsageLogRecord(2L, second), StoredUsageLogRecord(1L, first))
        awaitRecentRecords(viewModel, expected)
        recentRecords.value = listOf(StoredUsageLogRecord(3L, first))
        awaitRecentRecords(viewModel, listOf(UsageLogRecordItem(3L, first, "라벨_com.fake")))

        /** Then **/
        verify(exactly = 1) { installedAppResolver.resolveLabel("com.fake") }
    }

    /**
     * 웹후크 주소 입력이 앞뒤 공백을 제거해 저장되고 유효 여부가 저장된 값을 따르는지 검증
     *
     * Given: ViewModel 생성
     * When: 앞뒤 공백·개행이 붙은 올바른 주소로 updateWebhookUrl 호출
     * Then: 공백 없는 주소로 setWebhookUrl 호출, webhookUrl 반영, webhookUrlValid true
     */
    @Test
    fun test_14_updateWebhookUrlSavesTrimmed() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        viewModel.updateWebhookUrl("  https://discord.com/api/webhooks/1/abc\n")
        waitCoroutine()

        /** Then **/
        coVerify { settings.setWebhookUrl("https://discord.com/api/webhooks/1/abc") }
        assertEquals("https://discord.com/api/webhooks/1/abc", viewModel.uiState.value.webhookUrl)
        assertTrue(viewModel.uiState.value.webhookUrlValid)
    }

    /**
     * 지금 보내기가 주소 유효 여부에 따라 수동 전송 예약 또는 토스트로 갈리는지 검증
     *
     * Given: 저장된 주소가 올바르지 않은 상태
     * When: sendNow 호출, 이어서 올바른 주소가 저장된 뒤 다시 호출
     * Then: 처음엔 EventShowToast(usage_log_webhook_invalid)와 예약 없음, 다음엔 enqueueSend(MANUAL) 1회
     */
    @Test
    fun test_15_sendNowEnqueuesManual() = runTest {
        /** Given **/
        webhookUrl.value = "not a url"
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.sendNow()
        waitCoroutine()
        val toastOnly = receivedEvents.toList()
        verify(exactly = 0) { scheduler.enqueueSend(any()) }
        webhookUrl.value = "https://discord.com/api/webhooks/1/abc"
        waitCoroutine()
        viewModel.sendNow()
        waitCoroutine()

        /** Then **/
        assertEquals(
            listOf<ViewModelEvent>(UsageLogViewModel.EventShowToast(R.string.usage_log_webhook_invalid)),
            toastOnly,
        )
        verify(exactly = 1) { scheduler.enqueueSend(SendReason.MANUAL) }
    }

    /**
     * 키 새로 만들기가 확인 뒤에만 새 32바이트 키를 저장하는지 검증
     *
     * Given: 기존 키가 저장된 상태
     * When: requestRegenerateKey, 이어서 confirmRegenerateKey 호출
     * Then: 요청 뒤 확인 대화상자 표시, 저장 전 setEncryptionKey 미호출, 확인 뒤 기존과 다른 32바이트 키 저장과 대화상자 닫힘
     */
    @Test
    fun test_16_regenerateKeyAfterConfirm() = runTest {
        /** Given **/
        val oldKey = UsageLogCipher.encodeKey(UsageLogCipher.generateKey())
        encryptionKey.value = oldKey
        val viewModel = createViewModel()

        /** When **/
        viewModel.requestRegenerateKey()
        val shownAfterRequest = viewModel.uiState.value.showRegenerateKeyConfirm
        coVerify(exactly = 0) { settings.setEncryptionKey(any()) }
        viewModel.confirmRegenerateKey()
        waitCoroutine()

        /** Then **/
        assertTrue(shownAfterRequest)
        assertFalse(viewModel.uiState.value.showRegenerateKeyConfirm)
        val newKey = viewModel.uiState.value.encryptionKey
        assertNotEquals(oldKey, newKey)
        assertEquals(UsageLogCipher.KEY_SIZE_BYTES, UsageLogCipher.decodeKey(newKey)?.size)
        coVerify(exactly = 1) { settings.setEncryptionKey(newKey) }
    }

    /**
     * 키 복사가 키를 담은 복사 이벤트를 보내는지 검증
     *
     * Given: 저장된 키가 있는 상태
     * When: copyEncryptionKey 호출
     * Then: EventCopyEncryptionKey(저장된 키)
     */
    @Test
    fun test_17_copyEncryptionKeySendsEvent() = runTest {
        /** Given **/
        encryptionKey.value = "fakeKey="
        val viewModel = createViewModel()
        val receivedEvents = collectEvents(viewModel)

        /** When **/
        viewModel.copyEncryptionKey()

        /** Then **/
        assertEquals(
            listOf<ViewModelEvent>(UsageLogViewModel.EventCopyEncryptionKey("fakeKey=")),
            receivedEvents,
        )
    }

    /**
     * 대기 건수와 마지막 전송 결과가 저장소 Flow를 따르는지 검증
     *
     * Given: ViewModel 생성
     * When: 미전송 건수 23, 마지막 전송 결과(성공)가 바뀜
     * Then: pendingCount 23, lastSendResult가 그 결과
     */
    @Test
    fun test_18_pendingCountAndLastSendFollowRepository() = runTest {
        /** Given **/
        val viewModel = createViewModel()

        /** When **/
        unsentCount.value = 23
        lastSendResult.value = LastSendResult(1_000L, true)
        waitCoroutine()

        /** Then **/
        assertEquals(23, viewModel.uiState.value.pendingCount)
        assertEquals(LastSendResult(1_000L, true), viewModel.uiState.value.lastSendResult)
    }
}
