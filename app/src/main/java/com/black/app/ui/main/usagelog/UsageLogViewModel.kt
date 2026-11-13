package com.black.app.ui.main.usagelog

import androidx.annotation.StringRes
import com.black.app.R
import com.black.app.model.StoredUsageLogRecord
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.ui.common.selectapp.InstalledAppResolver
import com.black.app.ui.main.usagelog.UsageLogScreen
import com.black.app.usagelog.UsageLogCollectionController
import com.black.app.usagelog.collect.UsageStatsCollector
import com.black.app.usagelog.record.NotificationPostedRecord
import com.black.app.usagelog.record.NotificationRemovedRecord
import com.black.app.usagelog.upload.DiscordWebhookFormat
import com.black.app.usagelog.upload.SendReason
import com.black.app.usagelog.upload.UsageLogCipher
import com.black.app.usagelog.work.UsageLogScheduler
import com.black.core.util.Log
import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** [UsageLogScreen]의 상태·이벤트 관리 ViewModel */
@HiltViewModel
class UsageLogViewModel @Inject constructor(
    private val settings: UsageLogSettingsRepository,
    repository: UsageLogRepository,
    private val controller: UsageLogCollectionController,
    private val collector: UsageStatsCollector,
    private val status: UsageLogSystemStatus,
    private val installedAppResolver: InstalledAppResolver,
    private val scheduler: UsageLogScheduler,
) : EventViewModel() {

    /** 기기 사용 기록 화면 일회성 이벤트 */
    sealed class UsageLogEvent : ViewModelEvent

    /** 사용 정보 접근 설정 화면 이동 */
    data object EventOpenUsageAccessSettings : UsageLogEvent()
    /** 알림 접근 설정 화면 이동 */
    data object EventOpenNotificationAccessSettings : UsageLogEvent()
    /**
     * 위치 권한을 고를 앱 정보 화면 이동
     *
     * 특정 권한 화면으로 바로 가는 공개 인텐트가 없어 앱 정보 화면을 엶.
     */
    data object EventOpenLocationSettings : UsageLogEvent()
    /** 배터리 최적화 설정 화면 이동 */
    data object EventOpenBatteryOptimizationSettings : UsageLogEvent()
    /** 문자열 리소스 토스트 표시 */
    data class EventShowToast(@StringRes val messageResId: Int) : UsageLogEvent()

    /** 암호화 키 클립보드 복사 */
    data class EventCopyEncryptionKey(val key: String) : UsageLogEvent()

    private var collectionToggleJob: Job? = null

    /** 패키지별 앱 이름 캐시(변환이 IO 디스패처에서 차례로 실행돼 한 번에 하나만 접근) */
    private val appLabelCache = HashMap<String, String>()

    private val mutableUiState = MutableStateFlow(UsageLogUiState())
    val uiState: StateFlow<UsageLogUiState> = mutableUiState.asStateFlow()

    init {
        refreshSystemStatus()
        // 화면 진입 때 사라진 주기 작업 예약 복구
        launch { controller.ensureScheduled() }
        // 스위치·집 Wi-Fi·최근 기록은 저장된 값을 따름(저장 실패 시 이전 값으로 표시)
        launch {
            settings.collectionEnabled.collect { enabled ->
                mutableUiState.update { it.copy(collectionEnabled = enabled) }
            }
        }
        launch {
            settings.homeSsids.collect { ssids ->
                mutableUiState.update { it.copy(homeSsids = ssids.sorted()) }
            }
        }
        launch {
            settings.webhookUrl.collect { url ->
                mutableUiState.update {
                    it.copy(webhookUrl = url, webhookUrlValid = DiscordWebhookFormat.isValidWebhookUrl(url))
                }
            }
        }
        launch {
            settings.encryptionKey.collect { key ->
                mutableUiState.update { it.copy(encryptionKey = key) }
            }
        }
        launch {
            settings.lastSendResult.collect { result ->
                mutableUiState.update { it.copy(lastSendResult = result) }
            }
        }
        launch {
            repository.unsentCount().collect { count ->
                mutableUiState.update { it.copy(pendingCount = count) }
            }
        }
        launch {
            // 앱 이름 조회가 블로킹 호출이라 화면용 항목 변환은 IO 디스패처에서 실행
            repository.recentRecords(RECENT_RECORD_LIMIT)
                .map(::toRecordItems)
                .flowOn(Dispatchers.IO)
                .collect { records ->
                    mutableUiState.update { it.copy(recentRecords = records) }
                }
        }
    }

    /** 설정 앱에서 바뀔 수 있는 권한과 현재 Wi-Fi 이름 재조회 */
    private fun refreshSystemStatus() {
        mutableUiState.update {
            it.copy(
                usageAccessGranted = status.isUsageAccessGranted(),
                notificationAccessGranted = status.isNotificationAccessGranted(),
                fineLocationGranted = status.isFineLocationGranted(),
                backgroundLocationGranted = status.isBackgroundLocationGranted(),
                batteryOptimizationIgnored = status.isBatteryOptimizationIgnored(),
                currentSsid = status.currentSsid(),
            )
        }
    }

    /**
     * 권한과 현재 Wi-Fi 이름을 다시 읽고 수집
     *
     * 권한을 허용하고 돌아온 직후의 기록을 바로 반영함.
     */
    fun refreshStatus() {
        refreshSystemStatus()
        launch {
            // 조회·저장 실패로 앱이 종료되지 않도록 예외는 클래스 이름만 로그로 남김
            try {
                collector.collect()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.w("[UsageLogViewModel] 수집 실패: ${failure.javaClass.simpleName}")
            }
        }
    }

    fun setCollectionEnabled(enabled: Boolean) {
        // 켜기·끄기가 진행 중이면 겹치지 않도록 새 요청 무시
        if (collectionToggleJob?.isActive == true) return
        collectionToggleJob = launch {
            val result = if (enabled) controller.enable() else controller.disable()
            result.onFailure { sendEvent(EventShowToast(R.string.usage_log_save_failed)) }
        }
    }

    /**
     * 지금 연결된 Wi-Fi를 집 Wi-Fi로 등록
     *
     * 이름을 읽을 수 없으면 안내 토스트를 보냄.
     */
    fun registerCurrentWifi() {
        val ssid = status.currentSsid()
        if (ssid == null) {
            sendEvent(EventShowToast(R.string.usage_log_wifi_unknown))
            return
        }
        launch {
            settings.addHomeSsid(ssid).onFailure { sendEvent(EventShowToast(R.string.usage_log_save_failed)) }
        }
    }

    fun removeHomeWifi(ssid: String) {
        launch {
            settings.removeHomeSsid(ssid).onFailure { sendEvent(EventShowToast(R.string.usage_log_save_failed)) }
        }
    }

    /**
     * 웹후크 주소 저장
     *
     * 입력마다 호출되며 앞뒤 공백을 제거해 저장함. 주소는 로그에 남기지 않음.
     */
    fun updateWebhookUrl(text: String) {
        launch {
            settings.setWebhookUrl(text.trim()).onFailure { sendEvent(EventShowToast(R.string.usage_log_save_failed)) }
        }
    }

    fun copyEncryptionKey() {
        sendEvent(EventCopyEncryptionKey(mutableUiState.value.encryptionKey))
    }

    fun requestRegenerateKey() {
        mutableUiState.update { it.copy(showRegenerateKeyConfirm = true) }
    }

    fun dismissRegenerateKey() {
        mutableUiState.update { it.copy(showRegenerateKeyConfirm = false) }
    }

    /**
     * 암호화 키를 새로 만들어 저장
     *
     * 이 뒤 전송분부터 새 키로 암호화함. 저장이 실패해도 대화상자는 닫음.
     */
    fun confirmRegenerateKey() {
        dismissRegenerateKey()
        launch {
            settings.setEncryptionKey(UsageLogCipher.encodeKey(UsageLogCipher.generateKey()))
                .onFailure { sendEvent(EventShowToast(R.string.usage_log_save_failed)) }
        }
    }

    /**
     * 지금 보내기
     *
     * 저장된 웹후크 주소가 올바르면 수동 전송을 예약하고, 아니면 안내 토스트를 보냄.
     */
    fun sendNow() {
        if (!mutableUiState.value.webhookUrlValid) {
            sendEvent(EventShowToast(R.string.usage_log_webhook_invalid))
            return
        }
        scheduler.enqueueSend(SendReason.MANUAL)
    }

    fun openUsageAccessSettings() {
        sendEvent(EventOpenUsageAccessSettings)
    }

    fun openNotificationAccessSettings() {
        sendEvent(EventOpenNotificationAccessSettings)
    }

    /**
     * 위치 항상 허용을 고를 설정 화면 이동
     *
     * 권한 창으로 요청하면 앱 사용 중 허용 창과 항상 허용 설정 이동을 두 번에 나눠 거쳐야 하므로,
     * 처음부터 설정 화면에서 항상 허용을 고르게 함. 이미 항상 허용이면 이동하지 않음.
     */
    fun openLocationSettings() {
        if (!status.isFineLocationGranted() || !status.isBackgroundLocationGranted()) {
            sendEvent(EventOpenLocationSettings)
        }
    }

    fun openBatteryOptimizationSettings() {
        sendEvent(EventOpenBatteryOptimizationSettings)
    }

    /**
     * 저장된 기록을 화면용 항목으로 변환
     *
     * 알림 제거 기록의 앱 이름은 같은 목록의 같은 key 알림 게시 기록에서 가져오고, 없을 때만 패키지별 캐시를 거쳐 조회함.
     */
    private fun toRecordItems(records: List<StoredUsageLogRecord>): List<UsageLogRecordItem> {
        val postedAppNames = records.mapNotNull { it.record as? NotificationPostedRecord }
            .associate { it.key to it.appName }
        return records.map { stored ->
            val record = stored.record
            val appLabel = (record as? NotificationRemovedRecord)?.let { removed ->
                postedAppNames[removed.key]
                    ?: appLabelCache.getOrPut(removed.packageName) { installedAppResolver.resolveLabel(removed.packageName) }
            }
            UsageLogRecordItem(stored.id, record, appLabel)
        }
    }

    private companion object {
        const val RECENT_RECORD_LIMIT = 100
    }
}
