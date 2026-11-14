package com.black.app.usagelog

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.model.database.usagelog.UsageLogDatabase
import com.black.app.model.datastore.UsageLogDataStore
import com.black.app.usagelog.collect.UsageStatsCollector
import com.black.app.usagelog.record.GapCause
import com.black.app.usagelog.record.GapRecord
import com.black.app.usagelog.record.OpenUsageSession
import com.black.app.usagelog.record.ScreenOnRecord
import com.black.app.usagelog.record.WifiState
import com.black.app.usagelog.work.UsageLogScheduler
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.IOException

/** [UsageLogCollectionController]의 수집 켜기·끄기 상태 전환과 주기 작업 예약 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogCollectionControllerTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = UsageLogSettingsRepository(context)
    private lateinit var database: UsageLogDatabase
    private lateinit var repository: UsageLogRepository
    private val scheduler = mockk<UsageLogScheduler>()
    private var now = 0L

    /**
     * 각 테스트 전 저장값 삭제와 메모리 DB, 시계 초기화
     *
     * 실제 DataStore가 프로세스 단일 인스턴스라 테스트 간 공유되기 때문.
     */
    @Before
    fun setupController() {
        runTest { UsageLogDataStore(context).clear() }
        database = Room.inMemoryDatabaseBuilder(context, UsageLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = UsageLogRepository(database.dao())
        now = 0L
        every { scheduler.schedulePeriodicCollect() } just Runs
        every { scheduler.cancelPeriodicCollect() } just Runs
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun createController() = UsageLogCollectionController(settings, repository, scheduler) { now }

    /**
     * 처음 켜면 공백 기록 없이 켜지고 주기 작업이 예약되는지 검증
     *
     * Given: 꺼진 기록이 없는 초기 상태
     * When: enable 호출
     * Then: 미전송 기록 없음, 켜짐, 주기 작업 예약
     */
    @Test
    fun test_01_firstEnableAddsNoGapAndSchedules() = runTest {
        /** Given **/
        val controller = createController()

        /** When **/
        val result = controller.enable()

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals(emptyList<Any>(), repository.getUnsent(10))
        assertTrue(settings.isCollectionEnabled())
        verify { scheduler.schedulePeriodicCollect() }
    }

    /**
     * 껐다 다시 켜면 꺼진 구간이 공백 기록으로 남고 커서가 옮겨지는지 검증
     *
     * Given: 시각 1000에 disable
     * When: 시각 5000에 enable
     * Then: GapRecord(1000, 5000, COLLECTION_OFF) 추가, 커서 5000, 꺼진 시각 0
     */
    @Test
    fun test_02_reEnableAddsCollectionOffGap() = runTest {
        /** Given **/
        val controller = createController()
        now = 1000L
        controller.disable()

        /** When **/
        now = 5000L
        controller.enable()

        /** Then **/
        val records = repository.getUnsent(10).map { it.record }
        assertEquals(listOf(GapRecord(1000L, 5000L, GapCause.COLLECTION_OFF)), records)
        assertEquals(5000L, settings.getUsageCursor())
        assertEquals(0L, settings.getCollectionOffSince())
    }

    /**
     * 끄면 열린 구간이 지워지고 꺼진 시각이 저장되며 주기 작업이 취소되는지 검증
     *
     * Given: 켜진 상태와 저장된 열린 구간(시작 100)
     * When: 시각 7000에 disable
     * Then: 열린 구간 null, 꺼진 시각은 열린 구간 시작 100, 꺼짐, 주기 작업 취소
     */
    @Test
    fun test_03_disableClearsOpenSessionAndCancels() = runTest {
        /** Given **/
        val controller = createController()
        controller.enable()
        settings.setOpenSession(OpenUsageSession("com.example.app", 100L))
        now = 7000L

        /** When **/
        val result = controller.disable()

        /** Then **/
        assertTrue(result.isSuccess)
        assertNull(settings.getOpenSession())
        assertEquals(100L, settings.getCollectionOffSince())
        assertEquals(false, settings.isCollectionEnabled())
        verify { scheduler.cancelPeriodicCollect() }
    }

    /**
     * 켤 때 저장된 Wi-Fi 상태와 커서가 초기화되는지 검증
     *
     * Given: 마지막 Wi-Fi (true, MyHome_5G), 커서 10
     * When: 시각 9000에 enable
     * Then: Wi-Fi 상태 WifiState(false, null), 커서 9000
     */
    @Test
    fun test_04_enableResetsWifiStateAndCursor() = runTest {
        /** Given **/
        val controller = createController()
        settings.setLastWifiState(WifiState(true, "MyHome_5G"))
        settings.setUsageCursor(10L)
        now = 9000L

        /** When **/
        controller.enable()

        /** Then **/
        assertEquals(WifiState(false, null), settings.getLastWifiState())
        assertEquals(9000L, settings.getUsageCursor())
    }

    /**
     * 켜져 있을 때만 ensureScheduled가 예약하는지 검증
     *
     * Given: 꺼진 상태
     * When: ensureScheduled 호출 후 켜고 다시 호출
     * Then: 꺼짐에서는 예약 없음, 켜짐에서는 예약 한 번
     */
    @Test
    fun test_05_ensureScheduledOnlyWhenEnabled() = runTest {
        /** Given **/
        val controller = createController()

        /** When / Then **/
        controller.ensureScheduled()
        verify(exactly = 0) { scheduler.schedulePeriodicCollect() }
        settings.setCollectionEnabled(true)
        controller.ensureScheduled()
        verify(exactly = 1) { scheduler.schedulePeriodicCollect() }
    }

    /**
     * 공백 기록 추가가 실패하면 실패 결과를 돌려주고 이후 단계를 하지 않는지 검증
     *
     * Given: 꺼진 시각이 저장되고 repository.add가 예외를 던짐
     * When: enable 호출
     * Then: Result.failure, 켜짐 미저장, 예약 호출 없음
     */
    @Test
    fun test_06_enableReturnsFailureWhenGapAddFails() = runTest {
        /** Given **/
        val failure = IOException("add failed")
        val failingRepository = mockk<UsageLogRepository>()
        coEvery { failingRepository.add(any(), any()) } throws failure
        settings.setCollectionOffSince(1000L)
        now = 5000L
        val controller = UsageLogCollectionController(settings, failingRepository, scheduler) { now }

        /** When **/
        val result = controller.enable()

        /** Then **/
        assertSame(failure, result.exceptionOrNull())
        assertEquals(false, settings.isCollectionEnabled())
        verify(exactly = 0) { scheduler.schedulePeriodicCollect() }
    }

    /**
     * 켜면 암호화 키가 만들어지는지 검증
     *
     * Given: 암호화 키 없음
     * When: enable 호출
     * Then: 성공, 저장된 암호화 키가 비어 있지 않음
     */
    @Test
    fun test_07_enableCreatesEncryptionKey() = runTest {
        /** Given **/
        val controller = createController()

        /** When **/
        val result = controller.enable()

        /** Then **/
        assertTrue(result.isSuccess)
        assertTrue(settings.getEncryptionKey().isNotEmpty())
    }

    /**
     * 켜져 있을 때만 ensureScheduled가 암호화 키를 만드는지 검증
     *
     * Given: 꺼진 상태, 암호화 키 없음
     * When: ensureScheduled 호출 후 켜고 다시 호출
     * Then: 꺼짐에서는 키 없음, 켜짐에서는 키가 비어 있지 않음
     */
    @Test
    fun test_08_ensureScheduledCreatesKeyWhenEnabled() = runTest {
        /** Given **/
        val controller = createController()

        /** When **/
        controller.ensureScheduled()
        val keyWhenDisabled = settings.getEncryptionKey()
        settings.setCollectionEnabled(true)
        controller.ensureScheduled()

        /** Then **/
        assertEquals("", keyWhenDisabled)
        assertTrue(settings.getEncryptionKey().isNotEmpty())
    }

    /**
     * 끈 시각보다 앞선 커서와 열린 구간 시작부터 공백 기록이 시작되는지 검증
     *
     * Given: 켜진 상태, 커서 5000, 시작 4000인 열린 구간
     * When: 시각 7000에 disable, 시각 9000에 enable
     * Then: GapRecord(4000, 9000, COLLECTION_OFF) 하나
     */
    @Test
    fun test_09_disableGapStartsAtUnreadRange() = runTest {
        /** Given **/
        val controller = createController()
        controller.enable()
        settings.setUsageCursor(5000L)
        settings.setOpenSession(OpenUsageSession("com.example.app", 4000L))

        /** When **/
        now = 7000L
        controller.disable()
        now = 9000L
        controller.enable()

        /** Then **/
        val records = repository.getUnsent(10).map { it.record }
        assertEquals(listOf(GapRecord(4000L, 9000L, GapCause.COLLECTION_OFF)), records)
    }

    /**
     * 켤 때 켜짐 저장이 실패하면 꺼진 시각이 남아 다음 켜기에서 공백 기록을 잃지 않는지 검증
     *
     * Given: 꺼진 시각 1000, 켜짐 저장이 실패하는 설정 저장소
     * When: 시각 5000에 enable
     * Then: Result.failure, 꺼짐 그대로, 꺼진 시각 1000 유지, 예약 호출 없음
     */
    @Test
    fun test_10_enableKeepsOffSinceWhenEnabledSaveFails() = runTest {
        /** Given **/
        settings.setCollectionOffSince(1000L)
        val failingSettings = spyk(settings)
        coEvery { failingSettings.setCollectionEnabled(true) } returns Result.failure(IOException("save failed"))
        val controller = UsageLogCollectionController(failingSettings, repository, scheduler) { now }
        now = 5000L

        /** When **/
        val result = controller.enable()

        /** Then **/
        assertTrue(result.isFailure)
        assertEquals(false, settings.isCollectionEnabled())
        assertEquals(1000L, settings.getCollectionOffSince())
        verify(exactly = 0) { scheduler.schedulePeriodicCollect() }
    }

    /**
     * 끌 때 꺼진 시각 저장이 실패하면 꺼짐을 저장하지 않아 공백 기록 없이 꺼지지 않는지 검증
     *
     * Given: 켜진 상태, 꺼진 시각 저장이 실패하는 설정 저장소
     * When: 시각 7000에 disable
     * Then: Result.failure, 켜짐 그대로, 꺼진 시각 0, 주기 작업 취소 없음
     */
    @Test
    fun test_11_disableKeepsEnabledWhenOffSinceSaveFails() = runTest {
        /** Given **/
        createController().enable()
        val failingSettings = spyk(settings)
        coEvery { failingSettings.setCollectionOffSince(any()) } returns Result.failure(IOException("save failed"))
        val controller = UsageLogCollectionController(failingSettings, repository, scheduler) { now }
        now = 7000L

        /** When **/
        val result = controller.disable()

        /** Then **/
        assertTrue(result.isFailure)
        assertTrue(settings.isCollectionEnabled())
        assertEquals(0L, settings.getCollectionOffSince())
        verify(exactly = 0) { scheduler.cancelPeriodicCollect() }
    }

    /**
     * 수집 도중 끄고 켜도 수집이 끝난 뒤에 실행돼 커서가 켠 시각으로 남는지 검증
     *
     * Given: 켜진 상태, 커서 1000, 열린 구간을 읽는 시점에 멈추는 수집기(읽는 범위 끝 5000)
     * When: 수집이 멈춘 동안 시각 6000에 disable, 시각 9000에 enable을 시작한 뒤 수집 재개
     * Then: 수집이 멈춘 동안 켜기·끄기가 끝나지 않음, 커서 9000, GapRecord(5000, 9000, COLLECTION_OFF) 하나
     */
    @Test
    fun test_12_toggleDuringCollectKeepsEnableCursor() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setUsageCursor(1000L)
        val collectPaused = CompletableDeferred<Unit>()
        val resumeCollect = CompletableDeferred<Unit>()
        val pausingSettings = spyk(settings)
        // callOriginal은 실제로 중단되는 함수에서 호출부를 두 번 재개하므로 실제 설정 저장소를 직접 호출
        coEvery { pausingSettings.getOpenSession() } coAnswers {
            collectPaused.complete(Unit)
            resumeCollect.await()
            settings.getOpenSession()
        }
        val collector = UsageStatsCollector(
            source = { _, _ -> emptyList() },
            settings = pausingSettings,
            repository = repository,
            appNameOf = { it },
            clock = { UsageStatsCollector.SETTLE_DELAY_MILLIS + 5000L },
        )
        val controller = createController()

        /** When **/
        val collectJob = launch(Dispatchers.Default) { collector.collect() }
        collectPaused.await()
        val toggleJob = launch(Dispatchers.Default) {
            now = 6000L
            controller.disable()
            now = 9000L
            controller.enable()
        }
        // 수집이 멈춘 동안 켜기·끄기가 끝나는지 실제 시간으로 잠시 대기
        val finishedDuringCollect = withContext(Dispatchers.Default) {
            withTimeoutOrNull(500L) { toggleJob.join() } != null
        }
        resumeCollect.complete(Unit)
        collectJob.join()
        toggleJob.join()

        /** Then **/
        assertFalse(finishedDuringCollect)
        assertEquals(9000L, settings.getUsageCursor())
        val records = repository.getUnsent(10).map { it.record }
        assertEquals(listOf(GapRecord(5000L, 9000L, GapCause.COLLECTION_OFF)), records)
    }

    /**
     * 수집이 꺼져 있어도 ensureScheduled가 보관 기간이 지난 기록을 정리하는지 검증
     *
     * Given: 꺼진 상태, 시각 0에 저장한 미전송 ScreenOnRecord(500)
     * When: 보관 기간보다 1초 뒤에 ensureScheduled 호출
     * Then: 기록이 GapRecord(500, 500, UNSENT_DELETED)로 바뀜, 예약 호출 없음
     */
    @Test
    fun test_13_ensureScheduledPurgesExpiredWhenDisabled() = runTest {
        /** Given **/
        repository.add(listOf(ScreenOnRecord(500L)), now = 0L)
        val controller = createController()
        now = UsageLogRepository.RETENTION_MILLIS + 1000L

        /** When **/
        controller.ensureScheduled()

        /** Then **/
        val records = repository.getUnsent(10).map { it.record }
        assertEquals(listOf(GapRecord(500L, 500L, GapCause.UNSENT_DELETED)), records)
        verify(exactly = 0) { scheduler.schedulePeriodicCollect() }
    }

    /**
     * 보관 기간 정리가 실패해도 예약은 진행하고 로그에는 예외 클래스 이름만 남기는지 검증
     *
     * Given: 켜진 상태, purgeExpired가 메시지를 담은 IOException을 던지는 저장소
     * When: ensureScheduled 호출
     * Then: 예외 없이 예약 한 번, 경고 로그에 IOException은 있고 예외 메시지는 없음
     */
    @Test
    fun test_14_ensureScheduledContinuesWhenPurgeFails() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        val failingRepository = mockk<UsageLogRepository>()
        coEvery { failingRepository.purgeExpired(any()) } throws IOException("purge-detail")
        val controller = UsageLogCollectionController(settings, failingRepository, scheduler) { now }

        /** When **/
        controller.ensureScheduled()

        /** Then **/
        verify(exactly = 1) { scheduler.schedulePeriodicCollect() }
        verify { Log.w(any<String>(), match<String> { it.contains("IOException") }) }
        verify(exactly = 0) { Log.w(any<String>(), match<String> { it.contains("purge-detail") }) }
    }
}
