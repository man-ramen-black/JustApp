package com.black.app.model.datastore

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.black.core.model.BaseDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Created by jinhyuk.lee on 2024/08/06
 **/
class TextEditorDataStore @Inject constructor(
    @ApplicationContext context: Context
): BaseDataStore(context) {

    companion object {
        private val Context.textEditorDataStore: DataStore<Preferences> by preferencesDataStore(name = "textEditor")

        private val KEY_LATEST_FILE_URI = stringPreferencesKey("latestFileUri")
    }

    override fun getDataStore(context: Context): DataStore<Preferences>
        = context.textEditorDataStore

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