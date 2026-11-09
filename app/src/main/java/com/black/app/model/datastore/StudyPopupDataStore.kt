package com.black.app.model.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
        private val Context.studyPopupDataStore: DataStore<Preferences> by blackDataStore("studyPopup")

        private val KEY_ENABLED = booleanPreferencesKey("StudyPopupEnabled")
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
}
