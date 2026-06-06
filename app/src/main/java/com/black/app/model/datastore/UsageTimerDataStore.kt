package com.black.app.model.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.black.core.model.BaseDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * UsageTimer 로컬 데이터 저장소
 **/
class UsageTimerDataStore @Inject constructor(
    @ApplicationContext context: Context
): BaseDataStore(context) {

    companion object {
        /** 기존 SharedPreferences(Foreground)의 UsageTimer 키 이관 */
        private val Context.usageTimerDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "usageTimer",
            produceMigrations = { context ->
                listOf(
                    SharedPreferencesMigration(
                        context,
                        "${context.packageName}.Foreground",
                        setOf(KEY_PAUSE_END_TIME.name, KEY_PAUSE_DURATION.name, KEY_SELECTED_APPS.name),
                    )
                )
            },
        )

        // 기존 SharedPreferences 키 이름 유지(마이그레이션 호환)
        private val KEY_PAUSE_END_TIME = longPreferencesKey("UsageTimerPauseEndTime")
        private val KEY_PAUSE_DURATION = intPreferencesKey("UsageTimerPauseDuration")
        private val KEY_SELECTED_APPS = stringPreferencesKey("UsageTimerSelectedApps")

        private const val DEFAULT_PAUSE_DURATION_MINUTES = 3
    }

    override fun getDataStore(context: Context): DataStore<Preferences>
        = context.usageTimerDataStore

    suspend fun updatePauseEndTime(timestamp: Long) {
        update(KEY_PAUSE_END_TIME, timestamp)
    }

    suspend fun getPauseEndTime(): Long {
        return get(KEY_PAUSE_END_TIME, 0L)
    }

    suspend fun updatePauseDuration(durationMinutes: Int) {
        update(KEY_PAUSE_DURATION, durationMinutes)
    }

    suspend fun getPauseDuration(): Int {
        return get(KEY_PAUSE_DURATION, DEFAULT_PAUSE_DURATION_MINUTES)
    }

    suspend fun updateSelectedApps(packageNames: List<String>) {
        update(KEY_SELECTED_APPS, packageNames.joinToString(","))
    }

    suspend fun getSelectedApps(): List<String> {
        val joined = get(KEY_SELECTED_APPS, "")
        return if (joined.isEmpty()) emptyList() else joined.split(",")
    }
}
