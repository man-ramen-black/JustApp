package com.black.app.assistanttask.arrival

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.black.app.assistanttask.AssistantTask
import com.black.app.assistanttask.AssistantTaskDates
import com.black.app.assistanttask.AssistantTaskNotifier
import com.black.app.assistanttask.sync.AssistantTaskSyncResult
import com.black.app.assistanttask.sync.AssistantTaskSyncStatus
import com.black.app.assistanttask.sync.AssistantTaskSynchronizer
import com.black.app.model.AssistantTaskRepository
import com.black.core.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.TimeZone

/**
 * 집 도착 직후 동기화하고 해당 Todo를 알리는 일회성 작업
 *
 * 신호는 `NotificationListenerService` 안의 Wi-Fi 콜백에서 오는데, 서비스 scope는 `onDestroy`에서 취소되어 30초 동기화를 끝까지 기다린다는 보장이 없으므로 작업으로 돌림.
 * WorkManager 작업은 프로세스가 죽어도 남아 다시 실행되고, API 31 이상에서는 가속 실행을 요청함.
 * 동기화가 [SYNC_TIMEOUT_MILLIS] 안에 끝나지 않거나 실패해도 저장본으로 알림을 띄우며, 네트워크 조건은 걸지 않음.
 * 노션 조회 단계가 네트워크 오류로 끝나면 제한 시간 안에서 [NETWORK_RETRY_DELAY_MILLIS] 간격으로 다시 동기화함.
 * 노션 페이지 추가 요청은 취소되지 않아 진행 중이면 그 응답까지 더 기다릴 수 있음.
 * 작업 결과는 항상 성공이라 WorkManager가 다시 실행하지 않음.
 * `AssistantTaskSyncWorker`처럼 Hilt 없이 의존성을 직접 만들며, 시험은 내부 생성자로 바꿔 넣음.
 */
class AssistantTaskHomeArrivalWorker internal constructor(
    context: Context,
    params: WorkerParameters,
    private val sync: suspend () -> AssistantTaskSyncResult,
    private val loadTasks: suspend () -> List<AssistantTask>,
    private val notify: (List<AssistantTask>) -> Boolean,
    private val now: () -> Long,
    private val zone: () -> TimeZone,
) : CoroutineWorker(context, params) {

    /** WorkManager 기본 작업 팩토리가 찾는 생성자 */
    constructor(context: Context, params: WorkerParameters) : this(
        context,
        params,
        { AssistantTaskSynchronizer(context).syncWithStage() },
        { AssistantTaskRepository(context.applicationContext).getAll() },
        { tasks -> AssistantTaskNotifier(context.applicationContext).showHomeArrival(tasks) },
        System::currentTimeMillis,
        TimeZone::getDefault,
    )

    override suspend fun doWork(): Result {
        // 동기화는 제한 시간 안에서만 기다리고 실패해도 저장본으로 진행
        try {
            withTimeoutOrNull(SYNC_TIMEOUT_MILLIS) { syncUntilReachable() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("[AssistantTaskHomeArrivalWorker] 동기화 실패: ${failure::class.java.simpleName}")
        }
        // 저장본에서 대상을 골라 알림
        try {
            val currentZone = zone()
            val today = AssistantTaskDates.formatDate(now(), currentZone)
            notify(AssistantTaskHomeArrival.targets(loadTasks(), today, currentZone))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("[AssistantTaskHomeArrivalWorker] 알림 실패: ${failure::class.java.simpleName}")
        }
        return Result.success()
    }

    /**
     * 노션 조회 단계의 네트워크 오류가 아닐 때까지 [NETWORK_RETRY_DELAY_MILLIS] 간격으로 동기화
     *
     * Wi-Fi가 막 붙은 순간에는 첫 요청이 인터넷 연결 전에 네트워크 오류로 바로 끝날 수 있어, 남은 제한 시간을 다시 시도에 씀.
     * 보내기 단계의 네트워크 오류는 다시 시도하지 않음.
     * 이미 받은 목록으로 저장본이 바뀌었고, 응답만 끊긴 추가 요청을 다시 보내면 같은 Todo가 여러 개 생길 수 있음.
     */
    private suspend fun syncUntilReachable() {
        while (true) {
            val result = sync()
            if (result.status != AssistantTaskSyncStatus.NETWORK_ERROR || !result.failedOnFetch) return
            delay(NETWORK_RETRY_DELAY_MILLIS)
        }
    }

    companion object {
        const val WORK_NAME = "assistant_task_home_arrival"

        /** 도착 직후 동기화를 기다리는 최대 시간 */
        const val SYNC_TIMEOUT_MILLIS = 30_000L

        /** 노션 조회 단계가 네트워크 오류로 끝났을 때 다시 동기화하기까지 기다리는 시간 */
        const val NETWORK_RETRY_DELAY_MILLIS = 5_000L

        /**
         * 도착 작업 예약
         *
         * 대기 중인 작업이 있으면 [ExistingWorkPolicy.KEEP]으로 그대로 두어 도착 신호가 겹쳐도 알림이 한 번만 뜸.
         * 가속 실행은 API 31 이상에서만 요청하며, 그 미만에서는 `getForegroundInfo` 구현이 필요해 요청하지 않음.
         */
        fun enqueue(context: Context) {
            val builder = OneTimeWorkRequestBuilder<AssistantTaskHomeArrivalWorker>()
                .setConstraints(Constraints.NONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, builder.build())
        }
    }
}
