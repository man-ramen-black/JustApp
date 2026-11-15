package com.black.app.model

import android.content.Context
import com.black.app.model.datastore.SleepRoutineDataStore
import com.black.app.sleeproutine.SleepRoutineSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** 수면 루틴 설정과 진행 중 세션 조회·저장 */
class SleepRoutineRepository @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = SleepRoutineDataStore(context)

    /** 진행 중 세션 종료 시각(epoch ms), 진행 중이 아니면 null */
    val sessionEndTime: Flow<Long?> = dataStore.sessionEndTimeFlow().map { it.takeIf { time -> time > 0L } }

    suspend fun getSettings(): SleepRoutineSettings = dataStore.getSettings()

    suspend fun saveSettings(settings: SleepRoutineSettings): Result<Unit> = dataStore.updateSettings(settings)

    /** 진행 중 세션 종료 시각(epoch ms), 진행 중이 아니면 null */
    suspend fun getSessionEndTime(): Long? = dataStore.getSessionEndTime().takeIf { it > 0L }

    suspend fun startSession(endTimeMillis: Long): Result<Unit> = dataStore.updateSessionEndTime(endTimeMillis)

    suspend fun clearSession(): Result<Unit> = dataStore.removeSessionEndTime()
}
