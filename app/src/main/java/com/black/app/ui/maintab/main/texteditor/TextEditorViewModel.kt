package com.black.app.ui.maintab.main.texteditor

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.black.app.R
import com.black.app.model.database.studypopup.TextEditorRepository
import com.black.core.util.Extensions.launch
import com.black.core.util.Extensions.letIf
import com.black.core.util.Log
import com.black.core.viewmodel.EventViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [TextEditorFragment]
 */
@HiltViewModel
class TextEditorViewModel @Inject constructor(
    private val textEditorRepo: TextEditorRepository
) : EventViewModel() {
    companion object {
        const val EVENT_LOAD = "Load"
        const val EVENT_SAVE_NEW_FILE = "SaveNewFile"
        const val EVENT_RESET = "Reset"
        const val EVENT_TOAST = "Toast"
    }

    private val currentFileUriFlow = textEditorRepo.currentFileUriFlow

    val fileName = textEditorRepo.fileNameFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ""
        )

    private val originText = MutableStateFlow("")
    val text = MutableStateFlow("")

    init {
        launch(Dispatchers.IO) {
            val uri = currentFileUriFlow.firstOrNull()
                ?: return@launch

            val result = loadFile(uri)

            // 실패 시 처리
            result.exceptionOrNull()
                ?.let {
                    it.printStackTrace()
                    // 마지막 파일 로드 실패 시 마지막 파일 uri 초기화
                    textEditorRepo.reset()
                    postEvent(EVENT_TOAST, R.string.text_editor_load_failed_latest_file)
                }
        }
    }

    fun onClickNew() {
        Log.v()
        sendEvent(EVENT_RESET)
    }

    fun onClickLoad() {
        Log.v()
        sendEvent(EVENT_LOAD)
    }

    fun onClickSave() {
        Log.v()
        launch {
            if (saveCurrentFile().exceptionOrNull() is IllegalAccessException) {
                sendEvent(EVENT_SAVE_NEW_FILE)
            }
        }
    }

    suspend fun loadFile(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val loadResult = textEditorRepo.loadTextFile(uri)
        if (loadResult.isFailure) {
            return@withContext Result.failure(loadResult.exceptionOrNull()!!)
        }

        val loadedText = loadResult.getOrNull()!!
        text.value = loadedText
        originText.value = loadedText
        Result.success(Unit)
    }

    /**
     * 현재 파일 저장
     * @return 텍스트 변경 사항이 없어서 저장을 건너 뛴 경우 false
     */
    suspend fun saveCurrentFile(): Result<Boolean> {
        if (text.value == originText.value) {
            // 텍스트 변경 사항 없음
            return Result.success(false)
        }

        val uri = currentFileUriFlow.firstOrNull()
            ?: return Result.failure(IllegalAccessException("currentFileUri is null"))

        return saveFile(uri)
            .let {
                if (it.isSuccess) {
                    Result.success(true)
                } else {
                    Result.failure(it.exceptionOrNull()!!)
                }
            }
    }

    suspend fun saveFile(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        textEditorRepo.saveTextFile(uri, text.value)
            .also {
                if (it.isSuccess) {
                    postEvent(EVENT_TOAST, R.string.text_editor_save_completed)
                } else {
                    postEvent(EVENT_TOAST, R.string.text_editor_save_failed)
                }
            }
    }

    fun reset() {
        launch(Dispatchers.IO) {
            textEditorRepo.reset()
            text.value = ""
            originText.value = ""
        }
    }
}