package com.black.app.ui.main.sleeproutine

import androidx.annotation.StringRes
import com.black.app.R
import com.black.app.model.SleepRoutineRepository
import com.black.app.sleeproutine.SleepRoutineSettings
import com.black.app.ui.main.sleeproutine.SleepRoutineScreen
import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** [SleepRoutineScreen]의 상태·이벤트 관리 ViewModel */
@HiltViewModel
class SleepRoutineViewModel @Inject constructor(
    private val repository: SleepRoutineRepository,
    private val systemStatus: SleepRoutineSystemStatus,
) : EventViewModel() {

    /** 수면 루틴 화면 일회성 이벤트 */
    sealed class SleepRoutineEvent : ViewModelEvent

    /** 방해 금지 접근 설정 화면 이동 */
    data object EventOpenDoNotDisturbSettings : SleepRoutineEvent()
    /** 다른 앱 위에 표시 설정 화면 이동 */
    data object EventOpenOverlaySettings : SleepRoutineEvent()
    /** 알림 접근 설정 화면 이동 */
    data object EventOpenNotificationListenerSettings : SleepRoutineEvent()
    /** 앱 알림 설정 화면 이동 */
    data object EventOpenNotificationSettings : SleepRoutineEvent()
    /** 문자열 리소스 토스트 표시 */
    data class EventShowToast(@StringRes val messageResId: Int) : SleepRoutineEvent()

    private val mutableUiState = MutableStateFlow(SleepRoutineUiState())
    val uiState: StateFlow<SleepRoutineUiState> = mutableUiState.asStateFlow()

    /** 진행 중인 저장·시작 작업, 끝나기 전 들어온 저장·시작 요청을 거르는 데 사용 */
    private var saveJob: Job? = null

    init {
        refreshStatus()
        launch { loadSettings() }
        // 알림의 지금 종료·종료 시각 도달로 서비스가 멈춘 경우도 실행 섹션에 반영
        launch {
            systemStatus.running.collect { running ->
                mutableUiState.update { it.copy(running = running) }
            }
        }
        launch {
            repository.sessionEndTime.collect { endTimeMillis ->
                mutableUiState.update { it.copy(sessionEndTimeMillis = endTimeMillis) }
            }
        }
    }

    private suspend fun loadSettings() {
        val settings = repository.getSettings()
        val maxLevel = systemStatus.maxVolumeLevel()
        mutableUiState.update {
            it.copy(
                settingsLoaded = true,
                youtubeUrlInput = settings.youtubeUrl,
                durationInput = settings.durationMinutes.toString(),
                volumeEnabled = settings.volumeEnabled,
                // 다른 기기에서 저장한 단계가 이 기기 최대보다 클 수 있어 슬라이더 범위로 맞춤
                volumeLevel = settings.volumeLevel.coerceIn(0, maxLevel),
                maxVolumeLevel = maxLevel,
            )
        }
    }

    /** 설정 앱에서 바뀔 수 있는 권한 상태 재조회 */
    fun refreshStatus() {
        mutableUiState.update {
            it.copy(
                doNotDisturbAccessGranted = systemStatus.hasDoNotDisturbAccess(),
                overlayGranted = systemStatus.canDrawOverlays(),
                notificationListenerEnabled = systemStatus.isNotificationListenerEnabled(),
                notificationsEnabled = systemStatus.canShowNotification(),
            )
        }
    }

    fun updateYoutubeUrlInput(input: String) {
        mutableUiState.update { it.copy(youtubeUrlInput = input, youtubeUrlError = false) }
    }

    fun updateDurationInput(input: String) {
        mutableUiState.update { it.copy(durationInput = input, durationError = false) }
    }

    fun setVolumeEnabled(enabled: Boolean) {
        mutableUiState.update { it.copy(volumeEnabled = enabled) }
    }

    fun updateVolumeLevel(level: Int) {
        mutableUiState.update { it.copy(volumeLevel = level.coerceIn(0, it.maxVolumeLevel)) }
    }

    fun save() {
        launchSaveIfIdle {
            if (saveInputs()) sendEvent(EventShowToast(R.string.sleep_routine_save_completed))
        }
    }

    /**
     * 입력을 저장한 뒤 수면 루틴 시작
     *
     * 서비스는 저장된 설정을 읽으므로 이전 설정으로 시작하지 않도록 저장에 성공했을 때만 시작
     */
    fun startRoutine() {
        launchSaveIfIdle {
            if (saveInputs()) systemStatus.startRoutine()
        }
    }

    fun stopRoutine() {
        systemStatus.stopRoutine()
    }

    fun openDoNotDisturbSettings() {
        sendEvent(EventOpenDoNotDisturbSettings)
    }

    fun openOverlaySettings() {
        sendEvent(EventOpenOverlaySettings)
    }

    fun openNotificationListenerSettings() {
        sendEvent(EventOpenNotificationListenerSettings)
    }

    fun openNotificationSettings() {
        sendEvent(EventOpenNotificationSettings)
    }

    /**
     * 설정을 불러온 뒤 진행 중인 저장·시작이 없을 때만 [block] 실행
     *
     * 불러오기 전에 저장하면 입력란의 기본값이 저장된 설정을 덮어쓰고, 연타한 시작은 저장·시작을 겹쳐 실행하므로 무시
     */
    private fun launchSaveIfIdle(block: suspend () -> Unit) {
        if (!uiState.value.settingsLoaded || saveJob?.isActive == true) return
        saveJob = launch { block() }
    }

    /**
     * 입력 검증 후 저장, 저장 성공 여부 반환
     *
     * 검증에 실패하면 저장하지 않고 해당 입력란에 오류를 표시하며, 실행 버튼이 스크롤 아래에 있어 오류 칸이 화면 밖일 수 있으므로 입력 확인 토스트도 표시.
     * 저장에 실패하면 실패 토스트 표시
     */
    private suspend fun saveInputs(): Boolean {
        val state = uiState.value
        val urlValid = SleepRoutineSettings.isValidYoutubeUrl(state.youtubeUrlInput)
        val minutes = SleepRoutineSettings.parseDurationMinutes(state.durationInput)
        if (!urlValid || minutes == null) {
            mutableUiState.update { it.copy(youtubeUrlError = !urlValid, durationError = minutes == null) }
            sendEvent(EventShowToast(R.string.sleep_routine_input_invalid))
            return false
        }
        val settings = SleepRoutineSettings(
            youtubeUrl = state.youtubeUrlInput.trim(),
            durationMinutes = minutes,
            volumeEnabled = state.volumeEnabled,
            volumeLevel = state.volumeLevel,
        )
        return repository.saveSettings(settings)
            .onFailure { sendEvent(EventShowToast(R.string.sleep_routine_save_failed)) }
            .isSuccess
    }
}
