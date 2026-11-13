package com.black.app.model

import android.content.Context
import com.black.app.model.database.usagelog.UsageLogDao
import com.black.app.model.database.usagelog.UsageLogDatabase
import com.black.app.model.database.usagelog.UsageLogEntity
import com.black.app.usagelog.record.GapCause
import com.black.app.usagelog.record.GapRecord
import com.black.app.usagelog.record.UsageLogRecord
import com.black.app.usagelog.record.UsageLogRecordCodec
import com.black.core.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** DB에 저장된 기록과 그 행 id */
data class StoredUsageLogRecord(val id: Long, val record: UsageLogRecord)

/** 기기 사용 기록의 저장, 미전송 조회, 전송 표시, 보관 기간 정리 */
class UsageLogRepository(private val dao: UsageLogDao) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(UsageLogDatabase.getInstance(context).dao())

    /**
     * 기록 저장
     *
     * 저장 시각 [now]가 보관 기간의 기준.
     */
    suspend fun add(records: List<UsageLogRecord>, now: Long = System.currentTimeMillis()) {
        if (records.isEmpty()) return
        dao.insertAll(records.map { it.toEntity(now) })
    }

    /**
     * 미전송 기록 조회
     *
     * id 오름차순이며 복원할 수 없는 행은 결과에서 빼고 같은 호출에서 지움. 지운 행의 time 범위는
     * [GapCause.UNSENT_DELETED] 공백 기록으로 남기며 삭제와 공백 추가는 한 트랜잭션. [now]는 공백 기록의 저장 시각.
     * 빈 목록이면 미전송 기록이 더 없다는 뜻이며, 조회한 행이 모두 손상이었으면 지운 뒤 다시 조회함.
     */
    suspend fun getUnsent(limit: Int, now: Long = System.currentTimeMillis()): List<StoredUsageLogRecord> {
        while (true) {
            val entities = dao.getUnsent(limit)
            // 복원 가능한 행과 손상된 행 분리
            val stored = ArrayList<StoredUsageLogRecord>(entities.size)
            val corrupt = ArrayList<UsageLogEntity>()
            entities.forEach { entity ->
                val record = UsageLogRecordCodec.decode(entity.json)
                if (record == null) corrupt.add(entity) else stored.add(StoredUsageLogRecord(entity.id, record))
            }
            if (corrupt.isEmpty()) return stored

            // 손상된 행을 지우고 그 범위를 공백 기록으로 남김
            Log.w("[UsageLogRepository] 손상된 기록 ${corrupt.size}건 삭제, 공백 기록으로 대체")
            val gap = GapRecord(corrupt.minOf { it.time }, corrupt.maxOf { it.time }, GapCause.UNSENT_DELETED).toEntity(now)
            dao.deleteAndAddGap(corrupt.map { it.id }, MAX_IDS_PER_STATEMENT, gap)
            // 모두 손상이었으면 빈 목록이 "미전송 없음"으로 읽히지 않도록 뒤의 행과 공백 기록을 다시 조회
            if (stored.isNotEmpty()) return stored
        }
    }

    /**
     * 기록을 전송됨으로 표시
     *
     * [MAX_IDS_PER_STATEMENT]개씩 나눠 처리하되 한 호출은 한 트랜잭션이라 전부 표시되거나 하나도 표시되지 않음.
     */
    suspend fun markSent(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.markSentInChunks(ids, MAX_IDS_PER_STATEMENT)
    }

    /**
     * 최근 기록 조회
     *
     * time 내림차순이고 같으면 id 내림차순이며, 복원할 수 없는 행은 건너뜀.
     */
    fun recentRecords(limit: Int): Flow<List<StoredUsageLogRecord>> =
        dao.recent(limit).map { entities ->
            entities.mapNotNull { entity ->
                UsageLogRecordCodec.decode(entity.json)?.let { StoredUsageLogRecord(entity.id, it) }
            }
        }

    /** 전송되지 않은 기록 건수 */
    fun unsentCount(): Flow<Int> = dao.unsentCount()

    /**
     * 보관 기간이 지난 기록 삭제
     *
     * 지운 기록 중 미전송이 있으면 그 time 범위를 [GapCause.UNSENT_DELETED] 공백 기록으로 남김.
     * 삭제와 공백 추가는 한 트랜잭션.
     */
    suspend fun purgeExpired(now: Long) {
        dao.purgeExpired(now - RETENTION_MILLIS) { first, last ->
            GapRecord(first, last, GapCause.UNSENT_DELETED).toEntity(now)
        }
    }

    private fun UsageLogRecord.toEntity(now: Long) = UsageLogEntity(
        json = UsageLogRecordCodec.encode(this),
        time = time,
        createdAt = now,
    )

    companion object {
        /**
         * 문장 하나에 담는 id 개수 상한
         *
         * API 31 미만의 SQLite는 바인딩 변수를 999개까지만 허용하므로 그 아래로 잡음.
         */
        const val MAX_IDS_PER_STATEMENT = 900
        const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
