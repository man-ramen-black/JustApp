package com.black.app.ui.maintab.main.usagetimer

import com.black.app.model.UsageTimerRepository
import com.black.app.ui.common.selectapp.InstalledAppResolver
import com.black.app.ui.maintab.main.usagetimer.UsageTimerScreen
import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** UsageTimer 화면 상태·이벤트 관리 ViewModel [UsageTimerScreen] */
@HiltViewModel
class UsageTimerViewModel @Inject constructor(
    private val repository: UsageTimerRepository,
    private val installedAppResolver: InstalledAppResolver,
) : EventViewModel() {

    /** UsageTimer 화면 일회성 이벤트 */
    sealed class UsageTimerEvent : ViewModelEvent

    /** 사용 시간 타이머 오버레이 표시 */
    data object EventShowTimerView : UsageTimerEvent()
    /** 토스트 메시지 표시 */
    data class EventShowToast(val message: String) : UsageTimerEvent()
    /** 서비스에 떠 있는 타이머 오버레이 제거 */
    data object EventDetachTimerView : UsageTimerEvent()
    /** 접근성 설정 화면 이동 */
    data object EventOpenAccessibilitySettings : UsageTimerEvent()
    /** 앱 선택 다이얼로그 표시(현재 선택 목록 전달) */
    data class EventOpenSelectApp(val checkedPackageNames: List<String>) : UsageTimerEvent()

    private val mutableUiState = MutableStateFlow(UsageTimerUiState())
    val uiState: StateFlow<UsageTimerUiState> = mutableUiState.asStateFlow()

    init {
        launch {
            val pauseRemainTimeMillis = currentPauseRemainTimeMillis()
            val pauseDurationInput = repository.getPauseDuration().toString()
            val selectedApps = repository.getSelectedApps().map(installedAppResolver::resolve)
            mutableUiState.update {
                it.copy(
                    pauseRemainTimeMillis = pauseRemainTimeMillis,
                    pauseDurationInput = pauseDurationInput,
                    selectedApps = selectedApps,
                )
            }
        }
    }

    fun onPauseDurationInputChanged(input: String) {
        mutableUiState.update { it.copy(pauseDurationInput = input) }
    }

    fun onClickShow() {
        sendEvent(EventShowTimerView)
    }

    fun onClickSave() {
        launch {
            repository.savePauseDuration(pauseDurationMinutes())
            sendEvent(EventShowToast("Saved"))
        }
    }

    fun onClickPause() {
        launch {
            val pauseDuration = pauseDurationMinutes()
            repository.savePauseDuration(pauseDuration)
            repository.pause(pauseDuration)
            updatePauseRemainTime()
            sendEvent(EventDetachTimerView)
            sendEvent(EventShowToast("Pause : ${pauseDuration}m"))
        }
    }

    fun onClickCancelPause() {
        launch {
            repository.cancelPause()
            updatePauseRemainTime()
            sendEvent(EventShowToast("Pause canceled"))
        }
    }

    fun onFinishTimer() {
        mutableUiState.update { it.copy(pauseRemainTimeMillis = 0L) }
    }

    fun onClickAccessibility() {
        sendEvent(EventOpenAccessibilitySettings)
    }

    fun onClickSelectApp() {
        sendEvent(
            EventOpenSelectApp(uiState.value.selectedApps.map { it.packageName })
        )
    }

    fun onAppsSelected(packageNames: List<String>) {
        launch {
            repository.saveSelectedApps(packageNames)
            mutableUiState.update {
                it.copy(selectedApps = packageNames.map(installedAppResolver::resolve))
            }
        }
    }

    private fun pauseDurationMinutes(): Int {
        return uiState.value.pauseDurationInput.toIntOrNull() ?: 0
    }

    private suspend fun updatePauseRemainTime() {
        val pauseRemainTimeMillis = currentPauseRemainTimeMillis()
        mutableUiState.update { it.copy(pauseRemainTimeMillis = pauseRemainTimeMillis) }
    }

    /** 일시정지 종료 시각까지 남은 시간(ms). 지났으면 0 */
    private suspend fun currentPauseRemainTimeMillis(): Long {
        val remain = repository.getPauseEndTime() - System.currentTimeMillis()
        return remain.coerceAtLeast(0L)
    }
}
