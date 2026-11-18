package com.black.app.model.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.black.app.assistanttask.sync.AssistantTaskSyncState
import com.black.app.assistanttask.sync.AssistantTaskSyncStatus
import com.black.core.model.BaseDataStore
import com.black.core.model.PreferenceUpdate
import com.black.core.model.blackDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 비서 할 일의 노션 연결 설정, 마지막 동기화 결과, 마지막 집 출발 시각, 시각 알림 예약 ID 집합 저장소
 *
 * 마지막 집 출발 시각은 실제 출발과 추정 출발을 따로 둠.
 * 토큰과 data source ID는 비밀 값이라 로그에 값을 남기지 않음.
 */
class AssistantTaskDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : BaseDataStore(context) {

    companion object {
        private val Context.assistantTaskDataStore: DataStore<Preferences> by blackDataStore(name = "assistant_task")

        private val KEY_TOKEN = stringPreferencesKey("AssistantTaskToken")
        private val KEY_DATA_SOURCE_ID = stringPreferencesKey("AssistantTaskDataSourceId")
        private val KEY_LAST_SYNC_TIME = longPreferencesKey("AssistantTaskLastSyncTime")
        private val KEY_LAST_SYNC_STATUS = stringPreferencesKey("AssistantTaskLastSyncStatus")
        private val KEY_LAST_HOME_LEAVE_TIME = longPreferencesKey("AssistantTaskLastHomeLeaveTime")
        private val KEY_LAST_INFERRED_HOME_LEAVE_TIME = longPreferencesKey("AssistantTaskLastInferredHomeLeaveTime")
        private val KEY_SCHEDULED_ALARM_IDS = stringSetPreferencesKey("AssistantTaskScheduledAlarmIds")
    }

    override fun getDataStore(context: Context): DataStore<Preferences> = context.assistantTaskDataStore

    fun getTokenFlow(): Flow<String> = flow(KEY_TOKEN).map { it ?: "" }

    fun getDataSourceIdFlow(): Flow<String> = flow(KEY_DATA_SOURCE_ID).map { it ?: "" }

    suspend fun getToken(): String = get(KEY_TOKEN, "", logValue = false)

    suspend fun getDataSourceId(): String = get(KEY_DATA_SOURCE_ID, "", logValue = false)

    /** 토큰과 data source ID를 `edit` 한 번에 저장 */
    suspend fun updateConnection(token: String, dataSourceId: String): Result<Unit> = updateAll(
        PreferenceUpdate(KEY_TOKEN, token),
        PreferenceUpdate(KEY_DATA_SOURCE_ID, dataSourceId),
        logValue = false,
    )

    /**
     * 마지막 동기화 결과 흐름
     *
     * 저장값이 없거나 상태 이름을 알 수 없으면 null.
     */
    fun getLastSyncFlow(): Flow<AssistantTaskSyncState?> =
        combine(flow(KEY_LAST_SYNC_TIME), flow(KEY_LAST_SYNC_STATUS)) { time, statusName ->
            val status = AssistantTaskSyncStatus.entries.firstOrNull { it.name == statusName }
            if (time == null || status == null) null else AssistantTaskSyncState(time, status)
        }

    /** 마지막 동기화 시각과 상태를 `edit` 한 번에 저장 */
    suspend fun updateLastSync(state: AssistantTaskSyncState): Result<Unit> = updateAll(
        PreferenceUpdate(KEY_LAST_SYNC_TIME, state.timeMillis),
        PreferenceUpdate(KEY_LAST_SYNC_STATUS, state.status.name),
    )

    /** 마지막 동기화 시각과 상태를 `edit` 한 번에 삭제 */
    suspend fun removeLastSync(): Result<Unit> = updateAll(
        PreferenceUpdate(KEY_LAST_SYNC_TIME, null),
        PreferenceUpdate(KEY_LAST_SYNC_STATUS, null),
    )

    /** 마지막 집 출발 시각, 저장값이 없으면 null */
    suspend fun getLastHomeLeaveTime(): Long? = flow(KEY_LAST_HOME_LEAVE_TIME).first()

    /**
     * 마지막 집 출발 시각 저장
     *
     * 생활 패턴이 드러나는 값이라 로그에 남기지 않음.
     * 키 이름만 남겨도 그 logcat 시각이 곧 출발 시각이라 저장 로그 자체를 끔.
     */
    suspend fun updateLastHomeLeaveTime(time: Long): Result<Unit> =
        update(KEY_LAST_HOME_LEAVE_TIME, time, logWrite = false)

    /** 마지막 추정 집 출발 시각, 저장값이 없으면 null */
    suspend fun getLastInferredHomeLeaveTime(): Long? = flow(KEY_LAST_INFERRED_HOME_LEAVE_TIME).first()

    /**
     * 마지막 추정 집 출발 시각 저장
     *
     * 실제 출발 시각처럼 저장 로그 자체를 끔.
     */
    suspend fun updateLastInferredHomeLeaveTime(time: Long): Result<Unit> =
        update(KEY_LAST_INFERRED_HOME_LEAVE_TIME, time, logWrite = false)

    /**
     * 걸려 있을 수 있는 알람의 page ID 집합, 저장값이 없으면 빈 집합
     *
     * 취소 실패·다시 예약 실패·늦게 울릴 유예 중인 ID 포함.
     */
    suspend fun getScheduledAlarmIds(): Set<String> = get(KEY_SCHEDULED_ALARM_IDS, emptySet(), logValue = false)

    /** 걸려 있을 수 있는 알람의 page ID 집합 저장, 값은 로그에 남기지 않음 */
    suspend fun updateScheduledAlarmIds(ids: Set<String>): Result<Unit> =
        update(KEY_SCHEDULED_ALARM_IDS, ids, logValue = false)
}
