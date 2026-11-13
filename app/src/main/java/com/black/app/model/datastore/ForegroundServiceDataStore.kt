package com.black.app.model.datastore

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.black.app.service.ForegroundServiceController
import com.black.core.model.BaseDataStore
import com.black.core.model.blackDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * ForegroundService 실행 설정 저장소
 *
 * 실행 설정은 [ForegroundServiceController]만 읽고 씀
 */
class ForegroundServiceDataStore @Inject constructor(
    @ApplicationContext context: Context
): BaseDataStore(context) {

    companion object {
        // 손상 파일은 blackDataStore가 빈 값으로 교체하고, 빈 값이 되면 이관이 SharedPreferences에 남은 값으로 다시 채움
        private val Context.foregroundServiceDataStore: DataStore<Preferences> by blackDataStore(
            name = "foregroundService",
            produceMigrations = ::createMigrations,
        )

        // 기존 SharedPreferences 키 이름 유지(마이그레이션 호환)
        private val KEY_ACTIVATED = booleanPreferencesKey("ForegroundServiceActivated")

        @VisibleForTesting
        internal fun createMigrations(context: Context): List<DataMigration<Preferences>> = listOf(
            ActivatedSharedPreferencesMigration(context)
        )
    }

    /**
     * 기존 SharedPreferences(Foreground)의 실행 설정을 DataStore에 값이 없을 때 한 번만 복사
     *
     * SharedPreferencesMigration은 이관 후 원본 키를 지우고 빈 파일까지 삭제해, 같은 기기에 설치한
     * SharedPreferences를 읽는 다른 빌드가 실행 설정을 꺼짐으로 읽고 서비스를 띄우지 못하므로 원본 유지.
     * DataStore에 값이 생긴 뒤에는 원본을 다시 읽지 않아 다른 빌드가 나중에 쓴 값이 덮어쓰지 않음.
     * 같은 파일의 사용 타이머 키는 [UsageTimerDataStore]가 원본을 지우는 SharedPreferencesMigration으로 이관
     */
    private class ActivatedSharedPreferencesMigration(context: Context) : DataMigration<Preferences> {
        private val sharedPreferences by lazy {
            context.getSharedPreferences("${context.packageName}.Foreground", Context.MODE_PRIVATE)
        }

        override suspend fun shouldMigrate(currentData: Preferences): Boolean {
            return KEY_ACTIVATED !in currentData && sharedPreferences.contains(KEY_ACTIVATED.name)
        }

        override suspend fun migrate(currentData: Preferences): Preferences {
            val activated = sharedPreferences.getBoolean(KEY_ACTIVATED.name, false)
            return currentData.toMutablePreferences()
                .apply { this[KEY_ACTIVATED] = activated }
                .toPreferences()
        }

        /** 원본을 지우지 않으므로 정리 없음 */
        override suspend fun cleanUp() = Unit
    }

    override fun getDataStore(context: Context): DataStore<Preferences>
        = context.foregroundServiceDataStore

    suspend fun updateActivated(activated: Boolean): Result<Unit> {
        return update(KEY_ACTIVATED, activated)
    }

    suspend fun getActivated(): Boolean {
        return get(KEY_ACTIVATED, false)
    }

    fun getActivatedFlow(): Flow<Boolean> {
        return flow(KEY_ACTIVATED, false)
    }
}
