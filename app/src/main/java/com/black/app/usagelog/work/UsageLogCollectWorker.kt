package com.black.app.usagelog.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.ui.common.selectapp.InstalledAppResolver
import com.black.app.usagelog.collect.UsageStatsCollector
import com.black.app.usagelog.upload.SendReason
import com.black.core.util.Log
import kotlinx.coroutines.CancellationException

/**
 * 1시간마다 사용 기록을 읽고 보관 기간이 지난 기록을 정리한 뒤 전송 작업을 예약하는 작업
 *
 * 수집이 계속 실패해도 저장된 알림·Wi-Fi 기록이 전송되도록 성공·재시도 모두에서 전송 작업을 예약함.
 *
 * hilt-work를 쓰지 않고 저장소를 직접 만듦(기존 `UsageTimerAccessibility`와 같은 방식).
 */
class UsageLogCollectWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = try {
            val repository = UsageLogRepository(applicationContext)
            UsageStatsCollector(
                applicationContext,
                UsageLogSettingsRepository(applicationContext),
                repository,
                InstalledAppResolver(applicationContext),
            ).collect()
            repository.purgeExpired(System.currentTimeMillis())
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("[UsageLogCollectWorker] 수집 실패, 재시도: ${e.javaClass.simpleName}")
            Result.retry()
        }
        // 수집 결과와 무관하게 저장된 기록 전송 예약
        try {
            UsageLogScheduler(applicationContext).enqueueSend(SendReason.PERIODIC)
        } catch (e: Exception) {
            Log.w("[UsageLogCollectWorker] 전송 예약 실패: ${e.javaClass.simpleName}")
        }
        return result
    }
}
