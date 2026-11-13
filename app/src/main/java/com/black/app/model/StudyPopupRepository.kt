package com.black.app.model

import android.content.Context
import android.net.Uri
import com.black.app.model.database.studypopup.StudyPopupData
import com.black.app.model.database.studypopup.StudyPopupDatabase
import com.black.app.model.datastore.StudyPopupDataStore
import com.black.app.ui.main.studypopup.StudyPopupGlobal
import com.black.core.file.SAFHelper
import com.black.core.file.SAFHelper.getFileName
import com.black.core.file.SAFHelper.readDocumentText
import com.black.core.file.SAFHelper.writeDocumentText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 스터디 팝업 내용(팝업 DB·내용 파일)·화면 켜짐 표시 설정·마지막으로 연 파일 저장소
 *
 * 팝업은 DB를 읽고, 내용 파일(XML)에 저장할 때 DB도 함께 갱신.
 * Hilt 주입이 없는 화면 켜짐 처리([StudyPopupGlobal])·오버레이 뷰는 직접 생성해 사용
 * Created by jinhyuk.lee on 2022/05/30
 **/
class StudyPopupRepository @Inject constructor(@ApplicationContext context: Context) {
    private val appContext = context.applicationContext
    private val database = StudyPopupDatabase.getInstance(context)
    private val dataStore = StudyPopupDataStore(context)

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
     * 팝업에 보여 줄 내용 목록
     *
     * 공백뿐인 항목은 빈 페이지가 되므로 제외. 표시 여부 판정([hasContents])과 같은 기준
     */
    suspend fun loadPopupContents() : List<StudyPopupData.Contents> {
        return load().filter { it.contents.isNotBlank() }
    }

    /** 팝업에 보여 줄 내용 개수 */
    suspend fun countContents() : Int {
        return loadPopupContents().size
    }

    /** 팝업에 보여 줄 내용 존재 여부 */
    suspend fun hasContents() : Boolean {
        return countContents() > 0
    }

    /** 화면이 켜질 때 팝업 표시 설정 */
    val enabledFlow: Flow<Boolean> = dataStore.getEnabledFlow()

    suspend fun isEnabled() : Boolean {
        return dataStore.getEnabled()
    }

    suspend fun setEnabled(enabled: Boolean): Result<Unit> {
        return dataStore.updateEnabled(enabled)
    }

    suspend fun loadLatestUri() : Uri? {
        return dataStore.getLatestUri()
    }

    suspend fun removeLatestUri() {
        dataStore.removeLatestUri()
    }

    /**
     * [uri] 내용 파일의 내용 목록 읽기
     *
     * 다음에 화면을 열 때 자동으로 다시 읽도록 마지막으로 연 파일로 기록(영구 권한은 선택 직후 [keepPermission]).
     * 재설치 등으로 영구 권한이 사라진 uri는 SecurityException으로 실패
     */
    suspend fun readFile(uri: Uri) : Result<List<String>> =
        uri.readDocumentText(appContext) { text ->
            val contents = StudyPopupXml.parse(text)
            dataStore.updateLatestUri(uri)
            contents
        }

    /**
     * [contents]를 [uri] 내용 파일과 팝업 DB에 저장
     *
     * 팝업은 DB를 읽으므로 파일 저장에 성공한 뒤 DB도 같은 내용으로 갱신
     */
    suspend fun writeFile(uri: Uri, contents: List<String>) : Result<Unit> =
        uri.writeDocumentText(appContext, createText = { StudyPopupXml.serialize(contents) }) {
            dataStore.updateLatestUri(uri)
            save(contents.map { StudyPopupData.Contents(it) })
        }

    /** 선택창에서 고른 내용 파일에 다음 실행에도 접근할 수 있도록 영구 권한 요청 */
    suspend fun keepPermission(uri: Uri) : Boolean = SAFHelper.keepUriPermission(appContext, uri)

    /** 표시용 파일 이름, 조회할 수 없으면 빈 문자열 */
    suspend fun getFileName(uri: Uri) : String = withContext(Dispatchers.IO) {
        uri.getFileName(appContext).orEmpty()
    }
}
