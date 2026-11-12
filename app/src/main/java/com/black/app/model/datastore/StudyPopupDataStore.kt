package com.black.app.model.datastore

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.black.core.model.BaseDataStore
import com.black.core.model.blackDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * StudyPopup 로컬 데이터 저장소
 **/
class StudyPopupDataStore @Inject constructor(
    @ApplicationContext context: Context
): BaseDataStore(context) {

    companion object {
        /**
         * 기존 SharedPreferences(StudyPopup)의 마지막으로 연 파일 키 이관
         *
         * 마지막 파일 기록은 실패하면 지우는 값이라, 원본을 남기면 지운 뒤 다음 실행에서 옛 값이 다시 이관되므로
         * 원본을 지우는 SharedPreferencesMigration 사용
         */
        private val Context.studyPopupDataStore: DataStore<Preferences> by blackDataStore(
            name = "studyPopup",
            produceMigrations = { context ->
                listOf(
                    SharedPreferencesMigration(
                        context,
                        "${context.packageName}.StudyPopup",
                        setOf(KEY_LATEST_URI.name),
                    )
                )
            },
        )

        private val KEY_ENABLED = booleanPreferencesKey("StudyPopupEnabled")

        // 기존 SharedPreferences 키 이름 유지(마이그레이션 호환)
        private val KEY_LATEST_URI = stringPreferencesKey("LatestUri")
    }

    override fun getDataStore(context: Context): DataStore<Preferences>
        = context.studyPopupDataStore

    suspend fun updateEnabled(enabled: Boolean): Result<Unit> {
        return update(KEY_ENABLED, enabled)
    }

    /**
     * 화면이 켜질 때 팝업 표시 설정
     *
     * 저장값이 없으면 꺼짐이라, 켜기 전에는 화면을 켜도 팝업이 뜨지 않음
     */
    suspend fun getEnabled(): Boolean {
        return get(KEY_ENABLED, false)
    }

    fun getEnabledFlow(): Flow<Boolean> {
        return flow(KEY_ENABLED).map { it ?: false }
    }

    /** 마지막으로 연 내용 파일 기록 */
    suspend fun updateLatestUri(uri: Uri): Result<Unit> {
        return update(KEY_LATEST_URI, uri.toString())
    }

    /** 마지막으로 연 내용 파일(기록 없으면 null) */
    suspend fun getLatestUri(): Uri? {
        val uriString = get(KEY_LATEST_URI, "")
        return if (uriString.isEmpty()) null else Uri.parse(uriString)
    }

    suspend fun removeLatestUri(): Result<Unit> {
        return remove(KEY_LATEST_URI)
    }
}
