package com.black.app.usagelog.work

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.black.app.usagelog.upload.SendReason
import com.black.app.usagelog.upload.UsageLogUploader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** 기기 사용 기록을 읽는 1시간 주기 작업의 예약과 취소, 전송 작업 예약 */
class UsageLogScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * 주기 수집 작업 예약
     *
     * 이미 예약돼 있으면 [ExistingPeriodicWorkPolicy.KEEP]으로 기존 주기를 유지.
     */
    fun schedulePeriodicCollect() {
        val request = PeriodicWorkRequestBuilder<UsageLogCollectWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_COLLECT_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** 주기 수집 작업 취소 */
    fun cancelPeriodicCollect() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_COLLECT_WORK_NAME)
    }

    /**
     * 미전송 기록 전송 작업 예약
     *
     * [SendReason.PERIODIC]은 [SEND_WORK_NAME] 작업이 이미 대기 중이면 그대로 두고([ExistingWorkPolicy.KEEP]),
     * 가속 실행을 요청하지 않아 즉시 전송이 쓸 가속 할당량을 아낌. 대기 중인 작업이 실행될 때 미전송 기록을 모두 보내므로 하나로 충분함.
     * 재시도 대기 중인 작업도 대기로 보므로, 재시도는 [UsageLogSendWorker.MAX_PERIODIC_ATTEMPTS]번 실행까지만 하고 실패로 끝내 다음 정기 전송 예약을 막지 않음.
     *
     * 그 밖의 계기(집 도착·출발, 수동)는 [SEND_NOW_WORK_NAME] 작업이며 [ExistingWorkPolicy.REPLACE]로 대기 중이던 즉시 전송을 대체함.
     * 정기 전송의 재시도 대기에 막히지 않도록 작업 이름을 따로 두고, 두 작업이 동시에 보내지 않는 것은 [UsageLogUploader]의 Mutex가 보장함.
     * 대체로 실행 중이던 즉시 전송이 취소될 수 있으며 이미 디스코드가 받은 묶음이면 같은 기록이 다시 가고 PC가 기록 id로 걸러냄.
     * 집 연결이 오락가락해도 대기 중인 즉시 전송은 하나로 합쳐짐.
     * 가속 실행은 API 31 이상에서만 요청하며, 그 미만에서는 `getForegroundInfo` 구현이 필요해 요청하지 않음.
     * 재시도 대기는 [WorkRequest.MIN_BACKOFF_MILLIS]부터 시작하지만, WorkManager는 첫 실행에만 가속 실행을 요청하고
     * 재시도는 일반 작업으로 예약하므로 대기 시간이 지나도 기기 상태(절전, 앱 대기 버킷)에 따라 더 늦게 실행될 수 있음.
     */
    fun enqueueSend(reason: SendReason) {
        val isPeriodic = reason == SendReason.PERIODIC
        val builder = OneTimeWorkRequestBuilder<UsageLogSendWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(UsageLogSendWorker.KEY_REASON to reason.value))
        if (isPeriodic) {
            builder.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
        } else {
            builder.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            if (isPeriodic) SEND_WORK_NAME else SEND_NOW_WORK_NAME,
            if (isPeriodic) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
            builder.build(),
        )
    }

    companion object {
        const val PERIODIC_COLLECT_WORK_NAME = "usage_log_collect"
        const val SEND_WORK_NAME = "usage_log_send"
        const val SEND_NOW_WORK_NAME = "usage_log_send_now"
    }
}
