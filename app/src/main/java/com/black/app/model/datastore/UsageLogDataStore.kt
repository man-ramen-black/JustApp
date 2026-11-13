package com.black.app.model.datastore

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.black.app.usagelog.record.OpenUsageSession
import com.black.app.usagelog.record.WifiState
import com.black.app.usagelog.upload.LastSendResult
import com.black.app.usagelog.upload.UsageLogCipher
import com.black.core.model.BaseDataStore
import com.black.core.model.PreferenceUpdate
import com.black.core.model.blackDataStore
import com.black.core.util.JsonUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/**
 * 기기 사용 기록의 수집 설정과 진행 상태 저장소
 *
 * SSID(집 SSID 목록, 마지막 Wi-Fi 상태)와 열린 구간은 개인 데이터라 로그에 값을 남기지 않음.
 * 웹후크 주소, 암호화 키, 설치 식별자도 비밀 값이라 로그에 값을 남기지 않음.
 */
class UsageLogDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : BaseDataStore(context) {

    companion object {
        private val Context.usageLogDataStore: DataStore<Preferences> by blackDataStore(name = "usage_log")

        private val KEY_COLLECTION_ENABLED = booleanPreferencesKey("UsageLogCollectionEnabled")
        private val KEY_COLLECTION_OFF_SINCE = longPreferencesKey("UsageLogCollectionOffSince")
        private val KEY_HOME_SSIDS = stringSetPreferencesKey("UsageLogHomeSsids")
        private val KEY_USAGE_CURSOR = longPreferencesKey("UsageLogUsageCursor")
        private val KEY_OPEN_SESSION = stringPreferencesKey("UsageLogOpenSession")
        private val KEY_LAST_WIFI_STATE = stringPreferencesKey("UsageLogLastWifiState")
        private val KEY_INSTALL_ID = stringPreferencesKey("UsageLogInstallId")
        private val KEY_WEBHOOK_URL = stringPreferencesKey("UsageLogWebhookUrl")
        private val KEY_ENCRYPTION_KEY = stringPreferencesKey("UsageLogEncryptionKey")
        private val KEY_SEND_SEQUENCE = longPreferencesKey("UsageLogSendSequence")
        private val KEY_LAST_SEND_RESULT = stringPreferencesKey("UsageLogLastSendResult")
    }

    override fun getDataStore(context: Context): DataStore<Preferences> = context.usageLogDataStore

    fun getCollectionEnabledFlow(): Flow<Boolean> = flow(KEY_COLLECTION_ENABLED).map { it ?: false }

    fun getHomeSsidsFlow(): Flow<Set<String>> = flow(KEY_HOME_SSIDS).map { it ?: emptySet() }

    suspend fun getCollectionEnabled(): Boolean = get(KEY_COLLECTION_ENABLED, false)

    suspend fun updateCollectionEnabled(enabled: Boolean): Result<Unit> = update(KEY_COLLECTION_ENABLED, enabled)

    suspend fun getCollectionOffSince(): Long = get(KEY_COLLECTION_OFF_SINCE, 0L)

    suspend fun updateCollectionOffSince(time: Long): Result<Unit> = update(KEY_COLLECTION_OFF_SINCE, time)

    suspend fun getHomeSsids(): Set<String> = get(KEY_HOME_SSIDS, emptySet(), logValue = false)

    /**
     * 집 SSID 추가
     *
     * 읽고 쓰기를 한 번에 처리.
     */
    suspend fun addHomeSsid(ssid: String): Result<Unit> =
        updateWith(KEY_HOME_SSIDS, emptySet(), logValue = false) { it + ssid }.map { }

    /**
     * 집 SSID 삭제
     *
     * 읽고 쓰기를 한 번에 처리.
     */
    suspend fun removeHomeSsid(ssid: String): Result<Unit> =
        updateWith(KEY_HOME_SSIDS, emptySet(), logValue = false) { it - ssid }.map { }

    suspend fun getUsageCursor(): Long = get(KEY_USAGE_CURSOR, 0L)

    suspend fun updateUsageCursor(time: Long): Result<Unit> = update(KEY_USAGE_CURSOR, time)

    /**
     * 열린 구간과 커서를 DataStore `edit` 한 번에 저장
     *
     * 하나만 저장된 채 끊기면 다음 수집이 이미 반영한 이벤트를 열린 구간에 다시 이어 붙이므로 함께 저장.
     * [session]이 null이면 열린 구간 저장값을 삭제.
     */
    suspend fun updateUsageProgress(session: OpenUsageSession?, cursor: Long): Result<Unit> {
        val json = session?.let {
            JsonUtil.to(it, true) ?: return Result.failure(IllegalStateException("열린 구간 직렬화 실패"))
        }
        return updateAll(
            PreferenceUpdate(KEY_OPEN_SESSION, json),
            PreferenceUpdate(KEY_USAGE_CURSOR, cursor),
            logValue = false,
        )
    }

    /**
     * 열린 구간
     *
     * 저장값이 없거나 복원할 수 없으면 null.
     */
    suspend fun getOpenSession(): OpenUsageSession? {
        val json = get(KEY_OPEN_SESSION, "", logValue = false)
        if (json.isEmpty()) return null
        return JsonUtil.from<OpenUsageSession>(json, true, logInput = false)
    }

    /**
     * 열린 구간 저장
     *
     * null이면 저장값을 삭제.
     */
    suspend fun updateOpenSession(session: OpenUsageSession?): Result<Unit> {
        val json = session?.let { JsonUtil.to(it, true) } ?: return remove(KEY_OPEN_SESSION)
        return update(KEY_OPEN_SESSION, json, logValue = false)
    }

    /**
     * 열린 구간 JSON 문자열을 그대로 저장
     *
     * 손상된 저장값 상황을 만드는 테스트에만 사용.
     */
    @VisibleForTesting
    suspend fun updateRawOpenSession(json: String): Result<Unit> = update(KEY_OPEN_SESSION, json, logValue = false)

    /**
     * 마지막 Wi-Fi 상태
     *
     * 저장값이 없거나 복원할 수 없으면 `WifiState(false, null)`.
     */
    suspend fun getLastWifiState(): WifiState {
        val json = get(KEY_LAST_WIFI_STATE, "", logValue = false)
        if (json.isEmpty()) return WifiState(false, null)
        return JsonUtil.from<WifiState>(json, true, logInput = false) ?: WifiState(false, null)
    }

    /** 마지막 Wi-Fi 상태를 SSID 포함 한 키에 한 번에 저장 */
    suspend fun updateLastWifiState(state: WifiState): Result<Unit> {
        val json = JsonUtil.to(state, true)
            ?: return Result.failure(IllegalStateException("Wi-Fi 상태 직렬화 실패"))
        return update(KEY_LAST_WIFI_STATE, json, logValue = false)
    }

    /**
     * 설치 식별자
     *
     * 없으면 UUID를 만들어 저장하며, 확인과 저장을 한 번에 처리해 동시 호출에도 하나만 만들어짐.
     * 저장이 실패하면 식별자가 여러 개 생기지 않도록 실패 결과를 돌려줌.
     */
    suspend fun getInstallId(): Result<String> {
        // 없을 때만 새 UUID 저장
        return updateWith(KEY_INSTALL_ID, "", logValue = false) { saved -> saved.ifEmpty { UUID.randomUUID().toString() } }
    }

    fun getWebhookUrlFlow(): Flow<String> = flow(KEY_WEBHOOK_URL).map { it ?: "" }

    suspend fun getWebhookUrl(): String = get(KEY_WEBHOOK_URL, "", logValue = false)

    suspend fun updateWebhookUrl(url: String): Result<Unit> = update(KEY_WEBHOOK_URL, url, logValue = false)

    fun getEncryptionKeyFlow(): Flow<String> = flow(KEY_ENCRYPTION_KEY).map { it ?: "" }

    suspend fun getEncryptionKey(): String = get(KEY_ENCRYPTION_KEY, "", logValue = false)

    suspend fun updateEncryptionKey(key: String): Result<Unit> = update(KEY_ENCRYPTION_KEY, key, logValue = false)

    /**
     * 암호화 키 보장
     *
     * 없으면 새 키를 만들어 저장하며, 확인과 저장을 한 번에 처리해 동시 호출에도 하나만 만들어짐.
     * 저장이 실패하면 키가 여러 개 생기지 않도록 실패 결과를 돌려줌.
     */
    suspend fun ensureEncryptionKey(): Result<String> {
        // 없을 때만 새 키 저장
        return updateWith(KEY_ENCRYPTION_KEY, "", logValue = false) { saved ->
            saved.ifEmpty { UsageLogCipher.encodeKey(UsageLogCipher.generateKey()) }
        }
    }

    suspend fun getSendSequence(): Long = get(KEY_SEND_SEQUENCE, 0L)

    suspend fun updateSendSequence(sequence: Long): Result<Unit> = update(KEY_SEND_SEQUENCE, sequence)

    /**
     * 마지막 전송 결과 흐름
     *
     * 저장값이 없거나 복원할 수 없으면 null.
     */
    fun getLastSendResultFlow(): Flow<LastSendResult?> = flow(KEY_LAST_SEND_RESULT).map { json ->
        if (json.isNullOrEmpty()) null else JsonUtil.from<LastSendResult>(json, true, JsonUtil.kotlinSerializationCompact, logInput = false)
    }

    suspend fun updateLastSendResult(result: LastSendResult): Result<Unit> {
        val json = JsonUtil.to(result, true, JsonUtil.kotlinSerializationCompact)
            ?: return Result.failure(IllegalStateException("전송 결과 직렬화 실패"))
        return update(KEY_LAST_SEND_RESULT, json)
    }

    /**
     * 저장값 전체 삭제
     *
     * 테스트 간 격리에만 사용.
     */
    @VisibleForTesting
    suspend fun clear(): Result<Unit> {
        return try {
            getDataStore(context).edit { it.clear() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
