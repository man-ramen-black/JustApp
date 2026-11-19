package com.black.app.data

import android.content.Context
import android.net.Uri
import com.black.app.data.datastore.TextEditorDataStore
import com.black.core.file.SAFHelper
import com.black.core.file.SAFHelper.getFileName
import com.black.core.file.SAFHelper.readDocumentText
import com.black.core.file.SAFHelper.writeDocumentText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Created by jinhyuk.lee on 2024/08/06
 **/
class TextEditorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: TextEditorDataStore
) {
    companion object {
        private const val CRLF = "\r\n"
        private const val LF = "\n"

        /** 줄바꿈이 하나 이상이고 모든 LF 앞에 CR이 있는지 여부 */
        private fun isAllCrlf(text: String): Boolean {
            var lineFeedCount = 0
            text.forEachIndexed { index, char ->
                if (char != '\n') {
                    return@forEachIndexed
                }
                if (index == 0 || text[index - 1] != '\r') {
                    return false
                }
                lineFeedCount++
            }
            return lineFeedCount > 0
        }
    }

    /**
     * 줄 끝이 모두 CRLF인 문서 URI, 편집용으로 LF로 바꿔 넘긴 텍스트를 저장할 때 CRLF로 되돌리는 기준
     *
     * EditText는 CR을 공백처럼 보여 주고 줄 끝 입력이 CR과 LF 사이에 들어가 `\r입력\n`처럼 줄 끝이 섞이므로 편집 중에는 LF만 사용.
     * 읽기·저장은 IO 스레드, [reset]은 호출한 스레드에서 바꾸므로 다른 스레드의 변경이 보이도록 @Volatile.
     * 읽기와 저장이 겹치면 다른 문서를 읽은 기준으로 저장하므로 호출부가 읽기·저장·초기화를 하나씩 차례로 호출
     */
    @Volatile
    private var crlfFileUri: Uri? = null

    val currentFileUriFlow = dataStore.getLatestFileUriFlow()
    /** 표시용 파일 이름(문서 제공자 조회가 클라우드 문서면 오래 걸릴 수 있어 IO에서 조회) */
    val fileNameFlow = currentFileUriFlow
        .map { it?.getFileName(context) ?: "" }
        .flowOn(Dispatchers.IO)

    /** 선택창에서 고른 문서에 다음 실행에도 접근할 수 있도록 영구 권한 요청 */
    suspend fun keepPermission(uri: Uri): Boolean = SAFHelper.keepUriPermission(context, uri)

    suspend fun saveTextFile(uri: Uri, text: String): Result<Unit> =
        uri.writeDocumentText(context, createText = { if (uri == crlfFileUri) text.replace(LF, CRLF) else text }) {
            dataStore.updateLatestFileUri(uri)
        }

    suspend fun loadTextFile(uri: Uri): Result<String> =
        uri.readDocumentText(context) { fileText ->
            dataStore.updateLatestFileUri(uri)
            // LF와 CR이 섞인 문서는 저장 때 되돌릴 줄 끝을 정할 수 없어 그대로 편집
            val isCrlf = isAllCrlf(fileText)
            crlfFileUri = if (isCrlf) uri else null
            if (isCrlf) fileText.replace(CRLF, LF) else fileText
        }

    /**
     * 최근 문서 기록과 줄 끝 기준 초기화
     *
     * 최근 문서 기록을 지우지 못하면 실패 결과를 돌려주고 줄 끝 기준도 그대로 둠.
     * 호출부는 실패 때 편집 중인 텍스트를 비우지 않아야 함(비우면 이후 입력이 화면 이탈 자동 저장으로 옛 문서를 덮어씀)
     */
    suspend fun reset(): Result<Unit> =
        dataStore.removeLatestFileUri().onSuccess { crlfFileUri = null }
}