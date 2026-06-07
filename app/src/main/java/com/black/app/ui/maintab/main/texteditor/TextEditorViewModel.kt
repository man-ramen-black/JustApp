package com.black.app.ui.maintab.main.texteditor

import android.net.Uri
import androidx.annotation.StringRes
import com.black.app.R
import com.black.app.model.database.studypopup.TextEditorRepository
import com.black.core.di.HiltModule
import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

/** TextEditor 화면 상태·이벤트 관리 ViewModel [TextEditorScreen] */
@HiltViewModel
class TextEditorViewModel @Inject constructor(
    private val textEditorRepository: TextEditorRepository,
    @Named(HiltModule.NAME_MAIN_SCOPE) private val mainScope: CoroutineScope,
) : EventViewModel() {

    /** TextEditor 화면 일회성 이벤트 */
    sealed class TextEditorEvent : ViewModelEvent

    /** 문서 열기 선택창 실행 */
    data object EventOpenDocument : TextEditorEvent()
    /** 새 문서 생성 선택창 실행 */
    data object EventCreateDocument : TextEditorEvent()
    /** 토스트 메시지 표시 */
    data class EventShowToast(@StringRes val messageResId: Int) : TextEditorEvent()

    private val mutableUiState = MutableStateFlow(TextEditorUiState())
    val uiState: StateFlow<TextEditorUiState> = mutableUiState.asStateFlow()

    /** 마지막 로드·저장 시점 텍스트(변경 감지 기준) */
    private val originText = MutableStateFlow("")

    init {
        launch {
            textEditorRepository.fileNameFlow.collect { fileName ->
                mutableUiState.update { it.copy(fileName = fileName) }
            }
        }
        launch(Dispatchers.IO) {
            val uri = textEditorRepository.currentFileUriFlow.firstOrNull() ?: return@launch
            loadFile(uri).exceptionOrNull()?.let {
                it.printStackTrace()
                // 마지막 파일 로드 실패 시 마지막 파일 uri 초기화
                textEditorRepository.reset()
                sendEvent(EventShowToast(R.string.text_editor_load_failed_latest_file))
            }
        }
    }

    fun onTextChanged(text: String) {
        mutableUiState.update { it.copy(text = text) }
    }

    fun onClickNew() {
        mutableUiState.update { it.copy(showResetConfirm = true) }
    }

    fun onConfirmReset() {
        mutableUiState.update { it.copy(showResetConfirm = false) }
        launch(Dispatchers.IO) {
            textEditorRepository.reset()
            originText.value = ""
            mutableUiState.update { it.copy(text = "") }
        }
    }

    fun onDismissReset() {
        mutableUiState.update { it.copy(showResetConfirm = false) }
    }

    fun onClickLoad() {
        sendEvent(EventOpenDocument)
    }

    fun onClickSave() {
        launch {
            if (saveCurrentFile().exceptionOrNull() is IllegalAccessException) {
                sendEvent(EventCreateDocument)
            }
        }
    }

    fun onDocumentOpened(uri: Uri?) {
        launch {
            loadFile(uri ?: return@launch).exceptionOrNull()?.let {
                it.printStackTrace()
                sendEvent(EventShowToast(R.string.text_editor_load_failed))
            }
        }
    }

    fun onDocumentCreated(uri: Uri?) {
        launch {
            saveFile(uri ?: return@launch)
        }
    }

    /** 화면 이탈(ON_PAUSE) 시 자동 저장. ViewModel 소멸 후에도 저장이 완료되도록 [mainScope] 사용 */
    fun onPause() {
        mainScope.launch {
            saveCurrentFile()
        }
    }

    private suspend fun loadFile(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val loadResult = textEditorRepository.loadTextFile(uri)
        if (loadResult.isFailure) {
            return@withContext Result.failure(loadResult.exceptionOrNull()!!)
        }
        val loadedText = loadResult.getOrNull()!!
        originText.value = loadedText
        mutableUiState.update { it.copy(text = loadedText) }
        Result.success(Unit)
    }

    /**
     * 현재 파일 저장
     * @return 텍스트 변경 사항이 없어서 저장을 건너 뛴 경우 false
     */
    private suspend fun saveCurrentFile(): Result<Boolean> {
        if (uiState.value.text == originText.value) {
            // 텍스트 변경 사항 없음
            return Result.success(false)
        }
        val uri = textEditorRepository.currentFileUriFlow.firstOrNull()
            ?: return Result.failure(IllegalAccessException("currentFileUri is null"))
        val saveResult = saveFile(uri)
        return if (saveResult.isSuccess) {
            Result.success(true)
        } else {
            Result.failure(saveResult.exceptionOrNull()!!)
        }
    }

    private suspend fun saveFile(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val text = uiState.value.text
        textEditorRepository.saveTextFile(uri, text)
            .also {
                if (it.isSuccess) {
                    // 저장 성공 시 변경 감지 기준 갱신(중복 자동 저장 방지)
                    originText.value = text
                    sendEvent(EventShowToast(R.string.text_editor_save_completed))
                } else {
                    sendEvent(EventShowToast(R.string.text_editor_save_failed))
                }
            }
    }
}
