package com.black.feature.usagelog.data.usecase

import android.content.Context
import android.os.Build
import com.black.core.crypto.AesGcmCipher
import com.black.core.network.NetworkResult
import com.black.core.network.RateLimitRetry
import com.black.core.network.discord.DiscordWebhook
import com.black.core.network.discord.DiscordWebhookService
import com.black.core.network.logSummary
import com.black.core.util.Log
import com.black.core.util.runSuspendCatching
import com.black.feature.usagelog.data.UsageLogRepository
import com.black.feature.usagelog.data.UsageLogSettingsRepository
import com.black.feature.usagelog.model.LastSendResult
import com.black.feature.usagelog.model.SendReason
import com.black.feature.usagelog.model.UploadResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TimeZone
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 묶음 메타 줄에 들어가는 기기 정보
 *
 * [tzOffsetMinutes]는 시각을 받아 그 시점의 UTC 오프셋(분)을 돌려줌.
 */
data class DeviceInfo(val device: String, val appPackage: String, val tzOffsetMinutes: (Long) -> Int)

/**
 * 미전송 기록을 암호화한 묶음으로 만들어 웹후크로 보내고 전송됨으로 표시
 *
 * 웹후크 주소·암호화 키·기록 내용·응답 본문은 로그에 남기지 않고 건수, 상태 코드, 예외 클래스 이름만 남김.
 */
class UsageLogUploader(
    private val repository: UsageLogRepository,
    private val settings: UsageLogSettingsRepository,
    private val collector: UsageStatsCollector,
    private val smsCollector: SmsCollector,
    private val webhookService: DiscordWebhookService,
    private val deviceInfo: DeviceInfo,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        repository: UsageLogRepository,
        settings: UsageLogSettingsRepository,
        collector: UsageStatsCollector,
        smsCollector: SmsCollector,
    ) : this(
        repository,
        settings,
        collector,
        smsCollector,
        DiscordWebhook.service,
        DeviceInfo(Build.MODEL, context.packageName) { time -> TimeZone.getDefault().getOffset(time) / 60_000 },
    )

    /**
     * 미전송 기록을 모두 보냄
     *
     * 수집이 켜져 있으면 먼저 앱 사용 수집과 문자 수집을 하고 보관 기간 정리를 하며, 무엇이 실패해도 이미 저장된 기록은 보냄.
     * 한 번에 [MAX_RECORDS_PER_ROUND]건씩 읽어 미전송 기록이 없어질 때까지 반복하고,
     * 묶음마다 전송 성공 직후 전송됨 표시와 순번 저장을 함.
     * 암호화 키는 라운드마다 다시 읽어 화면에서 새로 만든 키를 다음 라운드부터 씀.
     * 일부 묶음을 보낸 뒤 실패해도 [UploadResult.Failed]이며, 이미 보낸 기록은 다시 보내지 않음.
     */
    suspend fun upload(reason: SendReason): UploadResult = mutex.withLock { uploadLocked(reason) }

    private suspend fun uploadLocked(reason: SendReason): UploadResult {
        // 수집이 켜져 있으면 수집하되 실패해도 저장된 기록 전송은 계속
        if (settings.isCollectionEnabled()) {
            runSuspendCatching { collector.collect() }.onFailure {
                Log.w("[UsageLogUploader] 수집 실패, 저장된 기록만 전송: ${it::class.java.simpleName}")
            }
            runSuspendCatching { smsCollector.collect() }.onFailure {
                Log.w("[UsageLogUploader] 문자 수집 실패, 저장된 기록만 전송: ${it::class.java.simpleName}")
            }
        }
        // 보관 기간 정리가 실패해도 전송은 계속
        runSuspendCatching { repository.purgeExpired(clock()) }.onFailure {
            Log.w("[UsageLogUploader] 보관 기간 정리 실패, 전송 계속: ${it::class.java.simpleName}")
        }

        // 전송 설정 확인
        val webhookUrl = settings.getWebhookUrl().trim()
        if (!DiscordWebhook.isValidUrl(webhookUrl) || readKey() == null) {
            return UploadResult.NotConfigured
        }

        // 미전송 기록이 없어질 때까지 한 번에 읽어 묶음으로 전송
        var sentCount = 0
        while (true) {
            val records = repository.getUnsent(MAX_RECORDS_PER_ROUND, clock())
            if (records.isEmpty()) {
                break
            }
            val key = readKey() ?: return UploadResult.NotConfigured
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
                if (result !is NetworkResult.Success) {
                    Log.w("[UsageLogUploader] 전송 실패: ${result.logSummary()}")
                    return failed(isRetryable(result))
                }
                if (!saveProgress(batch)) {
                    return failed(true)
                }
                sentCount += batch.recordIds.size
            }
        }

        // 보낸 기록이 있을 때만 결과 저장
        if (sentCount == 0) {
            return UploadResult.NothingToSend
        }
        settings.setLastSendResult(LastSendResult(clock(), true))
        return UploadResult.Sent(sentCount)
    }

    /** 저장된 암호화 키이며 없거나 유효하지 않으면 null */
    private suspend fun readKey(): ByteArray? = AesGcmCipher.decodeKey(settings.getEncryptionKey())

    /**
     * 429이면 안내된 시간만큼 기다려 같은 묶음을 최대 [MAX_RATE_LIMIT_ATTEMPTS]번 다시 보냄
     *
     * 안내된 시간이 [MAX_RATE_LIMIT_WAIT]를 넘으면 기다리지 않고 429 결과를 그대로 돌려줌.
     * 작업 실행 시간 제한 안에서 Mutex를 오래 붙잡아 즉시 전송까지 막지 않게 하고 WorkManager 재시도에 맡기기 위함.
     * 안내된 시간은 `Retry-After` 헤더로만 읽고, 헤더가 없으면 [DEFAULT_RATE_LIMIT_WAIT]만큼 기다림.
     */
    private suspend fun sendWithRateLimitRetry(webhookUrl: String, batch: EncodedBatch): NetworkResult<Unit> =
        RateLimitRetry.send(
            maxRetries = MAX_RATE_LIMIT_ATTEMPTS,
            maxWait = MAX_RATE_LIMIT_WAIT,
            defaultWait = DEFAULT_RATE_LIMIT_WAIT,
        ) {
            DiscordWebhook.send(webhookService, webhookUrl, batch.header, batch.fileName, batch.attachment)
        }

    /**
     * 전송된 묶음의 기록을 전송됨으로 표시하고 순번을 저장
     *
     * 표시나 저장이 실패하면 false이며, 전송됨 표시가 먼저라 순번 저장만 실패해도 기록은 다시 보내지 않음.
     */
    private suspend fun saveProgress(batch: EncodedBatch): Boolean {
        runSuspendCatching { repository.markSent(batch.recordIds) }.getOrElse {
            Log.w("[UsageLogUploader] 전송됨 표시 실패: ${it::class.java.simpleName}")
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
    private fun isRetryable(result: NetworkResult<Unit>): Boolean = when (result) {
        is NetworkResult.ConnectionFailure -> true
        is NetworkResult.HttpFailure ->
            result.statusCode == RateLimitRetry.STATUS_TOO_MANY_REQUESTS || result.statusCode >= SERVER_ERROR_STATUS
        // 응답 본문을 Unit으로 받아 생기지 않는 실패
        is NetworkResult.ParseFailure -> false
        is NetworkResult.Success -> false
    }

    companion object {
        const val MAX_RECORDS_PER_ROUND = 5000
        const val MAX_RATE_LIMIT_ATTEMPTS = 3

        /** 429 뒤 기다릴 수 있는 최대 시간이며, 안내된 시간이 이보다 길면 기다리지 않고 실패 */
        val MAX_RATE_LIMIT_WAIT: Duration = 60.seconds

        /** `Retry-After` 헤더가 없는 429 뒤 기다릴 시간 */
        private val DEFAULT_RATE_LIMIT_WAIT: Duration = 5.seconds

        /** 이 값 이상이면 서버 오류로 보고 재시도 */
        private const val SERVER_ERROR_STATUS = 500

        private val mutex = Mutex()
    }
}
