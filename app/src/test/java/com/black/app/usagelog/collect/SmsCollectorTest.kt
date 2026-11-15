package com.black.app.usagelog.collect

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.model.database.usagelog.UsageLogDatabase
import com.black.app.model.datastore.UsageLogDataStore
import com.black.app.usagelog.record.SmsDirection
import com.black.app.usagelog.record.SmsProgress
import com.black.app.usagelog.record.SmsRecord
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.IOException

/** [SmsCollector]의 조회 구간 판정, 중복 거르기, 진행 상태 저장 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SmsCollectorTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = UsageLogSettingsRepository(context)
    private lateinit var database: UsageLogDatabase
    private lateinit var repository: UsageLogRepository

    private val minute = 60L * 1000
    private val hour = 60 * minute
    private val day = 24 * hour
    private var now = 10 * day

    private val queries = ArrayList<Pair<Long, Long>>()
    private var messages: List<SmsRecord>? = emptyList()

    private fun createCollector(settingsRepository: UsageLogSettingsRepository = settings) = SmsCollector(
        source = { begin, end ->
            queries.add(begin to end)
            messages
        },
        settings = settingsRepository,
        repository = repository,
        clock = { now },
    )

    private fun message(id: Long, time: Long) = SmsRecord(
        time = time,
        messageKey = "sms:$id:$time",
        direction = SmsDirection.RECEIVED,
        peers = emptyList(),
        mms = false,
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
     * 진행 상태가 없으면 지금부터 모으도록 저장만 하는지 검증
     *
     * Given: 수집 켜짐, 진행 상태 없음
     * When: collect
     * Then: 조회 없음, 진행 상태 SmsProgress(now, now)
     */
    @Test
    fun test_01_firstCollectStoresNowWithoutQuery() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)

        /** When **/
        createCollector().collect()

        /** Then **/
        assertEquals(emptyList<Pair<Long, Long>>(), queries)
        assertEquals(SmsProgress(now, now), settings.getSmsProgress())
    }

    /**
     * 조회 구간이 [max(floor, cursor-24시간), 지금-2분)인지 검증
     *
     * Given: 수집 켜짐, floor now-5일, cursor now-1시간
     * When: collect
     * Then: query(cursor-24시간, now-2분)
     */
    @Test
    fun test_02_queryRangeUsesLookbackAndSettleDelay() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setSmsProgress(SmsProgress(now - 5 * day, now - hour))

        /** When **/
        createCollector().collect()

        /** Then **/
        assertEquals(listOf((now - hour - day) to (now - 2 * minute)), queries)
    }

    /**
     * 되돌아 읽는 범위가 floor 아래로 내려가지 않는지 검증
     *
     * Given: 수집 켜짐, floor now-30분, cursor now-10분
     * When: collect
     * Then: query(floor, now-2분)
     */
    @Test
    fun test_03_queryRangeNeverGoesBelowFloor() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setSmsProgress(SmsProgress(now - 30 * minute, now - 10 * minute))

        /** When **/
        createCollector().collect()

        /** Then **/
        assertEquals(listOf((now - 30 * minute) to (now - 2 * minute)), queries)
    }

    /**
     * 조회 결과를 저장하고 진행 상태를 옮기는지 검증
     *
     * Given: 수집 켜짐, 진행 상태 있음, 문자 2건 조회됨
     * When: collect
     * Then: 기록 2건 저장, cursor now-2분, seenKeys에 두 키
     */
    @Test
    fun test_04_savesRecordsAndMovesProgress() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setSmsProgress(SmsProgress(now - day, now - hour))
        val first = message(1, now - 30 * minute)
        val second = message(2, now - 20 * minute)
        messages = listOf(first, second)

        /** When **/
        createCollector().collect()

        /** Then **/
        assertEquals(listOf(first, second), savedRecords())
        assertEquals(
            SmsProgress(now - day, now - 2 * minute, setOf(first.messageKey, second.messageKey)),
            settings.getSmsProgress(),
        )
    }

    /**
     * 이미 기록한 문자는 다시 저장하지 않고 조회에 없는 옛 키는 빠지는지 검증
     *
     * Given: seenKeys에 a와 옛 키, 조회 결과 a와 b
     * When: collect
     * Then: b만 저장, seenKeys는 a와 b
     */
    @Test
    fun test_05_skipsSeenKeysAndDropsStaleKeys() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        val seen = message(1, now - 30 * minute)
        val fresh = message(2, now - 20 * minute)
        settings.setSmsProgress(SmsProgress(now - day, now - hour, setOf(seen.messageKey, "sms:99:1")))
        messages = listOf(seen, fresh)

        /** When **/
        createCollector().collect()

        /** Then **/
        assertEquals(listOf(fresh), savedRecords())
        assertEquals(setOf(seen.messageKey, fresh.messageKey), settings.getSmsProgress()?.seenKeys)
    }

    /**
     * 같은 키가 두 번 와도 한 번만 저장하는지 검증
     *
     * Given: 같은 키의 문자가 두 번 조회됨
     * When: collect
     * Then: 기록 1건
     */
    @Test
    fun test_06_duplicateKeyStoredOnce() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setSmsProgress(SmsProgress(now - day, now - hour))
        val duplicate = message(1, now - 30 * minute)
        messages = listOf(duplicate, duplicate)

        /** When **/
        createCollector().collect()

        /** Then **/
        assertEquals(listOf(duplicate), savedRecords())
    }

    /**
     * 구간 밖 시각의 문자는 저장하지도 seenKeys에 넣지도 않는지 검증
     *
     * Given: 구간 끝(now-2분) 이후 시각과 구간 시작 이전 시각의 문자가 조회됨
     * When: collect
     * Then: 기록 없음, seenKeys 비어 있음
     */
    @Test
    fun test_07_outOfRangeMessagesAreIgnored() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setSmsProgress(SmsProgress(now - day, now - hour))
        messages = listOf(message(1, now - minute), message(2, now - day - 1))

        /** When **/
        createCollector().collect()

        /** Then **/
        assertEquals(emptyList<Any>(), savedRecords())
        assertEquals(emptySet<String>(), settings.getSmsProgress()?.seenKeys)
    }

    /**
     * 조회가 null(권한 없음)이면 진행 상태와 기록이 그대로인지 검증
     *
     * Given: 수집 켜짐, 진행 상태 있음, 조회 결과 null
     * When: collect
     * Then: 진행 상태 그대로, 기록 없음
     */
    @Test
    fun test_08_nullQueryKeepsProgress() = runTest {
        /** Given **/
        val progress = SmsProgress(now - day, now - hour, setOf("sms:1:1"))
        settings.setCollectionEnabled(true)
        settings.setSmsProgress(progress)
        messages = null

        /** When **/
        createCollector().collect()

        /** Then **/
        assertEquals(progress, settings.getSmsProgress())
        assertEquals(emptyList<Any>(), savedRecords())
    }

    /**
     * 수집이 꺼져 있으면 조회하지 않는지 검증
     *
     * Given: 수집 꺼짐, 진행 상태 있음
     * When: collect
     * Then: 조회 없음, 진행 상태 그대로
     */
    @Test
    fun test_09_disabledCollectsNothing() = runTest {
        /** Given **/
        val progress = SmsProgress(now - day, now - hour)
        settings.setSmsProgress(progress)

        /** When **/
        createCollector().collect()

        /** Then **/
        assertEquals(emptyList<Pair<Long, Long>>(), queries)
        assertEquals(progress, settings.getSmsProgress())
    }

    /**
     * 구간 끝이 시작 이하이면 조회하지 않는지 검증
     *
     * Given: 수집 켜짐, floor와 cursor가 now-1분(켠 직후)
     * When: collect
     * Then: 조회 없음(queries 비어 있음), 진행 상태 그대로
     */
    @Test
    fun test_10_emptyRangeIsNotQueried() = runTest {
        /** Given **/
        val progress = SmsProgress(now - minute, now - minute)
        settings.setCollectionEnabled(true)
        settings.setSmsProgress(progress)

        /** When **/
        createCollector().collect()

        /** Then **/
        assertEquals(emptyList<Pair<Long, Long>>(), queries)
        assertEquals(progress, settings.getSmsProgress())
    }

    /**
     * 진행 상태 저장이 실패하면 다음 수집이 같은 구간을 다시 읽어 기록을 한 번 더 저장하는지 검증
     *
     * Given: 진행 상태 저장이 실패하는 설정 저장소, 문자 1건
     * When: collect 두 번
     * Then: 두 번 모두 같은 구간을 조회하고, 같은 기록이 두 번 저장되며, 저장된 진행 상태는 처음 그대로
     */
    @Test
    fun test_11_progressSaveFailureRereadsSameRange() = runTest {
        /** Given **/
        val progress = SmsProgress(now - day, now - hour)
        settings.setCollectionEnabled(true)
        settings.setSmsProgress(progress)
        val failingSettings = spyk(settings)
        coEvery { failingSettings.setSmsProgress(any()) } returns Result.failure(IOException("save failed"))
        val saved = message(1, now - 30 * minute)
        messages = listOf(saved)
        val collector = createCollector(failingSettings)

        /** When **/
        collector.collect()
        collector.collect()

        /** Then **/
        val range = (now - day) to (now - 2 * minute)
        assertEquals(listOf(range, range), queries)
        assertEquals(listOf(saved, saved), savedRecords())
        assertEquals(progress, settings.getSmsProgress())
    }

    /**
     * 진행 상태가 없는 채로 수집이 꺼져 있으면 아무것도 저장하지 않는지 검증
     *
     * Given: 수집 꺼짐, 진행 상태 없음
     * When: collect
     * Then: 진행 상태 없음
     */
    @Test
    fun test_12_disabledWithoutProgressStoresNothing() = runTest {
        /** Given / When **/
        createCollector().collect()

        /** Then **/
        assertNull(settings.getSmsProgress())
    }

    /**
     * 구간 시작 시각의 문자는 저장하고 구간 끝 시각의 문자는 저장하지 않는지 검증
     *
     * Given: 구간 [now-1시간, now-2분)의 시작 시각 문자와 끝 시각 문자
     * When: collect
     * Then: 시작 시각 문자만 저장
     */
    @Test
    fun test_13_boundaryStartIncludedEndExcluded() = runTest {
        /** Given **/
        val progress = SmsProgress(now - hour, now - hour)
        settings.setCollectionEnabled(true)
        settings.setSmsProgress(progress)
        val atBegin = message(1, now - hour)
        val atEnd = message(2, now - 2 * minute)
        messages = listOf(atBegin, atEnd)

        /** When **/
        createCollector().collect()

        /** Then **/
        assertEquals(listOf(atBegin), savedRecords())
        assertEquals(setOf(atBegin.messageKey), settings.getSmsProgress()?.seenKeys)
    }

    /**
     * 첫 수집이 빈 결과여도 다음 수집이 되돌아 읽어 이전 커서 앞 시각의 문자를 저장하는지 검증
     *
     * Given: 진행 상태 있음, 첫 조회는 빈 결과
     * When: 1시간 뒤 이전 커서보다 앞선 시각의 문자가 나타난 채로 다시 collect
     * Then: 그 문자가 저장됨
     */
    @Test
    fun test_14_lateMessageBeforePreviousCursorIsStored() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        settings.setSmsProgress(SmsProgress(now - day, now - hour))
        val collector = createCollector()
        collector.collect()
        val previousCursor = checkNotNull(settings.getSmsProgress()).cursor
        val late = message(1, previousCursor - 10 * minute)
        messages = listOf(late)

        /** When **/
        now += hour
        collector.collect()

        /** Then **/
        assertEquals(listOf(late), savedRecords())
    }

    /**
     * 시계가 되돌아가도 커서가 줄지 않고 이전 문자 키가 유지되는지 검증
     *
     * Given: cursor now+1시간, seenKeys에 a, 조회 결과에 b(시각이 구간 안)
     * When: collect
     * Then: cursor 그대로, seenKeys는 a와 b
     */
    @Test
    fun test_15_clockGoingBackwardsKeepsCursorAndKeys() = runTest {
        /** Given **/
        settings.setCollectionEnabled(true)
        val seen = message(1, now - 30 * minute)
        val fresh = message(2, now - 20 * minute)
        settings.setSmsProgress(SmsProgress(now - day, now + hour, setOf(seen.messageKey)))
        messages = listOf(fresh)

        /** When **/
        createCollector().collect()

        /** Then **/
        assertEquals(listOf(fresh), savedRecords())
        assertEquals(SmsProgress(now - day, now + hour, setOf(seen.messageKey, fresh.messageKey)), settings.getSmsProgress())
    }
}
