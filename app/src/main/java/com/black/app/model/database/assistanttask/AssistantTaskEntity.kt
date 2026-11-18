package com.black.app.model.database.assistanttask

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 비서 할 일 저장본의 DB 행
 *
 * 노션 페이지 하나가 행 하나. 기한은 날짜만 있으면 [dueDate]에 `yyyy-MM-dd`, 시각까지 있으면 [dueAt]에
 * epoch millis를 담고 둘 다 null이면 기한 없음. [kind]·[reminder]는 enum 이름 문자열.
 */
@Entity(tableName = "assistant_task")
data class AssistantTaskEntity(
    @PrimaryKey val pageId: String,
    val title: String,
    val kind: String,
    val done: Boolean,
    val dueDate: String?,
    val dueAt: Long?,
    val reminder: String,
    val content: String,
    val lastEditedTime: Long,
    val url: String,
    val pendingSync: Boolean,
)
