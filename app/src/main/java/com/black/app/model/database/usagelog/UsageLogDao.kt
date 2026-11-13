package com.black.app.model.database.usagelog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * 삭제 대상 미전송 기록의 time 범위
 *
 * 대상이 없으면 둘 다 null.
 */
data class UnsentTimeRange(val earliest: Long?, val latest: Long?)

@Dao
abstract class UsageLogDao {
    @Insert
    abstract suspend fun insertAll(entities: List<UsageLogEntity>)

    @Query("SELECT * FROM usage_log WHERE sent = 0 ORDER BY id ASC LIMIT :limit")
    abstract suspend fun getUnsent(limit: Int): List<UsageLogEntity>

    @Query("UPDATE usage_log SET sent = 1 WHERE id IN (:ids)")
    abstract suspend fun markSent(ids: List<Long>)

    @Query("DELETE FROM usage_log WHERE id IN (:ids)")
    abstract suspend fun deleteByIds(ids: List<Long>)

    /**
     * [ids]를 [chunkSize]개씩 나눠 전송됨으로 표시하며 한 트랜잭션으로 처리
     *
     * 중간에 실패하면 전부 되돌림.
     */
    @Transaction
    open suspend fun markSentInChunks(ids: List<Long>, chunkSize: Int) {
        ids.chunked(chunkSize).forEach { markSent(it) }
    }

    /**
     * [ids]를 [chunkSize]개씩 나눠 삭제하며 한 트랜잭션으로 처리
     *
     * 중간에 실패하면 전부 되돌림.
     */
    @Transaction
    open suspend fun deleteByIdsInChunks(ids: List<Long>, chunkSize: Int) {
        ids.chunked(chunkSize).forEach { deleteByIds(it) }
    }

    @Query("SELECT * FROM usage_log ORDER BY time DESC, id DESC LIMIT :limit")
    abstract fun recent(limit: Int): Flow<List<UsageLogEntity>>

    @Query("SELECT COUNT(*) FROM usage_log WHERE sent = 0")
    abstract fun unsentCount(): Flow<Int>

    /** 기준 시각 이전에 저장된 미전송 기록의 time 범위 */
    @Query("SELECT MIN(time) AS earliest, MAX(time) AS latest FROM usage_log WHERE sent = 0 AND createdAt < :threshold")
    abstract suspend fun getExpiredUnsentRange(threshold: Long): UnsentTimeRange

    @Query("DELETE FROM usage_log WHERE createdAt < :threshold")
    abstract suspend fun deleteExpired(threshold: Long)

    /**
     * 기준 시각 이전에 저장된 기록 삭제와 공백 기록 추가를 한 트랜잭션으로 처리
     *
     * 삭제한 기록 중 미전송이 있으면 그 time 범위로 [createGap]이 만든 행을 추가.
     */
    @Transaction
    open suspend fun purgeExpired(threshold: Long, createGap: (first: Long, last: Long) -> UsageLogEntity) {
        // 삭제 전에 미전송 범위 확인
        val range = getExpiredUnsentRange(threshold)
        deleteExpired(threshold)
        val first = range.earliest ?: return
        val last = range.latest ?: return
        // 미전송이 있었으면 공백 기록 추가
        insertAll(listOf(createGap(first, last)))
    }
}
