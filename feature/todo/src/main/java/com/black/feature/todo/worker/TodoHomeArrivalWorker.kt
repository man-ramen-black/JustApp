package com.black.feature.todo.worker

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.black.core.util.Log
import com.black.core.util.runSuspendCatching
import com.black.feature.todo.data.TodoRepository
import com.black.feature.todo.data.usecase.SyncTodoUseCase
import com.black.feature.todo.data.usecase.TodoHomeArrivalUseCase
import com.black.feature.todo.data.usecase.TodoSyncResult
import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoSyncStatus
import com.black.feature.todo.notification.TodoNotifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.milliseconds

/**
 * 집 도착 직후 동기화하고 해당 Todo를 알리는 일회성 작업
 *
 * 신호는 `NotificationListenerService` 안의 Wi-Fi 콜백에서 오는데, 서비스 scope는 `onDestroy`에서 취소되어 30초 동기화를 끝까지 기다린다는 보장이 없으므로 작업으로 돌림.
 * WorkManager 작업은 프로세스가 죽어도 남아 다시 실행되고, API 31 이상에서는 가속 실행을 요청함.
 * 동기화가 [SYNC_TIMEOUT_MILLIS] 안에 끝나지 않거나 실패해도 저장본으로 알림을 띄우며, 네트워크 조건은 걸지 않음.
 * 노션 조회 단계가 네트워크 오류로 끝나면 제한 시간 안에서 [NETWORK_RETRY_DELAY_MILLIS] 간격으로 다시 동기화함.
 * 노션 페이지 추가 요청은 취소되지 않아 진행 중이면 그 응답까지 더 기다릴 수 있음.
 * 작업 결과는 항상 성공이라 WorkManager가 다시 실행하지 않음.
 * `TodoSyncWorker`처럼 Hilt 없이 의존성을 직접 만들며, 시험은 내부 생성자로 바꿔 넣음.
 */
class TodoHomeArrivalWorker internal constructor(
    context: Context,
    params: WorkerParameters,
    private val sync: suspend () -> TodoSyncResult,
    private val loadTasks: suspend () -> List<Todo>,
    private val notify: (List<Todo>) -> Boolean,
    private val now: () -> Instant,
    private val zone: () -> ZoneId,
) : CoroutineWorker(context, params) {

    /** WorkManager 기본 작업 팩토리가 찾는 생성자 */
    constructor(context: Context, params: WorkerParameters) : this(
        context,
        params,
        { SyncTodoUseCase(context).syncWithStage() },
        { TodoRepository(context.applicationContext).getAll() },
        { tasks -> TodoNotifier(context.applicationContext).showHomeArrival(tasks) },
        Instant::now,
        ZoneId::systemDefault,
    )

    override suspend fun doWork(): Result {
        // 동기화는 제한 시간 안에서만 기다리고 실패해도 저장본으로 진행
        runSuspendCatching { withTimeoutOrNull(SYNC_TIMEOUT_MILLIS.milliseconds) { syncUntilReachable() } }.onFailure {
            Log.w("[TodoHomeArrivalWorker] 동기화 실패: ${it::class.java.simpleName}")
        }
        // 저장본에서 대상을 골라 알림
        runSuspendCatching {
            val currentZone = zone()
            val today = now().atZone(currentZone).toLocalDate()
            notify(TodoHomeArrivalUseCase.targets(loadTasks(), today, currentZone))
        }.onFailure {
            Log.w("[TodoHomeArrivalWorker] 알림 실패: ${it::class.java.simpleName}")
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
            if (result.status != TodoSyncStatus.NETWORK_ERROR || !result.failedOnFetch) {
                return
            }
            delay(NETWORK_RETRY_DELAY_MILLIS.milliseconds)
        }
    }

    companion object {
        const val WORK_NAME = "todo_home_arrival"

        /**
         * 모듈 분리 전 도착 작업의 고유 이름
         *
         * 그 작업은 옛 Worker 클래스 경로로 저장돼 실행되지 못하므로 새 작업을 예약할 때 함께 취소함.
         */
        const val LEGACY_WORK_NAME = "assistant_task_home_arrival"

        /** 도착 직후 동기화를 기다리는 최대 시간 */
        const val SYNC_TIMEOUT_MILLIS = 30_000L

        /** 노션 조회 단계가 네트워크 오류로 끝났을 때 다시 동기화하기까지 기다리는 시간 */
        const val NETWORK_RETRY_DELAY_MILLIS = 5_000L

        /**
         * 도착 작업 예약
         *
         * 대기 중인 작업이 있으면 [ExistingWorkPolicy.KEEP]으로 그대로 두어 도착 신호가 겹쳐도 알림이 한 번만 뜸.
         * 가속 실행은 API 31 이상에서만 요청하며, 그 미만에서는 `getForegroundInfo` 구현이 필요해 요청하지 않음.
         * 옛 이름의 작업은 [LEGACY_WORK_NAME]에 적은 이유로 함께 취소함.
         */
        fun enqueue(context: Context) {
            val builder = OneTimeWorkRequestBuilder<TodoHomeArrivalWorker>()
                .setConstraints(Constraints.NONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(LEGACY_WORK_NAME)
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, builder.build())
        }
    }
}
