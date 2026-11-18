package com.black.app.assistanttask

import java.util.UUID

/** 비서 할 일 종류 */
enum class AssistantTaskKind { TASK, MEMO }

/** 비서 할 일 알림 방식 */
enum class AssistantTaskReminder { HOME_ARRIVAL, TIME, NONE }

/** 비서 할 일 기한 */
sealed interface AssistantTaskDue {
    /**
     * 날짜만 있는 기한
     *
     * [date]는 `yyyy-MM-dd` 형식.
     */
    data class DateOnly(val date: String) : AssistantTaskDue

    /** 시각까지 있는 기한 */
    data class DateTime(val epochMillis: Long) : AssistantTaskDue
}

/** 노션 `비서 할 일` 데이터베이스의 페이지 하나 */
data class AssistantTask(
    val pageId: String,
    val title: String,
    val kind: AssistantTaskKind,
    val done: Boolean,
    val due: AssistantTaskDue?,
    val reminder: AssistantTaskReminder,
    val content: String,
    val lastEditedTime: Long,
    val url: String,
    val pendingSync: Boolean = false,
) {
    /** 앱에서 추가해 아직 노션 page ID를 받지 못한 항목 */
    val isLocalOnly: Boolean get() = pageId.startsWith(LOCAL_ID_PREFIX)

    companion object {
        /** 노션에 보내기 전 항목의 임시 page ID 접두사 */
        const val LOCAL_ID_PREFIX = "local-"

        /** 임시 page ID 생성 */
        fun newLocalId(): String = LOCAL_ID_PREFIX + UUID.randomUUID()
    }
}
