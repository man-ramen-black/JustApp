package com.black.app.model.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.black.core.model.BaseDataStore
import com.black.core.model.blackDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * UsageTimer 로컬 데이터 저장소
 **/
class UsageTimerDataStore @Inject constructor(
    @ApplicationContext context: Context
): BaseDataStore(context) {

    companion object {
        /**
         * 기존 SharedPreferences(Foreground)의 UsageTimer 키 이관(원본 키 삭제)
         *
         * 같은 파일의 실행 설정 키는 [ForegroundServiceDataStore]가 원본을 남기는 방식으로 따로 이관.
         * 실행 설정은 원본이 지워지면 같은 기기의 다른 빌드가 꺼짐으로 읽어 서비스가 뜨지 않지만, 사용 타이머 값은 지워져도
         * 기본값으로 동작하므로 방식이 다름. 이 이관은 자기 키만 지우고 다른 키가 남으면 파일도 남겨 서로 영향 없음
         */
        private val Context.usageTimerDataStore: DataStore<Preferences> by blackDataStore(
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

    suspend fun updatePauseEndTime(timestamp: Long): Result<Unit> {
        return update(KEY_PAUSE_END_TIME, timestamp)
    }

    suspend fun getPauseEndTime(): Long {
        return get(KEY_PAUSE_END_TIME, 0L)
    }

    suspend fun updatePauseDuration(durationMinutes: Int): Result<Unit> {
        return update(KEY_PAUSE_DURATION, durationMinutes)
    }

    suspend fun getPauseDuration(): Int {
        return get(KEY_PAUSE_DURATION, DEFAULT_PAUSE_DURATION_MINUTES)
    }

    suspend fun updateSelectedApps(packageNames: List<String>): Result<Unit> {
        return update(KEY_SELECTED_APPS, packageNames.joinToString(","))
    }

    suspend fun getSelectedApps(): List<String> {
        val joined = get(KEY_SELECTED_APPS, "")
        return if (joined.isEmpty()) emptyList() else joined.split(",")
    }
}
