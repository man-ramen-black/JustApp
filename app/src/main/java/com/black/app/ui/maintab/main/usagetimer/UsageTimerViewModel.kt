package com.black.app.ui.maintab.main.usagetimer

import com.black.app.model.UsageTimerRepository
import com.black.app.ui.common.selectapp.InstalledAppResolver
import com.black.app.ui.maintab.main.usagetimer.UsageTimerScreen
import com.black.core.viewmodel.EventViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** UsageTimer 화면 상태·이벤트 관리 ViewModel [UsageTimerScreen] */
@HiltViewModel
class UsageTimerViewModel @Inject constructor(
    private val repository: UsageTimerRepository,
    private val installedAppResolver: InstalledAppResolver,
) : EventViewModel() {

    private val mutableUiState = MutableStateFlow(UsageTimerUiState())
    val uiState: StateFlow<UsageTimerUiState> = mutableUiState.asStateFlow()

    private val eventChannel = Channel<UsageTimerEvent>(Channel.BUFFERED)
    val usageTimerEvents: Flow<UsageTimerEvent> = eventChannel.receiveAsFlow()

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
        sendEvent(UsageTimerEvent.ShowTimerView)
    }

    fun onClickSave() {
        launch {
            repository.savePauseDuration(pauseDurationMinutes())
            sendEvent(UsageTimerEvent.ShowToast("Saved"))
        }
    }

    fun onClickPause() {
        launch {
            val pauseDuration = pauseDurationMinutes()
            repository.savePauseDuration(pauseDuration)
            repository.pause(pauseDuration)
            updatePauseRemainTime()
            sendEvent(UsageTimerEvent.DetachTimerView)
            sendEvent(UsageTimerEvent.ShowToast("Pause : ${pauseDuration}m"))
        }
    }

    fun onClickCancelPause() {
        launch {
            repository.cancelPause()
            updatePauseRemainTime()
            sendEvent(UsageTimerEvent.ShowToast("Pause canceled"))
        }
    }

    fun onFinishTimer() {
        mutableUiState.update { it.copy(pauseRemainTimeMillis = 0L) }
    }

    fun onClickAccessibility() {
        sendEvent(UsageTimerEvent.OpenAccessibilitySettings)
    }

    fun onClickSelectApp() {
        sendEvent(
            UsageTimerEvent.OpenSelectApp(uiState.value.selectedApps.map { it.packageName })
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

    private fun sendEvent(event: UsageTimerEvent) {
        launch { eventChannel.send(event) }
    }
}
