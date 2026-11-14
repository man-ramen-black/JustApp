package com.black.app.model.database.usagelog

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 기기 사용 기록 한 건의 DB 행
 *
 * [json]은 type을 포함한 기록 JSON 한 줄, [time]은 정렬용 기록 시각,
 * [createdAt]은 저장한 시각이며 보관 기간 계산의 기준.
 */
@Entity(tableName = "usage_log", indices = [Index("sent"), Index("time")])
data class UsageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val json: String,
    val time: Long,
    val sent: Boolean = false,
    val createdAt: Long,
)
