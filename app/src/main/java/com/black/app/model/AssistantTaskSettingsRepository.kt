package com.black.app.model

import android.content.Context
import com.black.app.assistanttask.sync.AssistantTaskSyncState
import com.black.app.model.datastore.AssistantTaskDataStore
import com.black.app.usagelog.collect.HomeTransition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** 노션 연결 정보 */
data class AssistantTaskConnection(val token: String, val dataSourceId: String) {
    /** 토큰과 data source ID가 모두 비어 있지 않은지 */
    val isConfigured: Boolean get() = token.isNotEmpty() && dataSourceId.isNotEmpty()
}

/**
 * 비서 할 일의 노션 연결 설정, 마지막 동기화 결과, 마지막 집 출발 시각, 시각 알림 예약 ID 집합 저장·조회
 *
 * 마지막 집 출발 시각은 실제 출발과 추정 출발을 따로 둠.
 */
class AssistantTaskSettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = AssistantTaskDataStore(context)

    val connection: Flow<AssistantTaskConnection>
        get() = combine(dataStore.getTokenFlow(), dataStore.getDataSourceIdFlow(), ::AssistantTaskConnection)

    suspend fun getConnection(): AssistantTaskConnection =
        AssistantTaskConnection(dataStore.getToken(), dataStore.getDataSourceId())

    suspend fun saveConnection(connection: AssistantTaskConnection): Result<Unit> =
        dataStore.updateConnection(connection.token, connection.dataSourceId)

    val lastSync: Flow<AssistantTaskSyncState?> get() = dataStore.getLastSyncFlow()

    suspend fun saveLastSync(state: AssistantTaskSyncState): Result<Unit> = dataStore.updateLastSync(state)

    suspend fun clearLastSync(): Result<Unit> = dataStore.removeLastSync()

    /**
     * 마지막 집 출발 시각, 기록이 없으면 null
     *
     * 값은 epoch 밀리초.
     */
    suspend fun getLastHomeLeaveTime(): Long? = dataStore.getLastHomeLeaveTime()

    suspend fun saveLastHomeLeaveTime(time: Long): Result<Unit> = dataStore.updateLastHomeLeaveTime(time)

    /**
     * 마지막 추정 집 출발 시각, 기록이 없으면 null
     *
     * 값은 epoch 밀리초.
     * 실제 출발 순간이 아니라 나중에 알게 된 상태를 맞춘 출발이라 실제 출발 시각과 따로 둠.
     * 추정 출발이 생기는 경우는 [HomeTransition]에 적음.
     */
    suspend fun getLastInferredHomeLeaveTime(): Long? = dataStore.getLastInferredHomeLeaveTime()

    suspend fun saveLastInferredHomeLeaveTime(time: Long): Result<Unit> = dataStore.updateLastInferredHomeLeaveTime(time)

    /**
     * 걸려 있을 수 있는 알람의 page ID 집합, 기록이 없으면 빈 집합
     *
     * 취소 실패·다시 예약 실패·늦게 울릴 유예 중인 ID 포함.
     */
    suspend fun getScheduledAlarmIds(): Set<String> = dataStore.getScheduledAlarmIds()

    suspend fun saveScheduledAlarmIds(ids: Set<String>): Result<Unit> = dataStore.updateScheduledAlarmIds(ids)
}
