package com.black.app.usagelog.collect

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.model.database.usagelog.UsageLogDatabase
import com.black.app.model.datastore.UsageLogDataStore
import com.black.app.usagelog.record.AppUsageRecord
import com.black.app.usagelog.record.GapCause
import com.black.app.usagelog.record.GapRecord
import com.black.app.usagelog.record.OpenUsageSession
import com.black.app.usagelog.record.ScreenOnRecord
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.IOException

/** [UsageStatsCollector]의 수집 시점 판정, 커서·열린 구간 저장, 만료 공백 기록 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageStatsCollectorTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = UsageLogSettingsRepository(context)
    private lateinit var database: UsageLogDatabase
    private lateinit var repository: UsageLogRepository

    private val minute = 60L * 1000
    private val hour = 60 * minute
    private val day = 24 * hour
    private val now = 10 * day

    private val queries = ArrayList<Pair<Long, Long>>()
    private var events: List<RawUsageEvent>? = emptyList()

    private val collector by lazy { createCollector(settings) }

    private fun createCollector(
        settingsRepository: UsageLogSettingsRepository,
        usageLogRepository: UsageLogRepository = repository,
    ) =
        UsageStatsCollector(
            source = { begin, end ->
                queries.add(begin to end)
                events
            },
            settings = settingsRepository,
            repository = usageLogRepository,
            appNameOf = { "${it}앱" },
            clock = { now },
        )

    /**
     * 각 테스트 전 저장값을 비우고 메모리 DB 저장소 생성
     *
     * 실제 DataStore가 프로세스 단일 인스턴스라 테스트 간 공유되기 때문.
     */
    @Before
    fun setUp() {
        runTest { UsageLogDataStore(context).clear() }
        database = Room.inMemoryDatabaseBuilder(context, UsageLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = UsageLogRepository(database.dao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun savedRecords() = repository.getUnsent(100).map { it.record }

    /**
     * 수집이 꺼져 있으면 아무것도 읽지 않는지 검증
     *
     * Given: 수집 꺼짐, 커서 now-1시간
     * When: collect
     * Then: source 호출 없음, 커서 그대로
     */
    @Test
    fun test_01_disabledCollectsNothing() = runTest {
        /** Given **/
        settings.setUsageCursor(now - hour)

        /** When **/
        collector.collect()

        /** Then **/
        assertEquals(emptyList<Pair<Long, Long>>(), queries)
        assertEquals(now - hour, settings.getUsageCursor())
    }

    /**
     * 현재보다 10분 전까지 읽고 커서가 그 시각으로 옮겨지는지 검증
     *
     * Given: 수집 켜짐, 커서 now-1시간
     * When: collect
     * Then: query(커서, now-10분), 저장된 커서 now-10분
     */
    @Test
    fun test_02_readsUntilTenMinutesAgoAndMovesCursor() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setUsageCursor(now - hour)

        /** When **/
        collector.collect()

        /** Then **/
        assertEquals(listOf((now - hour) to (now - 10 * minute)), queries)
        assertEquals(now - 10 * minute, settings.getUsageCursor())
    }

    /**
     * 최근 10분만 지났으면 다음 수집으로 미루는지 검증
     *
     * Given: 수집 켜짐, 커서 now-5분
     * When: collect
     * Then: source 호출 없음
     */
    @Test
    fun test_03_recentTenMinutesDeferred() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setUsageCursor(now - 5 * minute)

        /** When **/
        collector.collect()

        /** Then **/
        assertEquals(emptyList<Pair<Long, Long>>(), queries)
        assertEquals(now - 5 * minute, settings.getUsageCursor())
    }

    /**
     * 커서가 오래돼 이벤트가 만료됐으면 공백 기록을 남기고 열린 구간을 버리는지 검증
     *
     * Given: 커서 now-5일, 저장된 열린 구간, 첫 이벤트 now-2일
     * When: collect
     * Then: GapRecord(커서, now-2일, USAGE_EVENTS_EXPIRED) 저장, 열린 구간 없음
     */
    @Test
    fun test_04_expiredGapWhenCursorIsOld() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setUsageCursor(now - 5 * day)
        settings.setOpenSession(OpenUsageSession("old", now - 6 * day, null))
        events = listOf(RawUsageEvent(RawUsageEventKind.SCREEN_ON, null, now - 2 * day))

        /** When **/
        collector.collect()

        /** Then **/
        val gap = GapRecord(now - 5 * day, now - 2 * day, GapCause.USAGE_EVENTS_EXPIRED)
        assertEquals(true, savedRecords().contains(gap))
        assertNull(settings.getOpenSession())
    }

    /**
     * 첫 이벤트가 커서 가까이에서 시작하면 공백 기록을 남기지 않는지 검증
     *
     * Given: 커서 now-5일, 첫 이벤트 커서+10분
     * When: collect
     * Then: 공백 기록 없음
     */
    @Test
    fun test_05_noGapWhenEventsStartNearCursor() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setUsageCursor(now - 5 * day)
        events = listOf(RawUsageEvent(RawUsageEventKind.SCREEN_ON, null, now - 5 * day + 10 * minute))

        /** When **/
        collector.collect()

        /** Then **/
        assertEquals(false, savedRecords().any { it is GapRecord })
    }

    /**
     * 닫히지 않은 구간이 저장되는지 검증
     *
     * Given: 수집 켜짐, a 실행 이벤트 now-30분
     * When: collect
     * Then: 열린 구간 OpenUsageSession(a, now-30분, null)
     */
    @Test
    fun test_06_openSessionPersisted() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setUsageCursor(now - hour)
        events = listOf(RawUsageEvent(RawUsageEventKind.RESUMED, "a", now - 30 * minute))

        /** When **/
        collector.collect()

        /** Then **/
        assertEquals(OpenUsageSession("a", now - 30 * minute, null), settings.getOpenSession())
    }

    /**
     * 정확히 3일이면 만료 확인 대상이 아닌지 검증
     *
     * Given: 커서가 end보다 정확히 3일 앞, 첫 이벤트는 커서+2시간
     * When: collect
     * Then: 공백 기록 없음
     */
    @Test
    fun test_07_noGapWhenExactlyThreeDays() = runTest {
        /** Given **/
        val cursor = now - 10 * minute - 3 * day
        settings.setCollectionEnabled(true)
        settings.setUsageCursor(cursor)
        events = listOf(RawUsageEvent(RawUsageEventKind.SCREEN_ON, null, cursor + 2 * hour))

        /** When **/
        collector.collect()

        /** Then **/
        assertEquals(false, savedRecords().any { it is GapRecord })
    }

    /**
     * 3일을 넘고 이벤트가 하나도 없으면 end까지 공백 기록을 남기는지 검증
     *
     * Given: 커서 now-5일, 이벤트 없음
     * When: collect
     * Then: GapRecord(커서, now-10분, USAGE_EVENTS_EXPIRED)
     */
    @Test
    fun test_08_gapUntilEndWhenNoEventsAndOld() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setUsageCursor(now - 5 * day)

        /** When **/
        collector.collect()

        /** Then **/
        val gap = GapRecord(now - 5 * day, now - 10 * minute, GapCause.USAGE_EVENTS_EXPIRED)
        assertEquals(listOf(gap), savedRecords())
    }

    /**
     * 커서가 end와 같으면 조회하지 않는지 검증
     *
     * Given: 커서 now-10분(end와 같음)
     * When: collect
     * Then: source 호출 없음
     */
    @Test
    fun test_09_cursorEqualsEndSkipsQuery() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setUsageCursor(now - 10 * minute)

        /** When **/
        collector.collect()

        /** Then **/
        assertEquals(emptyList<Pair<Long, Long>>(), queries)
    }

    /**
     * 저장돼 있던 열린 구간이 새 이벤트로 이어져 닫히는지 검증
     *
     * Given: 저장된 열린 구간 a(now-2시간), 이벤트 a 일시정지 now-90분, 화면 꺼짐 now-80분
     * When: collect
     * Then: a 구간 now-2시간~now-90분 저장, 열린 구간 없음
     */
    @Test
    fun test_10_storedOpenSessionContinuesAndCloses() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setUsageCursor(now - 3 * hour)
        settings.setOpenSession(OpenUsageSession("a", now - 2 * hour, null))
        events = listOf(
            RawUsageEvent(RawUsageEventKind.PAUSED, "a", now - 90 * minute),
            RawUsageEvent(RawUsageEventKind.SCREEN_OFF, null, now - 80 * minute),
        )

        /** When **/
        collector.collect()

        /** Then **/
        assertEquals(true, savedRecords().contains(AppUsageRecord("a", "a앱", now - 2 * hour, now - 90 * minute)))
        assertNull(settings.getOpenSession())
    }

    /**
     * 열린 구간과 커서 저장이 실패하면 둘 다 이전 값으로 남는지 검증
     *
     * Given: 커서 now-1시간, 저장된 열린 구간 a(now-2시간), 이벤트 a 일시정지 now-50분과 b 실행 now-40분, 진행 상태 저장이 실패하는 설정 저장소
     * When: collect
     * Then: 커서 now-1시간과 열린 구간 a(now-2시간) 그대로
     */
    @Test
    fun test_11_progressKeptWhenSaveFails() = runTest {
        /** Given **/
        val open = OpenUsageSession("a", now - 2 * hour, null)
        settings.setCollectionEnabled(true)
        settings.setUsageCursor(now - hour)
        settings.setOpenSession(open)
        events = listOf(
            RawUsageEvent(RawUsageEventKind.PAUSED, "a", now - 50 * minute),
            RawUsageEvent(RawUsageEventKind.RESUMED, "b", now - 40 * minute),
        )
        val failing = spyk(settings)
        coEvery { failing.setUsageProgress(any(), any()) } returns Result.failure(IOException("save failed"))

        /** When **/
        createCollector(failing).collect()

        /** Then **/
        assertEquals(now - hour, settings.getUsageCursor())
        assertEquals(open, settings.getOpenSession())
    }

    /**
     * 사용 정보 접근 권한이 없어 조회 결과가 null이면 커서와 열린 구간을 건드리지 않는지 검증
     *
     * Given: source가 null, 저장된 열린 구간 있음
     * When: collect
     * Then: 커서와 열린 구간 그대로, 저장된 기록 없음
     */
    @Test
    fun test_12_noPermissionKeepsCursor() = runTest {
        /** Given **/
        val open = OpenUsageSession("a", now - 2 * hour, null)
        settings.setCollectionEnabled(true)
        settings.setUsageCursor(now - hour)
        settings.setOpenSession(open)
        events = null

        /** When **/
        collector.collect()

        /** Then **/
        assertEquals(now - hour, settings.getUsageCursor())
        assertEquals(open, settings.getOpenSession())
        assertEquals(emptyList<Any>(), savedRecords())
    }

    /**
     * 기록 저장 직후 취소돼도 진행 상태까지 저장해 다음 수집이 같은 기록을 다시 넣지 않는지 검증
     *
     * Given: 수집 켜짐, 커서 now-1시간, 화면 켜짐 이벤트 now-30분, 기록 추가 직후 수집 작업을 취소하는 저장소
     * When: 취소되는 수집 뒤 같은 이벤트로 다시 collect
     * Then: 저장된 기록은 ScreenOnRecord(now-30분) 하나, 커서 now-10분
     */
    @Test
    fun test_13_cancelDuringSaveDoesNotDuplicate() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setUsageCursor(now - hour)
        events = listOf(RawUsageEvent(RawUsageEventKind.SCREEN_ON, null, now - 30 * minute))
        lateinit var collectJob: Job
        // callOriginal은 실제로 중단되는 함수에서 호출부를 두 번 재개하므로 실제 저장소를 직접 호출
        val cancellingRepository = mockk<UsageLogRepository>()
        coEvery { cancellingRepository.add(any(), any()) } coAnswers {
            repository.add(firstArg(), secondArg())
            collectJob.cancel()
        }
        val cancellingCollector = createCollector(settings, cancellingRepository)

        /** When **/
        collectJob = launch { cancellingCollector.collect() }
        collectJob.join()
        collector.collect()

        /** Then **/
        assertEquals(listOf(ScreenOnRecord(now - 30 * minute)), savedRecords())
        assertEquals(now - 10 * minute, settings.getUsageCursor())
    }
}
