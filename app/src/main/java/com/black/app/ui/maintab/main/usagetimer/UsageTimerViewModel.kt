package com.black.app.ui.maintab.main.usagetimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.black.app.model.UsageTimerModel
import com.black.app.ui.common.selectapp.InstalledAppResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UsageTimer 화면 상태·이벤트 관리 ViewModel */
@HiltViewModel
class UsageTimerViewModel @Inject constructor(
    private val model: UsageTimerModel,
    private val installedAppResolver: InstalledAppResolver,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(UsageTimerUiState())
    val uiState: StateFlow<UsageTimerUiState> = mutableUiState.asStateFlow()

    private val eventChannel = Channel<UsageTimerEvent>(Channel.BUFFERED)
    val events: Flow<UsageTimerEvent> = eventChannel.receiveAsFlow()

    init {
        mutableUiState.update {
            it.copy(
                pauseRemainTimeMillis = currentPauseRemainTimeMillis(),
                pauseDurationInput = model.getPauseDuration().toString(),
                selectedApps = model.getSelectedApps().map(installedAppResolver::resolve),
            )
        }
    }

    fun onPauseDurationInputChanged(input: String) {
        mutableUiState.update { it.copy(pauseDurationInput = input) }
    }

    fun onClickShow() {
        sendEvent(UsageTimerEvent.ShowTimerView)
    }

    fun onClickSave() {
        model.savePauseDuration(pauseDurationMinutes())
        sendEvent(UsageTimerEvent.ShowToast("Saved"))
    }

    fun onClickPause() {
        val pauseDuration = pauseDurationMinutes()
        model.savePauseDuration(pauseDuration)
        model.pause(pauseDuration)
        updatePauseRemainTime()
        sendEvent(UsageTimerEvent.DetachTimerView)
        sendEvent(UsageTimerEvent.ShowToast("Pause : ${pauseDuration}m"))
    }

    fun onClickCancelPause() {
        model.cancelPause()
        updatePauseRemainTime()
        sendEvent(UsageTimerEvent.ShowToast("Pause canceled"))
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
        model.saveSelectedApps(packageNames)
        mutableUiState.update {
            it.copy(selectedApps = packageNames.map(installedAppResolver::resolve))
        }
    }

    private fun pauseDurationMinutes(): Int {
        return uiState.value.pauseDurationInput.toIntOrNull() ?: 0
    }

    private fun updatePauseRemainTime() {
        mutableUiState.update { it.copy(pauseRemainTimeMillis = currentPauseRemainTimeMillis()) }
    }

    /** 일시정지 종료 시각까지 남은 시간(ms). 지났으면 0 */
    private fun currentPauseRemainTimeMillis(): Long {
        val remain = model.getPauseEndTime() - System.currentTimeMillis()
        return remain.coerceAtLeast(0L)
    }

    private fun sendEvent(event: UsageTimerEvent) {
        viewModelScope.launch { eventChannel.send(event) }
    }
}
