package com.black.app.ui.maintab.main.texteditor

import android.net.Uri
import androidx.annotation.StringRes
import com.black.app.R
import com.black.app.model.database.studypopup.TextEditorRepository
import com.black.app.ui.maintab.main.texteditor.TextEditorScreen
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
    /** 접근 권한을 다시 받도록 [uri] 문서가 있는 위치에서 문서 열기 선택창 실행 */
    data class EventReopenDocument(val uri: Uri) : TextEditorEvent()
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
                // 마지막 파일 로드 실패 시 마지막 파일 uri 초기화(권한 없는 uri로 자동 저장되지 않도록)
                textEditorRepository.reset()
                if (it is SecurityException) {
                    // 재설치 등으로 영구 권한이 사라진 문서는 다시 선택해 권한을 받도록 요청
                    mutableUiState.update { state -> state.copy(permissionRequiredUri = uri) }
                } else {
                    sendEvent(EventShowToast(R.string.text_editor_load_failed_latest_file))
                }
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

    /** 권한 요청 다이얼로그 확인 시 같은 문서를 다시 선택하도록 선택창 실행 */
    fun onConfirmPermissionRequest() {
        val uri = uiState.value.permissionRequiredUri ?: return
        mutableUiState.update { it.copy(permissionRequiredUri = null) }
        sendEvent(EventReopenDocument(uri))
    }

    fun onDismissPermissionRequest() {
        mutableUiState.update { it.copy(permissionRequiredUri = null) }
    }

    fun onClickLoad() {
        sendEvent(EventOpenDocument)
    }

    fun onClickSave() {
        launch {
            val result = saveCurrentFile()
            if (result.exceptionOrNull() is IllegalAccessException) {
                sendEvent(EventCreateDocument)
                return@launch
            }
            // 변경 사항이 없어 저장을 건너뛴 경우에도 저장 완료 피드백 제공
            sendEvent(EventShowToast(saveResultMessage(result)))
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
            val result = saveFile(uri ?: return@launch)
            sendEvent(EventShowToast(saveResultMessage(result)))
        }
    }

    /**
     * 화면 이탈(ON_PAUSE) 시 자동 저장. ViewModel 소멸 후에도 저장이 완료되도록 [mainScope] 사용
     *
     * 뒤로가기 이탈은 저장이 끝나기 전에 화면 수집이 멈추고 ViewModel이 정리되어 [sendEvent] 토스트가 전달되지 않으므로,
     * 저장 결과 메시지는 화면보다 오래 유지되는 [showSaveResult]로 전달. 변경 사항이 없거나 저장할 파일이 없으면 미전달
     */
    fun onPause(showSaveResult: (messageResId: Int) -> Unit) {
        mainScope.launch {
            val result = saveCurrentFile()
            if (result.getOrNull() == false || result.exceptionOrNull() is IllegalAccessException) {
                return@launch
            }
            showSaveResult(saveResultMessage(result))
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
                }
            }
    }

    /** 저장 결과 토스트 메시지 */
    @StringRes
    private fun saveResultMessage(result: Result<*>): Int {
        return if (result.isSuccess) R.string.text_editor_save_completed else R.string.text_editor_save_failed
    }
}
