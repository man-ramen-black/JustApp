package com.black.feature.usagetimer.ui

import androidx.annotation.StringRes
import com.black.core.system.AppUsageTimeRepository
import com.black.core.ui.component.OverlayPositionInput
import com.black.core.ui.selectapp.selectedAppUiItem
import com.black.core.util.AccessibilityServiceStatus
import com.black.core.util.InstalledAppResolver
import com.black.core.util.Log
import com.black.core.util.OverlayPermissionStatus
import com.black.core.util.UsageAccessStatus
import com.black.core.util.runSuspendCatching
import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import com.black.feature.usagetimer.R
import com.black.feature.usagetimer.data.UsageTimerRepository
import com.black.feature.usagetimer.overlay.UsageTimerGlobal
import com.black.feature.usagetimer.ui.UsageTimerScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours

/** [UsageTimerScreen]의 상태·이벤트 관리 ViewModel */
@HiltViewModel
class UsageTimerViewModel @Inject constructor(
    private val repository: UsageTimerRepository,
    private val installedAppResolver: InstalledAppResolver,
    private val overlayPermissionStatus: OverlayPermissionStatus,
    private val accessibilityServiceStatus: AccessibilityServiceStatus,
    private val usageAccessStatus: UsageAccessStatus,
    private val appUsageTimeRepository: AppUsageTimeRepository,
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
    /** 이 앱의 다른 앱 위에 표시 권한 설정 화면 이동 */
    data object EventOpenOverlaySettings : UsageTimerEvent()
    /** 사용 정보 접근 설정 화면 이동 */
    data object EventOpenUsageAccessSettings : UsageTimerEvent()
    /** 앱 선택 다이얼로그 표시(현재 선택 목록 전달) */
    data class EventOpenSelectApp(val checkedPackageNames: List<String>) : UsageTimerEvent()

    private val mutableUiState = MutableStateFlow(UsageTimerUiState())
    val uiState: StateFlow<UsageTimerUiState> = mutableUiState.asStateFlow()

    /** 직전 최근 사용 시간 조회. 새 조회가 시작되면 취소해 늦게 끝난 옛 결과가 덮지 않게 함 */
    private var recentUsageJob: Job? = null

    init {
        // 첫 화면부터 실제 권한 상태 표시
        refreshStatus()
        launch {
            val pauseRemainTimeMillis = repository.getPauseRemainTimeMillis()
            val pauseDurationInput = repository.getPauseDuration().toString()
            val selectedApps = repository.getSelectedApps().map(installedAppResolver::selectedAppUiItem)
            mutableUiState.update {
                it.copy(
                    pauseRemainTimeMillis = pauseRemainTimeMillis,
                    pauseDurationInput = pauseDurationInput,
                    selectedApps = selectedApps,
                )
            }
        }
        // 선택 앱 해석을 기다리지 않도록 위치는 따로 읽고, 입력 중인 값을 덮지 않도록 한 번만 읽음
        launch {
            val positionInput = OverlayPositionInput.from(repository.getPositionFlow().first())
            mutableUiState.update { it.copy(positionInput = positionInput) }
        }
    }

    /** 설정 앱에서 권한을 바꾸고 돌아온 경우를 위한 권한 상태 갱신 */
    fun refreshStatus() {
        // 세 권한 상태 조회
        val accessibilityServiceEnabled = accessibilityServiceStatus.isAccessibilityServiceEnabled()
        val overlayPermissionGranted = overlayPermissionStatus.canDrawOverlays()
        val usageAccessGranted = usageAccessStatus.isUsageAccessGranted()
        // UiState 반영
        mutableUiState.update {
            it.copy(
                accessibilityServiceEnabled = accessibilityServiceEnabled,
                overlayPermissionGranted = overlayPermissionGranted,
                usageAccessGranted = usageAccessGranted,
            )
        }
        // 권한이 바뀌었을 수 있으므로 사용 시간도 다시 조회
        refreshRecentUsage()
    }

    /**
     * 선택 앱의 최근 [RECENT_USAGE_PERIOD] 사용 시간을 조회해 UiState에 반영
     *
     * 권한이 없거나 조회에 실패하면 사용 시간을 null로 둠. 앱 사이를 잠깐 오간 구간을 이어 붙이는 간격은
     * 오버레이 타이머가 같은 이탈을 같은 사용으로 보는 [UsageTimerGlobal.SESSION_GRACE_MILLIS]와 맞춤.
     */
    private fun refreshRecentUsage() {
        recentUsageJob?.cancel()
        recentUsageJob = launch {
            val recentUsageMillis = runSuspendCatching {
                appUsageTimeRepository.getRecentUsageMillis(
                    repository.getSelectedApps(),
                    RECENT_USAGE_PERIOD,
                    UsageTimerGlobal.SESSION_GRACE_MILLIS,
                )
            }.onFailure { failure ->
                Log.w("[UsageTimerViewModel] 최근 사용 시간 조회 실패: ${failure::class.java.simpleName}")
            }.getOrNull()
            mutableUiState.update { it.copy(recentUsageMillis = recentUsageMillis) }
        }
    }

    fun onPauseDurationInputChanged(input: String) {
        mutableUiState.update { it.copy(pauseDurationInput = input) }
    }

    /**
     * 표시 위치 입력을 화면에 반영하고 저장
     *
     * 저장에 실패하면 실패 토스트를 보냄.
     */
    fun updatePosition(input: OverlayPositionInput) {
        mutableUiState.update { it.copy(positionInput = input) }
        launch {
            if (repository.savePosition(input.toPosition()).isFailure) {
                sendEvent(EventShowToast(R.string.usage_timer_save_failed))
            }
        }
    }

    fun onClickShow() {
        // 권한 없이 오버레이 창을 추가하면 붙지 않으므로 권한부터 안내
        if (!overlayPermissionStatus.canDrawOverlays()) {
            showOverlayPermissionDialog()
            return
        }
        sendEvent(EventShowTimerView)
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

    /** 사용 정보 접근 설정 화면으로 이동 */
    fun openUsageAccessSettings() {
        sendEvent(EventOpenUsageAccessSettings)
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
                it.copy(selectedApps = packageNames.map(installedAppResolver::selectedAppUiItem))
            }
            refreshRecentUsage()
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
        val pauseRemainTimeMillis = repository.getPauseRemainTimeMillis()
        mutableUiState.update { it.copy(pauseRemainTimeMillis = pauseRemainTimeMillis) }
    }

    companion object {
        /** 선택 앱 사용 시간을 보여 줄 기간(지금부터 거슬러 올라감) */
        val RECENT_USAGE_PERIOD = 24.hours
    }
}
