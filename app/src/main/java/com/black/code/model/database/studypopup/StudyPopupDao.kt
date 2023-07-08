package com.black.code.model.database.studypopup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query


@Dao
interface StudyPopupDao {
    @Query("SELECT * FROM Contents")
    fun getAll(): List<StudyPopupData.Contents>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(vararg contents: StudyPopupData.Contents)

    @Query("SELECT COUNT(id) FROM Contents")
    fun getRowCount(): Int

    @Query("DELETE FROM Contents WHERE id > :beforeId")
    fun deleteAfter(beforeId: Int)
}