package com.black.code.model

import android.content.Context
import android.net.Uri
import com.black.code.model.database.studypopup.StudyPopupData
import com.black.code.model.database.studypopup.StudyPopupDatabase
import com.black.code.model.preferences.StudyPopupPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Created by jinhyuk.lee on 2022/05/30
 **/
class StudyPopupModel(context: Context) {
    private val preferences = StudyPopupPreferences(context)
    private val database = StudyPopupDatabase.getInstance(context)

    suspend fun save(list: List<StudyPopupData.Contents>) {
        val dao = database.dao()
        list.forEachIndexed { index, data ->
            data.id = index + 1
        }

        if (dao.getRowCount() > list.size) {
            dao.deleteAfter(beforeId = list.size)
        }

        dao.insertOrUpdate(*list.toTypedArray())
    }

    suspend fun load() : List<StudyPopupData.Contents> = withContext(Dispatchers.IO) {
        database.dao().getAll()
    }

    fun loadLatestUri() : Uri? {
        return preferences.loadLatestUri()
    }

    fun saveLatestUri(uri: Uri) {
        preferences.saveLatestUri(uri)
    }
}