package com.black.app.usagelog.upload

import android.content.Context
import android.os.Build
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.usagelog.collect.UsageStatsCollector
import com.black.core.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TimeZone
import javax.inject.Inject

/** 전송 한 번의 결과 */
sealed interface UploadResult {
    /** 웹후크 주소나 암호화 키가 없거나 유효하지 않아 보내지 않음 */
    data object NotConfigured : UploadResult

    /** 미전송 기록이 없음 */
    data object NothingToSend : UploadResult

    /** [recordCount]건을 보냄 */
    data class Sent(val recordCount: Int) : UploadResult

    /** 전송 실패이며 [retryable]이면 나중에 다시 시도할 가치가 있음 */
    data class Failed(val retryable: Boolean) : UploadResult
}

/**
 * 묶음 메타 줄에 들어가는 기기 정보
 *
 * [tzOffsetMinutes]는 시각을 받아 그 시점의 UTC 오프셋(분)을 돌려줌.
 */
data class DeviceInfo(val device: String, val appPackage: String, val tzOffsetMinutes: (Long) -> Int)

/**
 * 미전송 기록을 암호화한 묶음으로 만들어 웹후크로 보내고 전송됨으로 표시
 *
 * 웹후크 주소·암호화 키·기록 내용·응답 본문은 로그에 남기지 않고 건수와 상태 코드만 남김.
 */
class UsageLogUploader(
    private val repository: UsageLogRepository,
    private val settings: UsageLogSettingsRepository,
    private val collector: UsageStatsCollector,
    private val sender: WebhookSender,
    private val deviceInfo: DeviceInfo,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        repository: UsageLogRepository,
        settings: UsageLogSettingsRepository,
        collector: UsageStatsCollector,
        sender: DiscordWebhookClient,
    ) : this(
        repository,
        settings,
        collector,
        sender,
        DeviceInfo(Build.MODEL, context.packageName) { time -> TimeZone.getDefault().getOffset(time) / 60_000 },
    )

    /**
     * 미전송 기록을 모두 보냄
     *
     * 수집이 켜져 있으면 먼저 수집하며, 수집이 실패해도 이미 저장된 기록은 보냄.
     * 한 번에 [MAX_RECORDS_PER_ROUND]건씩 읽어 미전송 기록이 없어질 때까지 반복하고,
     * 묶음마다 전송 성공 직후 전송됨 표시와 순번 저장을 함.
     * 일부 묶음을 보낸 뒤 실패해도 [UploadResult.Failed]이며, 이미 보낸 기록은 다시 보내지 않음.
     */
    suspend fun upload(reason: SendReason): UploadResult = mutex.withLock { uploadLocked(reason) }

    private suspend fun uploadLocked(reason: SendReason): UploadResult {
        // 수집이 켜져 있으면 수집하되 실패해도 저장된 기록 전송은 계속
        if (settings.isCollectionEnabled()) {
            try {
                collector.collect()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("[UsageLogUploader] 수집 실패, 저장된 기록만 전송: ${failure::class.java.simpleName}")
            }
        }
        repository.purgeExpired(clock())

        // 전송 설정 확인
        val webhookUrl = settings.getWebhookUrl().trim()
        val key = UsageLogCipher.decodeKey(settings.getEncryptionKey())
        if (!DiscordWebhookFormat.isValidWebhookUrl(webhookUrl) || key == null) return UploadResult.NotConfigured

        // 미전송 기록이 없어질 때까지 한 번에 읽어 묶음으로 전송
        var sentCount = 0
        while (true) {
            val records = repository.getUnsent(MAX_RECORDS_PER_ROUND)
            if (records.isEmpty()) break
            val installId = settings.getInstallId().getOrElse { return failed(true) }
            val sentAt = clock()
            val batchContext = BatchContext(
                sentAt = sentAt,
                reason = reason,
                device = deviceInfo.device,
                appPackage = deviceInfo.appPackage,
                tzOffsetMinutes = deviceInfo.tzOffsetMinutes(sentAt),
                installId = installId,
            )
            val batches = UsageLogBatchEncoder.encode(records, settings.getSendSequence() + 1, batchContext, key)
            for (batch in batches) {
                val result = sendWithRateLimitRetry(webhookUrl, batch)
                if (result != WebhookResult.Success) {
                    Log.w("[UsageLogUploader] 전송 실패: ${describe(result)}")
                    return failed(isRetryable(result))
                }
                if (!saveProgress(batch)) return failed(true)
                sentCount += batch.recordIds.size
            }
        }

        // 보낸 기록이 있을 때만 결과 저장
        if (sentCount == 0) return UploadResult.NothingToSend
        settings.setLastSendResult(LastSendResult(clock(), true))
        return UploadResult.Sent(sentCount)
    }

    /** 429이면 안내된 시간만큼 기다려 같은 묶음을 최대 [MAX_RATE_LIMIT_ATTEMPTS]번 다시 보냄 */
    private suspend fun sendWithRateLimitRetry(webhookUrl: String, batch: EncodedBatch): WebhookResult {
        var result = sender.send(webhookUrl, batch.header, batch.fileName, batch.attachment)
        var retryCount = 0
        while (result is WebhookResult.RateLimited && retryCount < MAX_RATE_LIMIT_ATTEMPTS) {
            delay(result.retryAfterMillis)
            retryCount++
            result = sender.send(webhookUrl, batch.header, batch.fileName, batch.attachment)
        }
        return result
    }

    /**
     * 전송된 묶음의 기록을 전송됨으로 표시하고 순번을 저장
     *
     * 표시나 저장이 실패하면 false이며, 전송됨 표시가 먼저라 순번 저장만 실패해도 기록은 다시 보내지 않음.
     */
    private suspend fun saveProgress(batch: EncodedBatch): Boolean {
        try {
            repository.markSent(batch.recordIds)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("[UsageLogUploader] 전송됨 표시 실패: ${failure::class.java.simpleName}")
            return false
        }
        return settings.setSendSequence(batch.sequence).isSuccess
    }

    /** 실패 결과를 저장하고 [UploadResult.Failed]를 돌려줌 */
    private suspend fun failed(retryable: Boolean): UploadResult {
        settings.setLastSendResult(LastSendResult(clock(), false))
        return UploadResult.Failed(retryable)
    }

    /** 응답을 받지 못했거나 서버 오류이거나 요청 제한이 반복되면 다시 시도할 가치가 있음 */
    private fun isRetryable(result: WebhookResult): Boolean = when (result) {
        is WebhookResult.Failure -> result.statusCode == null || result.statusCode >= 500
        is WebhookResult.RateLimited -> true
        WebhookResult.Success -> false
    }

    /** 응답 본문이 섞이지 않도록 상태 코드만 담은 로그 문자열 */
    private fun describe(result: WebhookResult): String = when (result) {
        is WebhookResult.Failure -> "status=${result.statusCode}"
        is WebhookResult.RateLimited -> "status=429"
        WebhookResult.Success -> "success"
    }

    companion object {
        const val MAX_RECORDS_PER_ROUND = 5000
        const val MAX_RATE_LIMIT_ATTEMPTS = 3
        private val mutex = Mutex()
    }
}
