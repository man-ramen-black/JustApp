package com.black.app.ui.main.texteditor

import android.net.Uri
import androidx.annotation.StringRes
import com.black.app.R
import com.black.app.data.TextEditorRepository
import com.black.app.ui.main.texteditor.TextEditorScreen
import com.black.core.di.HiltModule
import com.black.core.util.Log
import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Named

/** [TextEditorScreen]의 상태·이벤트 관리 ViewModel */
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

    /**
     * 편집기 내용의 원본 문서
     *
     * 화면 이탈 자동 저장과 저장 버튼의 저장 대상이며 null이면 새 문서.
     * 최근 문서 기록은 불러오지 못한 문서로 남을 수 있어(시작 로드 실패 뒤 기록 삭제 실패) 저장 대상으로 쓰지 않음.
     * 로드 성공과 새 문서 생성 저장 성공 때 설정하고 새 문서 초기화 성공 때 비우며, 로드 실패 때는 화면에 남은 앞 문서가 맞아 그대로 둠.
     * [fileMutex] 안에서만 읽고 씀
     */
    private var sourceFileUri: Uri? = null

    /**
     * 로드·저장·새 문서 초기화를 호출 순서대로 하나씩 실행하는 잠금
     *
     * 겹치면 늦게 끝난 쪽이 화면 내용, [sourceFileUri], 최근 문서 기록, 줄 끝 기준을 서로 다른 문서 기준으로 바꿈.
     * 화면 이탈 자동 저장도 [mainScope]에서 같은 잠금을 씀.
     * 재진입이 안 되는 잠금이라 잠금 안에서 다시 잡지 않고, 잠금 안에서 시작하는 로드는 새 코루틴으로 실행
     */
    private val fileMutex = Mutex()

    /**
     * 최근 문서를 불러오는 작업
     *
     * 새 문서 초기화 확인 때 취소해, 아직 읽지 않았으면 읽지 않고 읽는 중이면 읽기가 끝난 뒤 결과를 화면에 반영하지 않음
     */
    private var latestFileLoadJob: Job

    /**
     * 진행 중인 새 문서 초기화
     *
     * 확인이 겹치면 앞 초기화만 실행.
     */
    private var resetJob: Job? = null

    init {
        launch {
            textEditorRepository.fileNameFlow.collect { fileName ->
                mutableUiState.update { it.copy(fileName = fileName) }
            }
        }
        latestFileLoadJob = loadLatestFile()
    }

    fun onTextChanged(text: String) {
        mutableUiState.update { it.copy(text = text) }
    }

    fun onClickNew() {
        mutableUiState.update { it.copy(showResetConfirm = true) }
    }

    fun onConfirmReset() {
        mutableUiState.update { it.copy(showResetConfirm = false) }
        // 앞 확인의 초기화가 끝나지 않았으면 같은 요청이라 건너뜀(실패 안내 중복 방지)
        if (resetJob?.isActive == true) {
            return
        }
        // 시작 로드는 끝나기를 기다리지 않고 취소만 함(초기화는 아래 잠금으로 로드가 잠금을 놓은 뒤 실행)
        val wasLoading = latestFileLoadJob.isActive
        latestFileLoadJob.cancel()
        // Repository가 DataStore로 저장소를 정리해 메인 스레드에서 호출해도 안전하므로 디스패처를 바꾸지 않음
        resetJob = launch {
            fileMutex.withLock {
                // 최근 문서 기록과 줄 끝 기준 초기화
                if (textEditorRepository.reset().isFailure) {
                    // 원본 문서와 최근 문서 기록이 그대로라 텍스트를 비우면 이후 입력이 화면 이탈 자동 저장으로 옛 문서를 덮어쓰므로 텍스트 유지
                    sendEvent(EventShowToast(R.string.text_editor_new_failed))
                    if (wasLoading) {
                        // 끊은 시작 로드 다시 시작(새 코루틴이라 이 잠금이 풀린 뒤 실행)
                        latestFileLoadJob = loadLatestFile()
                    }
                    return@withLock
                }
                // 원본 문서, 변경 감지 기준, 화면 내용 비움
                sourceFileUri = null
                originText.value = ""
                mutableUiState.update { it.copy(text = "") }
            }
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
            val result = fileMutex.withLock { saveCurrentFile() }
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
            uri ?: return@launch
            // 권한 요청 전에 잠금을 잡아 뒤에 누른 새 문서 초기화보다 먼저 실행
            val result = fileMutex.withLock {
                textEditorRepository.keepPermission(uri)
                loadFile(uri)
            }
            result.exceptionOrNull()?.let {
                Log.w(it)
                sendEvent(EventShowToast(R.string.text_editor_load_failed))
            }
        }
    }

    fun onDocumentCreated(uri: Uri?) {
        launch {
            uri ?: return@launch
            val result = fileMutex.withLock {
                textEditorRepository.keepPermission(uri)
                // 저장에 성공하면 만든 문서가 편집기 내용의 원본 문서
                saveFile(uri).onSuccess { sourceFileUri = uri }
            }
            sendEvent(EventShowToast(saveResultMessage(result)))
        }
    }

    /**
     * 화면 이탈(ON_PAUSE) 시 자동 저장. ViewModel 소멸 후에도 저장이 완료되도록 [mainScope] 사용
     *
     * 진행 중인 로드·저장·초기화가 있으면 [fileMutex]로 끝나기를 기다린 뒤 그 결과의 원본 문서 기준으로 저장.
     * 뒤로가기 이탈은 저장이 끝나기 전에 화면 수집이 멈추고 ViewModel이 정리되어 [sendEvent] 토스트가 전달되지 않으므로,
     * 저장 결과 메시지는 화면보다 오래 유지되는 [showSaveResult]로 전달. 변경 사항이 없거나 저장할 파일이 없으면 미전달
     */
    fun onPause(showSaveResult: (messageResId: Int) -> Unit) {
        mainScope.launch {
            val result = fileMutex.withLock { saveCurrentFile() }
            if (result.getOrNull() == false || result.exceptionOrNull() is IllegalAccessException) {
                return@launch
            }
            showSaveResult(saveResultMessage(result))
        }
    }

    /**
     * 최근 문서 불러오기
     *
     * 실패하면 최근 문서 기록을 지우고 권한 요청 또는 실패 안내
     */
    private fun loadLatestFile(): Job = launch {
        fileMutex.withLock {
            val uri = textEditorRepository.currentFileUriFlow.firstOrNull() ?: return@withLock
            loadFile(uri).exceptionOrNull()?.let {
                Log.w(it)
                // 불러오지 못한 문서 이름을 표시하거나 다음 실행에 다시 불러오지 않도록 최근 문서 기록 삭제
                // (지우지 못해도 원본 문서로 설정되지 않아 이후 입력이 그 문서에 자동 저장되지 않음)
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

    /**
     * [uri] 문서를 불러와 화면 내용, 변경 감지 기준, 원본 문서를 함께 바꿈
     *
     * [fileMutex] 안에서 호출. 실패하면 셋 다 그대로 둠
     */
    private suspend fun loadFile(uri: Uri): Result<Unit> {
        val loadResult = textEditorRepository.loadTextFile(uri)
        if (loadResult.isFailure) {
            return Result.failure(loadResult.exceptionOrNull()!!)
        }
        val loadedText = loadResult.getOrNull()!!
        sourceFileUri = uri
        originText.value = loadedText
        mutableUiState.update { it.copy(text = loadedText) }
        return Result.success(Unit)
    }

    /**
     * 편집기 내용을 원본 문서에 저장
     *
     * [fileMutex] 안에서 호출. 원본 문서가 없으면(새 문서) IllegalAccessException 실패 결과
     * @return 텍스트 변경 사항이 없어서 저장을 건너 뛴 경우 false
     */
    private suspend fun saveCurrentFile(): Result<Boolean> {
        if (uiState.value.text == originText.value) {
            // 텍스트 변경 사항 없음
            return Result.success(false)
        }
        val uri = sourceFileUri
            ?: return Result.failure(IllegalAccessException("sourceFileUri is null"))
        val saveResult = saveFile(uri)
        return if (saveResult.isSuccess) {
            Result.success(true)
        } else {
            Result.failure(saveResult.exceptionOrNull()!!)
        }
    }

    /** [fileMutex] 안에서 편집기 내용을 [uri] 문서에 저장 */
    private suspend fun saveFile(uri: Uri): Result<Unit> {
        val text = uiState.value.text
        return textEditorRepository.saveTextFile(uri, text)
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
