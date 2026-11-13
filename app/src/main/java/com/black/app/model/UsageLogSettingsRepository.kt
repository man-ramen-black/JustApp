package com.black.app.model

import android.content.Context
import com.black.app.model.datastore.UsageLogDataStore
import com.black.app.usagelog.record.OpenUsageSession
import com.black.app.usagelog.record.WifiState
import com.black.app.usagelog.upload.LastSendResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** 기기 사용 기록의 수집 설정, 집 SSID, 수집 진행 상태, 전송 설정 저장·조회 */
class UsageLogSettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = UsageLogDataStore(context)

    val collectionEnabled: Flow<Boolean> get() = dataStore.getCollectionEnabledFlow()

    suspend fun isCollectionEnabled(): Boolean = dataStore.getCollectionEnabled()

    suspend fun setCollectionEnabled(enabled: Boolean): Result<Unit> = dataStore.updateCollectionEnabled(enabled)

    /**
     * 수집을 끈 시각
     *
     * 0이면 끈 기록이 없음.
     */
    suspend fun getCollectionOffSince(): Long = dataStore.getCollectionOffSince()

    suspend fun setCollectionOffSince(time: Long): Result<Unit> = dataStore.updateCollectionOffSince(time)

    val homeSsids: Flow<Set<String>> get() = dataStore.getHomeSsidsFlow()

    suspend fun getHomeSsids(): Set<String> = dataStore.getHomeSsids()

    suspend fun addHomeSsid(ssid: String): Result<Unit> = dataStore.addHomeSsid(ssid)

    suspend fun removeHomeSsid(ssid: String): Result<Unit> = dataStore.removeHomeSsid(ssid)

    suspend fun getUsageCursor(): Long = dataStore.getUsageCursor()

    suspend fun setUsageCursor(time: Long): Result<Unit> = dataStore.updateUsageCursor(time)

    suspend fun getOpenSession(): OpenUsageSession? = dataStore.getOpenSession()

    suspend fun setOpenSession(session: OpenUsageSession?): Result<Unit> = dataStore.updateOpenSession(session)

    /**
     * 열린 구간과 커서를 한 번에 저장
     *
     * 둘 다 저장되거나 하나도 저장되지 않음. [session]이 null이면 열린 구간을 지움.
     */
    suspend fun setUsageProgress(session: OpenUsageSession?, cursor: Long): Result<Unit> =
        dataStore.updateUsageProgress(session, cursor)

    /**
     * 마지막 Wi-Fi 상태
     *
     * 저장값이 없으면 `WifiState(false, null)`.
     */
    suspend fun getLastWifiState(): WifiState = dataStore.getLastWifiState()

    suspend fun setLastWifiState(state: WifiState): Result<Unit> = dataStore.updateLastWifiState(state)

    /**
     * 설치 식별자
     *
     * 없으면 만들어 저장하며, 저장이 실패하면 실패 결과.
     */
    suspend fun getInstallId(): Result<String> = dataStore.getInstallId()

    val webhookUrl: Flow<String> get() = dataStore.getWebhookUrlFlow()

    suspend fun getWebhookUrl(): String = dataStore.getWebhookUrl()

    suspend fun setWebhookUrl(url: String): Result<Unit> = dataStore.updateWebhookUrl(url)

    val encryptionKey: Flow<String> get() = dataStore.getEncryptionKeyFlow()

    suspend fun getEncryptionKey(): String = dataStore.getEncryptionKey()

    suspend fun setEncryptionKey(key: String): Result<Unit> = dataStore.updateEncryptionKey(key)

    /**
     * 암호화 키 보장
     *
     * 없으면 만들어 저장하며, 저장이 실패하면 실패 결과.
     */
    suspend fun ensureEncryptionKey(): Result<String> = dataStore.ensureEncryptionKey()

    /**
     * 마지막으로 전송한 묶음 순번
     *
     * 0이면 아직 보낸 묶음이 없음.
     */
    suspend fun getSendSequence(): Long = dataStore.getSendSequence()

    suspend fun setSendSequence(sequence: Long): Result<Unit> = dataStore.updateSendSequence(sequence)

    val lastSendResult: Flow<LastSendResult?> get() = dataStore.getLastSendResultFlow()

    suspend fun setLastSendResult(result: LastSendResult): Result<Unit> = dataStore.updateLastSendResult(result)
}
