package com.black.app.usagelog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.model.database.usagelog.UsageLogDatabase
import com.black.app.model.datastore.UsageLogDataStore
import com.black.app.usagelog.record.GapCause
import com.black.app.usagelog.record.GapRecord
import com.black.app.usagelog.record.OpenUsageSession
import com.black.app.usagelog.record.WifiState
import com.black.app.usagelog.work.UsageLogScheduler
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

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
     * Given: 켜진 상태와 저장된 열린 구간
     * When: 시각 7000에 disable
     * Then: 열린 구간 null, 꺼진 시각 7000, 꺼짐, 주기 작업 취소
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
        assertEquals(7000L, settings.getCollectionOffSince())
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
}
