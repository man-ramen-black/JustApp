package com.black.code.model.database.studypopup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query


@Dao
interface StudyPopupDao {
    @Query("SELECT * FROM Contents")
    suspend fun getAll(): List<StudyPopupData.Contents>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(vararg contents: StudyPopupData.Contents)

    @Query("SELECT COUNT(id) FROM Contents")
    suspend fun getRowCount(): Int

    @Query("DELETE FROM contents WHERE id > :beforeId")
    suspend fun deleteAfter(beforeId: Int)
}