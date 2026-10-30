package com.black.app.model.datastore

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.black.core.model.BaseDataStore
import com.black.core.model.blackDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Created by jinhyuk.lee on 2024/08/06
 *
 * [dataStore]를 받는 생성자는 사용자 최근 문서와 분리된 저장소가 필요한 계측 테스트용
 **/
class TextEditorDataStore(
    context: Context,
    private val dataStore: DataStore<Preferences>,
): BaseDataStore(context) {

    @Inject constructor(@ApplicationContext context: Context) : this(context, context.textEditorDataStore)

    companion object {
        private val Context.textEditorDataStore: DataStore<Preferences> by blackDataStore("textEditor")

        private val KEY_LATEST_FILE_URI = stringPreferencesKey("latestFileUri")
    }

    override fun getDataStore(context: Context): DataStore<Preferences>
        = dataStore

    suspend fun updateLatestFileUri(uri: Uri) {
        update(KEY_LATEST_FILE_URI, uri.toString())
    }

    fun getLatestFileUriFlow(): Flow<Uri?> {
        return flow(KEY_LATEST_FILE_URI)
            .map { Uri.parse(it ?: return@map null) }
    }

    suspend fun removeLatestFileUri() {
        remove(KEY_LATEST_FILE_URI)
    }
}