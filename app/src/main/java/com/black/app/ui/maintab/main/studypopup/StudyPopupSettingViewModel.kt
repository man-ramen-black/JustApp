package com.black.app.ui.maintab.main.studypopup

import android.net.Uri
import androidx.annotation.StringRes
import com.black.app.R
import com.black.app.model.StudyPopupRepository
import com.black.app.service.ForegroundServiceController
import com.black.app.ui.maintab.main.studypopup.StudyPopupSettingScreen
import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** [StudyPopupSettingScreen]의 상태·이벤트 관리 ViewModel */
@HiltViewModel
class StudyPopupSettingViewModel @Inject constructor(
    private val repository: StudyPopupRepository,
    private val status: StudyPopupStatus,
    foregroundServiceController: ForegroundServiceController,
) : EventViewModel() {

    /** 스터디 팝업 설정 화면 일회성 이벤트 */
    sealed class StudyPopupSettingEvent : ViewModelEvent

    /** 내용 파일 열기 선택창 실행 */
    data object EventOpenDocument : StudyPopupSettingEvent()
    /** 새 내용 파일 만들기 선택창 실행 */
    data object EventCreateDocument : StudyPopupSettingEvent()
    /** 스터디 팝업 미리보기 오버레이 표시 */
    data object EventShowPopupPreview : StudyPopupSettingEvent()
    /** 이 앱의 다른 앱 위에 표시 권한 설정 화면 이동 */
    data object EventOpenOverlaySettings : StudyPopupSettingEvent()
    /** 토스트 메시지 표시 */
    data class EventShowToast(@StringRes val messageResId: Int) : StudyPopupSettingEvent()

    private val mutableUiState = MutableStateFlow(StudyPopupSettingUiState())
    val uiState: StateFlow<StudyPopupSettingUiState> = mutableUiState.asStateFlow()

    /** 연 내용 파일. 열지 않았으면 null이라 저장 시 새 파일을 만듦 */
    private var fileUri: Uri? = null

    init {
        launch {
            repository.enabledFlow.collect { enabled ->
                mutableUiState.update { it.copy(showOnScreenOn = enabled) }
            }
        }
        // 화면이 떠 있는 동안 서비스가 켜지거나 꺼져도 상태 행에 반영
        launch {
            foregroundServiceController.running.collect { running ->
                mutableUiState.update { it.copy(serviceRunning = running) }
            }
        }
        refreshStatus()
        loadLatestFile()
    }

    /** 설정 앱에서 바뀔 수 있는 권한 상태와 팝업에 저장된 내용 개수 재조회 */
    fun refreshStatus() {
        mutableUiState.update { it.copy(overlayPermissionGranted = status.canDrawOverlays()) }
        launch {
            val count = repository.countContents()
            mutableUiState.update { it.copy(savedContentsCount = count) }
        }
    }

    fun setEnabled(enabled: Boolean) {
        launch {
            // 저장에 실패하면 스위치는 저장값을 따라 이전 상태로 남음
            if (repository.setEnabled(enabled).isFailure) {
                sendEvent(EventShowToast(R.string.study_popup_save_failed))
                return@launch
            }
            // 권한이 없으면 화면이 켜져도 팝업을 띄우지 못하므로 켤 때 바로 허용하도록 안내
            if (enabled && !status.canDrawOverlays()) {
                showOverlayPermissionDialog()
            }
        }
    }

    fun showPopupPreview() {
        launch {
            // 팝업은 저장된 내용을 보여 주므로 저장된 내용이 없으면 빈 팝업 대신 안내
            if (!repository.hasContents()) {
                sendEvent(EventShowToast(R.string.study_popup_no_contents))
                return@launch
            }
            // 권한 없이 오버레이 창을 추가하면 크래시하므로 권한부터 안내
            if (!status.canDrawOverlays()) {
                showOverlayPermissionDialog()
                return@launch
            }
            sendEvent(EventShowPopupPreview)
        }
    }

    fun showOverlayPermissionDialog() {
        mutableUiState.update { it.copy(showOverlayPermissionDialog = true) }
    }

    fun dismissOverlayPermissionDialog() {
        mutableUiState.update { it.copy(showOverlayPermissionDialog = false) }
    }

    /** 권한 안내 다이얼로그를 닫고 이 앱의 권한 스위치가 있는 설정 화면으로 이동 */
    fun openOverlaySettings() {
        dismissOverlayPermissionDialog()
        sendEvent(EventOpenOverlaySettings)
    }

    fun openFile() {
        sendEvent(EventOpenDocument)
    }

    /** 연 파일을 다시 읽어 편집 중인 내용을 파일 내용으로 되돌림 */
    fun reloadFile() {
        loadFile(fileUri)
    }

    /** 선택창에서 고른 [uri] 파일 불러오기. 선택을 취소하면 null */
    fun loadFile(uri: Uri?) {
        uri ?: return
        launch {
            repository.keepPermission(uri)
            repository.readFile(uri)
                .onSuccess { contents ->
                    applyLoadedFile(uri, contents)
                    sendEvent(EventShowToast(R.string.study_popup_loaded))
                }
                .onFailure {
                    it.printStackTrace()
                    sendEvent(EventShowToast(R.string.study_popup_load_failed))
                }
        }
    }

    /** 연 파일에 저장하고, 연 파일이 없으면 새 파일 만들기 선택창 실행 */
    fun saveFile() {
        val uri = fileUri ?: run {
            sendEvent(EventCreateDocument)
            return
        }
        saveFileTo(uri)
    }

    /** [uri] 파일과 팝업 DB에 편집 중인 내용 저장. 선택을 취소하면 null */
    fun saveFileTo(uri: Uri?) {
        uri ?: return
        launch {
            repository.keepPermission(uri)
            repository.writeFile(uri, uiState.value.contents)
                .onSuccess {
                    fileUri = uri
                    val fileName = repository.getFileName(uri)
                    val count = repository.countContents()
                    mutableUiState.update {
                        it.copy(fileName = fileName, modified = false, savedContentsCount = count)
                    }
                    sendEvent(EventShowToast(R.string.study_popup_saved))
                }
                .onFailure {
                    it.printStackTrace()
                    sendEvent(EventShowToast(R.string.study_popup_save_failed))
                }
        }
    }

    fun showAddDialog() {
        mutableUiState.update { it.copy(editingIndex = it.contents.size) }
    }

    fun showEditDialog(index: Int) {
        mutableUiState.update { it.copy(editingIndex = index) }
    }

    fun dismissEditDialog() {
        mutableUiState.update { it.copy(editingIndex = null) }
    }

    /** 편집 다이얼로그 대상 위치에 [text] 반영(목록 끝이면 새 내용 추가) */
    fun applyEdit(text: String) {
        mutableUiState.update {
            val index = it.editingIndex ?: return@update it
            val contents = it.contents.toMutableList()
            if (index < contents.size) {
                contents[index] = text
            } else {
                contents.add(text)
            }
            it.copy(contents = contents, modified = true, editingIndex = null)
        }
    }

    fun showDeleteDialog(index: Int) {
        mutableUiState.update { it.copy(deletingIndex = index) }
    }

    fun dismissDeleteDialog() {
        mutableUiState.update { it.copy(deletingIndex = null) }
    }

    /** 삭제 확인 다이얼로그 대상 내용 삭제 */
    fun deleteContents() {
        mutableUiState.update {
            val index = it.deletingIndex ?: return@update it
            it.copy(
                contents = it.contents.filterIndexed { position, _ -> position != index },
                modified = true,
                deletingIndex = null,
            )
        }
    }

    /** 마지막으로 연 파일을 화면을 열 때 자동으로 불러오기 */
    private fun loadLatestFile() {
        launch {
            val uri = repository.loadLatestUri() ?: return@launch
            repository.readFile(uri)
                .onSuccess { contents -> applyLoadedFile(uri, contents) }
                .onFailure {
                    it.printStackTrace()
                    // 재설치 등으로 권한이 사라진 파일은 화면을 열 때마다 실패하지 않도록 기록 삭제
                    repository.removeLatestUri()
                    sendEvent(EventShowToast(R.string.study_popup_load_failed_latest_file))
                }
        }
    }

    /** 불러온 파일과 내용을 화면 상태에 반영 */
    private suspend fun applyLoadedFile(uri: Uri, contents: List<String>) {
        fileUri = uri
        val fileName = repository.getFileName(uri)
        mutableUiState.update { it.copy(fileName = fileName, contents = contents, modified = false) }
    }
}
