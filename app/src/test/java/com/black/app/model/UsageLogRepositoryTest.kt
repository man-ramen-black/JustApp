package com.black.app.model

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.black.app.model.database.usagelog.UsageLogDao
import com.black.app.model.database.usagelog.UsageLogDatabase
import com.black.app.model.database.usagelog.UsageLogEntity
import com.black.app.usagelog.record.AppUsageRecord
import com.black.app.usagelog.record.GapCause
import com.black.app.usagelog.record.GapRecord
import com.black.app.usagelog.record.ScreenOnRecord
import com.black.test.BaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [UsageLogRepository]의 기록 저장, 미전송 조회, 전송 표시, 보관 기간 정리 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogRepositoryTest : BaseTest() {

    private lateinit var database: UsageLogDatabase
    private lateinit var dao: UsageLogDao
    private lateinit var repository: UsageLogRepository

    private val day = 24L * 60 * 60 * 1000

    /**
     * 각 테스트 전 메모리 DB로 저장소 생성
     *
     * 테스트 간 격리용.
     */
    @Before
    fun createRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, UsageLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.dao()
        repository = UsageLogRepository(dao)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun screen(time: Long) = ScreenOnRecord(time)

    /**
     * 추가한 기록이 미전송으로 추가 순서대로 조회되는지 검증
     *
     * Given: 기록 3건
     * When: 추가 후 미전송 조회
     * Then: 추가 순서 그대로 반환
     */
    @Test
    fun test_01_addedRecordsAreUnsentInIdOrder() = runTest {
        /** Given **/
        val records = listOf(
            screen(300),
            AppUsageRecord("com.a", "A", 100, 200),
            GapRecord(1, 2, GapCause.COLLECTION_OFF),
        )

        /** When **/
        repository.add(records, now = 1000)

        /** Then **/
        assertEquals(records, repository.getUnsent(10).map { it.record })
    }

    /**
     * 전송 표시한 기록이 미전송 조회에서 빠지는지 검증
     *
     * Given: 기록 3건 추가
     * When: 첫 건 전송 표시
     * Then: 미전송 2건
     */
    @Test
    fun test_02_markSentExcludesFromUnsent() = runTest {
        /** Given **/
        repository.add(listOf(screen(1), screen(2), screen(3)), now = 1000)

        /** When **/
        repository.markSent(listOf(repository.getUnsent(10).first().id))

        /** Then **/
        assertEquals(2, repository.getUnsent(10).size)
    }

    /**
     * 최근 기록이 시각 내림차순이고 개수 제한을 지키는지 검증
     *
     * Given: time 100, 300, 200 순으로 추가
     * When: recentRecords(2) 조회
     * Then: time 300, 200
     */
    @Test
    fun test_03_recentRecordsNewestFirstLimited() = runTest {
        /** Given **/
        repository.add(listOf(screen(100), screen(300), screen(200)), now = 1000)

        /** When **/
        val recent = repository.recentRecords(2).first()

        /** Then **/
        assertEquals(listOf(300L, 200L), recent.map { it.record.time })
    }

    /**
     * 미전송 건수가 전송 표시를 따라 줄어드는지 검증
     *
     * Given: 기록 3건 추가
     * When: 1건 전송 표시
     * Then: 건수 3에서 2
     */
    @Test
    fun test_04_unsentCountFollowsMarkSent() = runTest {
        /** Given **/
        repository.add(listOf(screen(1), screen(2), screen(3)), now = 1000)
        assertEquals(3, repository.unsentCount().first())

        /** When **/
        repository.markSent(listOf(repository.getUnsent(10).first().id))

        /** Then **/
        assertEquals(2, repository.unsentCount().first())
    }

    /**
     * 보관 기간이 지난 기록을 지우고 지운 미전송 기록의 구간을 공백으로 남기는지 검증
     *
     * Given: 31일 전 저장된 미전송 2건(time 1000, 5000)과 전송됨 2건(time 100, 9000), 현재 저장된 미전송 1건
     * When: purgeExpired
     * Then: 남은 전체 기록이 최근 1건과 GapRecord(1000, 5000, UNSENT_DELETED)뿐
     */
    @Test
    fun test_05_purgeDeletesOldAndAddsGapForUnsent() = runTest {
        /** Given **/
        val now = 40 * day
        val old = now - 31 * day
        repository.add(listOf(screen(100), screen(9000)), now = old)
        repository.markSent(repository.getUnsent(10).map { it.id })
        repository.add(listOf(screen(1000), screen(5000)), now = old)
        val recent = screen(now)
        repository.add(listOf(recent), now = now)

        /** When **/
        repository.purgeExpired(now)

        /** Then **/
        val remaining = repository.recentRecords(10).first().map { it.record }
        assertEquals(listOf(recent, GapRecord(1000, 5000, GapCause.UNSENT_DELETED)), remaining)
    }

    /**
     * 지운 기록이 모두 전송됨이면 공백 없이 지우기만 하는지 검증
     *
     * Given: 31일 전 저장된 전송됨 기록 1건
     * When: purgeExpired
     * Then: 기록이 하나도 남지 않음
     */
    @Test
    fun test_06_purgeWithoutOldUnsentAddsNoGap() = runTest {
        /** Given **/
        val now = 40 * day
        repository.add(listOf(screen(1000)), now = now - 31 * day)
        repository.markSent(repository.getUnsent(10).map { it.id })

        /** When **/
        repository.purgeExpired(now)

        /** Then **/
        assertEquals(emptyList<Any>(), repository.recentRecords(10).first())
    }

    /**
     * 복원할 수 없는 행이 있어도 나머지 기록을 돌려주고 미전송 조회가 손상 행을 지우는지 검증
     *
     * Given: 정상 기록 2건 사이에 json이 깨진 미전송 행 1건
     * When: recentRecords와 getUnsent 조회
     * Then: 둘 다 정상 2건만 반환하고, getUnsent 이후 행 자체가 지워짐
     */
    @Test
    fun test_07_corruptRowIsSkippedAndDeletedOnGetUnsent() = runTest {
        /** Given **/
        repository.add(listOf(screen(1)), now = 1000)
        dao.insertAll(listOf(UsageLogEntity(json = "{broken", time = 2, createdAt = 1000)))
        repository.add(listOf(screen(3)), now = 1000)

        /** When **/
        val recent = repository.recentRecords(10).first()
        val unsent = repository.getUnsent(10)

        /** Then **/
        assertEquals(listOf(3L, 1L), recent.map { it.record.time })
        assertEquals(listOf(1L, 3L), unsent.map { it.record.time })
        assertEquals(2, dao.getUnsent(10).size)
    }

    /**
     * 한 문장의 id 상한보다 많은 id도 한 번의 호출로 모두 전송됨 표시되는지 검증
     *
     * Given: 미전송 기록 2000건
     * When: 전체 id로 markSent 한 번 호출
     * Then: 미전송 0건
     */
    @Test
    fun test_08_markSentHandlesMoreIdsThanStatementLimit() = runTest {
        /** Given **/
        repository.add(List(2000) { screen(it.toLong()) }, now = 1000)
        val ids = repository.getUnsent(5000).map { it.id }

        /** When **/
        repository.markSent(ids)

        /** Then **/
        assertEquals(2000, ids.size)
        assertEquals(0, repository.getUnsent(5000).size)
    }

    /**
     * 한 문장의 id 상한보다 많은 손상 행도 한 번의 조회로 모두 지워지는지 검증
     *
     * Given: json이 깨진 미전송 행 2000건과 정상 1건
     * When: getUnsent 호출
     * Then: 정상 1건만 반환하고 손상 행은 모두 지워짐
     */
    @Test
    fun test_09_corruptRowsMoreThanStatementLimitAreAllDeleted() = runTest {
        /** Given **/
        dao.insertAll(List(2000) { UsageLogEntity(json = "{broken", time = it.toLong(), createdAt = 1000) })
        repository.add(listOf(screen(5000)), now = 1000)

        /** When **/
        val unsent = repository.getUnsent(5000)

        /** Then **/
        assertEquals(1, unsent.size)
        assertEquals(1, dao.getUnsent(5000).size)
    }
}
