package com.black.feature.usagelog.worker

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.await
import androidx.work.workDataOf
import com.black.feature.usagelog.data.usecase.UsageLogUploader
import com.black.feature.usagelog.model.SendReason
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** 기기 사용 기록을 읽는 1시간 주기 작업의 예약과 취소, 전송 작업 예약 */
class UsageLogScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * 주기 수집 작업 예약
     *
     * 이미 예약돼 있으면(끝나지 않은 작업이 있으면) [ExistingPeriodicWorkPolicy.UPDATE]로 같은 id의 요청만 바꿔 기존 주기를 유지.
     * WorkManager DB에는 작업이 Worker 클래스 이름으로 저장돼, 클래스 경로가 바뀐 업데이트 직후의 옛 작업은 실행되면 작업자를 만들지 못해 FAILED로 끝남.
     * UPDATE는 그 옛 작업도 같은 id의 [UsageLogCollectWorker] 작업으로 바꿈.
     * UPDATE는 이미 끝난 작업(옛 클래스로 실행돼 FAILED가 된 작업 등)을 바꾸지 않으므로,
     * 끝나지 않은 작업이 없으면 [ExistingPeriodicWorkPolicy.KEEP]으로 끝난 작업을 지우고 새로 예약.
     *
     * 조회와 예약 사이에 옛 작업이 실행돼 끝나면 UPDATE는 적용되지 않음(`NOT_APPLIED`이지만 예약 [Operation]은 성공으로 끝남).
     * 그대로 두면 다음 예약 호출(화면 열기 등)까지 주기 수집이 멈추므로, 예약이 끝난 뒤 끝나지 않은 작업이 없으면 KEEP으로 한 번 더 예약함.
     * 옛 작업이 실행에 들어간 뒤의 UPDATE는 다음 실행부터 적용(`APPLIED_FOR_NEXT_RUN`)되지만, 옛 작업은 Worker를 만들지 못해 RUNNING을 거치지 않고 ENQUEUED에서 FAILED로 끝나 다음 실행이 없음.
     * 이때 옛 작업의 실행은 UPDATE가 끝나기 전에 WorkManager의 직렬 실행기에 올라가 있고, 다시 조회도 WorkManager가 DB 쿼리 실행기로 지정한 같은 직렬 실행기에서 돌아
     * 실패 처리가 다시 조회보다 먼저 끝남. 그래서 다시 조회에서 FAILED가 보여 KEEP으로 새로 예약됨.
     * WorkManager 2.10.1 기준 동작이라 버전을 올리면 다시 확인.
     *
     * 예약이 실패하면 [Operation.await]가 그 원인을 예외로 던짐.
     * 수집 켜기는 켜짐을 저장한 뒤 이 함수를 부르므로, 이때 켜기가 실패를 돌려줘도 켜짐은 저장된 상태로 남고 다음 예약 호출에서 예약을 복구함.
     */
    suspend fun schedulePeriodicCollect() {
        val workManager = WorkManager.getInstance(context)
        // 끝나지 않은 작업이 있으면 같은 id로 갱신, 없으면 새로 예약
        val policy = if (hasUnfinishedPeriodicCollect(workManager)) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
        enqueuePeriodicCollect(workManager, policy)
        // 조회 뒤 옛 작업이 끝나 UPDATE가 적용되지 않았으면 새로 예약
        if (!hasUnfinishedPeriodicCollect(workManager)) {
            enqueuePeriodicCollect(workManager, ExistingPeriodicWorkPolicy.KEEP)
        }
    }

    private suspend fun hasUnfinishedPeriodicCollect(workManager: WorkManager): Boolean =
        workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_COLLECT_WORK_NAME).first().any { !it.state.isFinished }

    /** [policy]로 주기 수집 작업을 예약하고 WorkManager DB 반영까지 대기 */
    private suspend fun enqueuePeriodicCollect(workManager: WorkManager, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<UsageLogCollectWorker>(1, TimeUnit.HOURS).build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_COLLECT_WORK_NAME, policy, request).await()
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
