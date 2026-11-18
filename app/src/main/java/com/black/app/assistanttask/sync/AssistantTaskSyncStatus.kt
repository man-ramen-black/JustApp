package com.black.app.assistanttask.sync

/**
 * 비서 할 일 동기화 결과
 *
 * [STORAGE_ERROR]는 노션 요청이 아니라 기기의 저장본을 읽거나 쓰지 못한 경우.
 */
enum class AssistantTaskSyncStatus {
    SUCCESS,
    NOT_CONFIGURED,
    AUTH_ERROR,
    NETWORK_ERROR,
    SERVER_ERROR,
    REQUEST_ERROR,
    STORAGE_ERROR,
}

/** 마지막 동기화 시도의 시각과 결과 */
data class AssistantTaskSyncState(val timeMillis: Long, val status: AssistantTaskSyncStatus)
