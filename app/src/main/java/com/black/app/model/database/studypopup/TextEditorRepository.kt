package com.black.app.model.database.studypopup

import android.content.Context
import android.net.Uri
import com.black.app.model.datastore.TextEditorDataStore
import com.black.core.file.SAFHelper.Companion.getFileName
import com.black.core.file.SAFHelper.Companion.openInputStream
import com.black.core.file.SAFHelper.Companion.openOutputStream
import com.black.core.util.FileUtil
import com.black.core.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/**
 * Created by jinhyuk.lee on 2024/08/06
 **/
class TextEditorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: TextEditorDataStore
) {
    val currentFileUriFlow = dataStore.getLatestFileUriFlow()
    val fileNameFlow = currentFileUriFlow
        .map { it?.getFileName(context) ?: "" }

    suspend fun saveTextFile(uri: Uri?, text: String) {
        try {
            val stream = uri?.openOutputStream(context, "wt")
                ?: run {
                    Log.w("openOutputStream failed, uri : $uri")
                    return
                }

            FileUtil.writeText(stream) { it.write(text) }
                .also { dataStore.updateLatestFileUri(uri) }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    suspend fun loadTextFile(uri: Uri?): String? {
        return try {
            val stream = uri?.openInputStream(context)
                ?: run {
                    Log.w("openInputStream failed, uri : $uri")
                    return null
                }

            FileUtil.readText(stream)
                .also { dataStore.updateLatestFileUri(uri) }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    suspend fun reset() {
        dataStore.removeLatestFileUri()
    }
}