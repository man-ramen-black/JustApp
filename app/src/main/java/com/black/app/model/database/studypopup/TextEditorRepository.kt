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

    suspend fun saveTextFile(uri: Uri?, text: String): Result<Unit> {
        return try {
            val stream = uri?.openOutputStream(context, "wt")
                ?: run {
                    return Result.failure(IllegalStateException("openOutputStream failed, uri : $uri"))
                }

            FileUtil.writeText(stream) { it.write(text) }
                .also { dataStore.updateLatestFileUri(uri) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadTextFile(uri: Uri?): Result<String> {
        return try {
            val stream = uri?.openInputStream(context)
                ?: run {
                    return Result.failure(IllegalStateException("openInputStream failed, uri : $uri"))
                }

            val text = FileUtil.readText(stream)
                .also { dataStore.updateLatestFileUri(uri) }
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reset() {
        dataStore.removeLatestFileUri()
    }
}