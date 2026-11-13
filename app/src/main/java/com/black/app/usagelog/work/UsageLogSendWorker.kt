package com.black.app.usagelog.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.ui.common.selectapp.InstalledAppResolver
import com.black.app.usagelog.collect.UsageStatsCollector
import com.black.app.usagelog.upload.DiscordWebhookClient
import com.black.app.usagelog.upload.SendReason
import com.black.app.usagelog.upload.UploadResult
import com.black.app.usagelog.upload.UsageLogUploader
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
        return try {
            val repository = UsageLogRepository(applicationContext)
            val settings = UsageLogSettingsRepository(applicationContext)
            val collector = UsageStatsCollector(applicationContext, settings, repository, InstalledAppResolver(applicationContext))
            when (val result = UsageLogUploader(applicationContext, repository, settings, collector, DiscordWebhookClient()).upload(reason)) {
                UploadResult.NotConfigured, UploadResult.NothingToSend, is UploadResult.Sent -> Result.success()
                is UploadResult.Failed -> {
                    Log.w("[UsageLogSendWorker] 전송 실패, 재시도 여부: ${result.retryable}")
                    if (result.retryable) Result.retry() else Result.failure()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("[UsageLogSendWorker] 전송 작업 실패, 재시도: ${failure::class.java.simpleName}")
            Result.retry()
        }
    }

    companion object {
        const val KEY_REASON = "reason"
    }
}
