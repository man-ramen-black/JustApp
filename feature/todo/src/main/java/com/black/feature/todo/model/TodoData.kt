package com.black.feature.todo.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Todo 알림 방식 */
enum class TodoReminder { HOME_ARRIVAL, TIME, NONE }

/** Todo 기한 */
sealed interface TodoDue {
    /** 날짜만 있는 기한 */
    data class Date(val date: LocalDate) : TodoDue

    /** 시각까지 있는 기한 */
    data class DateTime(val instant: Instant) : TodoDue

    /**
     * 기한의 [zone] 기준 날짜
     *
     * 날짜만 있는 기한은 시간대와 무관하게 그 날짜 그대로.
     */
    fun localDate(zone: ZoneId): LocalDate = when (this) {
        is Date -> date
        is DateTime -> instant.atZone(zone).toLocalDate()
    }

    companion object {
        /**
         * 기한 정렬 기준
         *
         * [zone] 기준 날짜 오름차순이며 같은 날은 날짜만 있는 기한이 먼저, 그다음 시각 순.
         */
        fun comparator(zone: ZoneId): Comparator<TodoDue> = compareBy<TodoDue>(
            { it.localDate(zone) },
            { if (it is Date) 0 else 1 },
            { (it as? DateTime)?.instant },
        )
    }
}

/** 노션 `Todo` 데이터베이스의 페이지 하나 */
data class Todo(
    val pageId: String,
    val title: String,
    val done: Boolean,
    val due: TodoDue?,
    val reminder: TodoReminder,
    val content: String,
    val lastEditedTime: Instant,
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

/**
 * 기한 있는 Todo를 [TodoDue.comparator] 순서로 정렬
 *
 * Todo 화면 구획과 집 도착 알림 대상이 함께 씀. 기한 없는 항목이 섞이면 [IllegalArgumentException].
 */
fun List<Todo>.sortedByDue(zone: ZoneId): List<Todo> =
    sortedWith(compareBy(TodoDue.comparator(zone)) { todo: Todo -> requireNotNull(todo.due) })

/**
 * Todo 동기화 결과
 *
 * [STORAGE_ERROR]는 노션 요청이 아니라 기기의 저장본을 읽거나 쓰지 못한 경우.
 */
enum class TodoSyncStatus {
    SUCCESS,
    NOT_CONFIGURED,
    AUTH_ERROR,
    NETWORK_ERROR,
    SERVER_ERROR,
    REQUEST_ERROR,
    STORAGE_ERROR,
}

/** 마지막 동기화 시도의 시각과 결과 */
data class TodoSyncState(val timeMillis: Long, val status: TodoSyncStatus)

/** 노션 연결 정보 */
data class TodoConnection(val token: String, val dataSourceId: String) {
    /** 토큰과 data source ID가 모두 비어 있지 않은지 */
    val isConfigured: Boolean get() = token.isNotEmpty() && dataSourceId.isNotEmpty()
}
