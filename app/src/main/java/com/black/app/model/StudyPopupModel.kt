package com.black.app.model

import android.content.Context
import android.net.Uri
import com.black.app.model.database.studypopup.StudyPopupData
import com.black.app.model.database.studypopup.StudyPopupDatabase
import com.black.app.model.preferences.StudyPopupPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Created by jinhyuk.lee on 2022/05/30
 **/
class StudyPopupModel(context: Context) {
    private val preferences = StudyPopupPreferences(context)
    private val database = StudyPopupDatabase.getInstance(context)

    // Room은 메인 스레드 접근 시 예외를 던지고, 호출부는 viewModelScope(Main)라 IO로 전환
    suspend fun save(list: List<StudyPopupData.Contents>) = withContext(Dispatchers.IO) {
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

    /**
     * 팝업에 보여 줄 내용 존재 여부
     *
     * 공백뿐인 항목은 빈 페이지가 되므로 내용으로 치지 않음
     */
    suspend fun hasContents() : Boolean {
        return load().any { it.contents.isNotBlank() }
    }

    fun loadLatestUri() : Uri? {
        return preferences.loadLatestUri()
    }

    fun saveLatestUri(uri: Uri) {
        preferences.saveLatestUri(uri)
    }
}