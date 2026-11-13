package com.black.app.ui.main.usagetimer

import androidx.annotation.StringRes
import com.black.app.R
import com.black.app.model.UsageTimerRepository
import com.black.app.ui.main.usagetimer.UsageTimerScreen
import com.black.app.util.InstalledAppResolver
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
    /** 문자열 리소스 토스트 표시, [formatArgs]는 리소스 서식 인자 */
    data class EventShowToast(
        @StringRes val messageResId: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : UsageTimerEvent()
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
        val pauseDuration = validPauseDurationMinutes() ?: return
        launch {
            val result = repository.savePauseDuration(pauseDuration)
            sendEvent(EventShowToast(saveResultMessage(result, R.string.usage_timer_save_completed)))
        }
    }

    fun onClickPause() {
        val pauseDuration = validPauseDurationMinutes() ?: return
        launch {
            // 일시정지 시각을 저장하지 못하면 타이머를 떼지 않고 실패만 안내
            if (repository.savePauseDuration(pauseDuration).isFailure || repository.pause(pauseDuration).isFailure) {
                sendEvent(EventShowToast(R.string.usage_timer_save_failed))
                return@launch
            }
            updatePauseRemainTime()
            sendEvent(EventDetachTimerView)
            sendEvent(EventShowToast(R.string.usage_timer_paused, listOf(pauseDuration)))
        }
    }

    fun onClickCancelPause() {
        launch {
            val result = repository.cancelPause()
            updatePauseRemainTime()
            sendEvent(EventShowToast(saveResultMessage(result, R.string.usage_timer_pause_canceled)))
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
            if (repository.saveSelectedApps(packageNames).isFailure) {
                sendEvent(EventShowToast(R.string.usage_timer_save_failed))
                return@launch
            }
            mutableUiState.update {
                it.copy(selectedApps = packageNames.map(installedAppResolver::resolve))
            }
        }
    }

    /** 저장 결과에 맞는 토스트 메시지(성공이면 [successMessageResId]) */
    @StringRes
    private fun saveResultMessage(result: Result<Unit>, @StringRes successMessageResId: Int): Int {
        return if (result.isSuccess) successMessageResId else R.string.usage_timer_save_failed
    }

    /** 입력이 1분 이상의 정수면 그 값, 아니면 안내 토스트를 보내고 null */
    private fun validPauseDurationMinutes(): Int? {
        val minutes = uiState.value.pauseDurationInput.toIntOrNull()
        if (minutes == null || minutes < 1) {
            sendEvent(EventShowToast(R.string.usage_timer_pause_duration_invalid))
            return null
        }
        return minutes
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
