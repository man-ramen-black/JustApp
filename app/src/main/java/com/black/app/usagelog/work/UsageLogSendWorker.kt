package com.black.app.usagelog.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.usagelog.collect.UsageStatsCollector
import com.black.app.usagelog.upload.DiscordWebhookClient
import com.black.app.usagelog.upload.SendReason
import com.black.app.usagelog.upload.UploadResult
import com.black.app.usagelog.upload.UsageLogUploader
import com.black.app.util.InstalledAppResolver
import com.black.core.util.Log
import kotlinx.coroutines.CancellationException

/**
 * 미전송 기록을 웹후크로 보내는 작업
 *
 * [UsageLogCollectWorker]처럼 hilt-work를 쓰지 않고 저장소를 직접 만듦.
 * 전송 계기는 입력 [KEY_REASON]의 [SendReason.value]이며, 없거나 알 수 없으면 [SendReason.PERIODIC].
 */
class UsageLogSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reasonValue = inputData.getString(KEY_REASON)
        val reason = SendReason.entries.firstOrNull { it.value == reasonValue } ?: SendReason.PERIODIC
        val uploadResult = try {
            val repository = UsageLogRepository(applicationContext)
            val settings = UsageLogSettingsRepository(applicationContext)
            val collector = UsageStatsCollector(applicationContext, settings, repository, InstalledAppResolver(applicationContext))
            UsageLogUploader(applicationContext, repository, settings, collector, DiscordWebhookClient()).upload(reason)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("[UsageLogSendWorker] 전송 작업 실패: ${failure::class.java.simpleName}")
            UploadResult.Failed(true)
        }
        if (uploadResult is UploadResult.Failed) {
            Log.w("[UsageLogSendWorker] 전송 실패, 재시도 여부: ${uploadResult.retryable}, 실행 횟수: ${runAttemptCount + 1}")
        }
        return toWorkResult(uploadResult, reason, runAttemptCount)
    }

    companion object {
        const val KEY_REASON = "reason"

        /** 정기 전송이 재시도 대상 실패를 거듭할 때 첫 실행을 포함해 실행하는 최대 횟수 */
        const val MAX_PERIODIC_ATTEMPTS = 3

        /**
         * 전송 결과를 작업 결과로 변환
         *
         * 정기 전송은 [MAX_PERIODIC_ATTEMPTS]번째 실행까지 재시도 대상으로 실패하면 재시도하지 않고 실패로 끝냄.
         * 재시도를 기다리는 작업은 대기 상태로 남아 다음 정기 전송 예약을 [ExistingWorkPolicy.KEEP]으로 막고,
         * 지수 백오프가 최대 5시간까지 늘어나기 때문. 남은 기록은 다음 정기 전송이 보냄.
         * 즉시 전송은 새 예약이 [ExistingWorkPolicy.REPLACE]로 대체하므로 횟수 제한 없이 재시도함.
         * [runAttemptCount]는 WorkManager가 넘기는 값으로 첫 실행이 0.
         */
        internal fun toWorkResult(result: UploadResult, reason: SendReason, runAttemptCount: Int): ListenableWorker.Result =
            when (result) {
                UploadResult.NotConfigured, UploadResult.NothingToSend, is UploadResult.Sent -> ListenableWorker.Result.success()
                is UploadResult.Failed -> when {
                    !result.retryable -> ListenableWorker.Result.failure()
                    reason == SendReason.PERIODIC && runAttemptCount + 1 >= MAX_PERIODIC_ATTEMPTS -> ListenableWorker.Result.failure()
                    else -> ListenableWorker.Result.retry()
                }
            }
    }
}
