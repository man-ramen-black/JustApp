package com.black.feature.sleeproutine.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.black.core.data.BaseDataStore
import com.black.core.data.PreferenceUpdate
import com.black.core.data.blackDataStore
import com.black.feature.sleeproutine.model.SleepRoutineSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** 수면 루틴 설정과 진행 중 세션 종료 시각 저장소 */
class SleepRoutineDataStore @Inject constructor(
    @ApplicationContext context: Context,
) : BaseDataStore(context) {

    companion object {
        private val Context.sleepRoutineDataStore: DataStore<Preferences> by blackDataStore(name = "sleepRoutine")

        private val KEY_YOUTUBE_URL = stringPreferencesKey("youtubeUrl")
        private val KEY_DURATION_MINUTES = intPreferencesKey("durationMinutes")
        private val KEY_VOLUME_ENABLED = booleanPreferencesKey("volumeEnabled")
        private val KEY_VOLUME_LEVEL = intPreferencesKey("volumeLevel")

        /** 진행 중 세션 종료 시각(epoch ms), 없으면 키 없음 */
        private val KEY_SESSION_END_TIME = longPreferencesKey("sessionEndTime")
    }

    override fun getDataStore(context: Context): DataStore<Preferences> = context.sleepRoutineDataStore

    /** 저장된 설정, 없는 항목은 [SleepRoutineSettings]의 기본값(YouTube 주소는 개인 데이터라 값을 로그에 남기지 않음) */
    suspend fun getSettings(): SleepRoutineSettings {
        val defaults = SleepRoutineSettings()
        return SleepRoutineSettings(
            youtubeUrl = get(KEY_YOUTUBE_URL, defaults.youtubeUrl, logValue = false),
            durationMinutes = get(KEY_DURATION_MINUTES, defaults.durationMinutes),
            volumeEnabled = get(KEY_VOLUME_ENABLED, defaults.volumeEnabled),
            volumeLevel = get(KEY_VOLUME_LEVEL, defaults.volumeLevel),
        )
    }

    /**
     * 설정 항목을 한 번에 저장
     *
     * 일부만 저장된 채 끊기지 않음.
     * YouTube 주소가 함께 저장되므로 값을 로그에 남기지 않음.
     */
    suspend fun updateSettings(settings: SleepRoutineSettings): Result<Unit> = updateAll(
        PreferenceUpdate(KEY_YOUTUBE_URL, settings.youtubeUrl),
        PreferenceUpdate(KEY_DURATION_MINUTES, settings.durationMinutes),
        PreferenceUpdate(KEY_VOLUME_ENABLED, settings.volumeEnabled),
        PreferenceUpdate(KEY_VOLUME_LEVEL, settings.volumeLevel),
        logValue = false,
    )

    /** 진행 중 세션 종료 시각, 없으면 0 */
    suspend fun getSessionEndTime(): Long = get(KEY_SESSION_END_TIME, 0L)

    /** 진행 중 세션 종료 시각 Flow, 없으면 0 */
    fun sessionEndTimeFlow(): Flow<Long> = flow(KEY_SESSION_END_TIME, 0L)

    suspend fun updateSessionEndTime(timestamp: Long): Result<Unit> = update(KEY_SESSION_END_TIME, timestamp)

    suspend fun removeSessionEndTime(): Result<Unit> = remove(KEY_SESSION_END_TIME)
}
