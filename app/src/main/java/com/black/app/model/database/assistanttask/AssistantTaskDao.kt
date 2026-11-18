package com.black.app.model.database.assistanttask

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AssistantTaskDao {
    @Query("SELECT * FROM assistant_task")
    abstract fun observeAll(): Flow<List<AssistantTaskEntity>>

    @Query("SELECT * FROM assistant_task")
    abstract suspend fun getAll(): List<AssistantTaskEntity>

    @Query("DELETE FROM assistant_task")
    abstract suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(entities: List<AssistantTaskEntity>)

    @Query("SELECT * FROM assistant_task WHERE pageId = :pageId")
    abstract suspend fun getById(pageId: String): AssistantTaskEntity?

    @Query("SELECT * FROM assistant_task WHERE pendingSync = 1")
    abstract suspend fun getPending(): List<AssistantTaskEntity>

    @Query("DELETE FROM assistant_task WHERE pageId = :pageId")
    abstract suspend fun deleteById(pageId: String)

    @Query("DELETE FROM assistant_task WHERE pendingSync = 0")
    abstract suspend fun deleteSynced()

    @Query("UPDATE assistant_task SET done = :done, pendingSync = 1, lastEditedTime = :changedAt WHERE pageId = :pageId")
    abstract suspend fun updateDone(pageId: String, done: Boolean, changedAt: Long)

    @Query("UPDATE assistant_task SET pageId = :newPageId, url = :url WHERE pageId = :pageId")
    abstract suspend fun updatePageIdAndUrl(pageId: String, newPageId: String, url: String)

    /**
     * 노션으로 보낸 결과 반영
     *
     * 현재 행이 [snapshot]과 `done`·`lastEditedTime`이 같으면 행을 지우고 [resolved]를 반영 대기 해제로 넣음.
     * 보내는 사이 다시 바뀌었으면 임시 ID 행만 [resolved]의 page ID·URL로 바꾸고 반영 대기는 유지.
     * 현재 행이 없으면(연결 교체로 비워진 경우) 아무것도 하지 않음. 한 트랜잭션으로 처리.
     */
    @Transaction
    open suspend fun resolvePending(snapshot: AssistantTaskEntity, resolved: AssistantTaskEntity?, snapshotIsLocalOnly: Boolean) {
        // 현재 행 확인
        val current = getById(snapshot.pageId) ?: return
        val unchanged = current.done == snapshot.done && current.lastEditedTime == snapshot.lastEditedTime
        if (unchanged) {
            // 보내는 사이 변경 없음: 보낸 행 교체
            deleteById(snapshot.pageId)
            if (resolved != null) insertAll(listOf(resolved.copy(pendingSync = false)))
        } else if (snapshotIsLocalOnly && resolved != null) {
            // 보내는 사이 변경: 임시 ID 행의 키만 교체
            updatePageIdAndUrl(snapshot.pageId, resolved.pageId, resolved.url)
        }
    }

    /**
     * 반영 대기 행을 남기고 나머지 저장본을 [synced]로 교체
     *
     * 비우기와 채우기를 한 트랜잭션으로 처리해 중간 상태가 관찰되지 않음.
     */
    @Transaction
    open suspend fun replaceSynced(synced: List<AssistantTaskEntity>) {
        // 반영 대기 행 확인
        val pendingIds = getPending().mapTo(HashSet()) { it.pageId }
        // 반영 대기 아닌 행 교체
        deleteSynced()
        insertAll(synced.filter { it.pageId !in pendingIds })
    }
}
