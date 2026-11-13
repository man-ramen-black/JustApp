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
 * 1시간마다 보관 기간이 지난 기록을 정리하고 사용 기록을 읽은 뒤 전송 작업을 예약하는 작업
 *
 * 정리·수집이 실패해도 저장된 알림·Wi-Fi 기록이 전송되도록 항상 전송 작업을 예약하고 성공으로 끝냄.
 *
 * hilt-work를 쓰지 않고 저장소를 직접 만듦(기존 `UsageTimerAccessibility`와 같은 방식).
 */
class UsageLogCollectWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = UsageLogRepository(applicationContext)
        val collector = UsageStatsCollector(
            applicationContext,
            UsageLogSettingsRepository(applicationContext),
            repository,
            InstalledAppResolver(applicationContext),
        )
        purgeThenCollect(repository, collector, System.currentTimeMillis())
        // 정리·수집 결과와 무관하게 저장된 기록 전송 예약
        try {
            UsageLogScheduler(applicationContext).enqueueSend(SendReason.PERIODIC)
        } catch (failure: Exception) {
            Log.w("[UsageLogCollectWorker] 전송 예약 실패: ${failure::class.java.simpleName}")
        }
        return Result.success()
    }

    companion object {
        /**
         * 보관 기간 정리 뒤 수집
         *
         * 서로 막지 않도록 각자 실패를 잡아 클래스 이름만 로그로 남김. 정리를 먼저 해 수집이 계속 실패해도 오래된 기록은 지워짐.
         * 수집이 실패하면 커서가 그대로라 다음 주기에 같은 구간을 다시 읽으므로 작업을 재시도하지 않음.
         */
        internal suspend fun purgeThenCollect(repository: UsageLogRepository, collector: UsageStatsCollector, now: Long) {
            try {
                repository.purgeExpired(now)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("[UsageLogCollectWorker] 보관 기간 정리 실패: ${failure::class.java.simpleName}")
            }
            try {
                collector.collect()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("[UsageLogCollectWorker] 수집 실패, 다음 주기에 다시 읽음: ${failure::class.java.simpleName}")
            }
        }
    }
}
