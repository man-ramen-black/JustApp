package com.black.feature.todo.worker

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Todo 노션 동기화 15분 주기 작업의 예약과 취소, 바로 동기화 작업 예약 */
class TodoSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * 주기 동기화 작업 예약
     *
     * 이미 예약돼 있으면 [ExistingPeriodicWorkPolicy.KEEP]으로 기존 주기를 유지.
     * WorkManager 주기 작업의 최소 간격이 15분이라 이보다 짧게 둘 수 없음.
     * 옛 이름의 주기 작업은 [LEGACY_PERIODIC_WORK_NAME]에 적은 이유로 함께 취소함.
     */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<TodoSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .build()
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** 옛 이름의 작업까지 주기 동기화 작업 취소 */
    fun cancelPeriodic() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    /**
     * 완료 체크·추가 직후 바로 동기화하는 일회성 작업 예약
     *
     * [ExistingWorkPolicy.APPEND_OR_REPLACE]로 끝나지 않은 바로 동기화 뒤에 이어 붙여 진행 중인 동기화를 취소하지 않음.
     * 취소되면 노션에 만든 페이지를 저장본에 적지 못하고 다음 동기화가 같은 페이지를 다시 만들 수 있기 때문.
     * 앞 작업이 실패·취소로 끝났으면 이어 붙이지 않고 새로 시작함.
     * 가속 실행은 API 31 이상에서만 요청하며, 그 미만에서는 `getForegroundInfo` 구현이 필요해 요청하지 않음.
     * 옛 이름의 바로 동기화 작업은 [LEGACY_SYNC_NOW_WORK_NAME]에 적은 이유로 함께 취소함.
     */
    fun enqueueSyncNow() {
        val builder = OneTimeWorkRequestBuilder<TodoSyncWorker>()
            .setConstraints(networkConstraints())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_SYNC_NOW_WORK_NAME)
        workManager.enqueueUniqueWork(
            SYNC_NOW_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            builder.build(),
        )
    }

    private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    companion object {
        const val PERIODIC_WORK_NAME = "todo_sync_periodic"
        const val SYNC_NOW_WORK_NAME = "todo_sync_now"

        /**
         * 모듈 분리 전 주기 동기화 작업의 고유 이름
         *
         * 그 작업은 옛 Worker 클래스 경로로 저장돼 있어, 다음 실행 때 Worker를 만들지 못해 `FAILED`로 한 번 끝남(주기 작업이어도 다시 잡히지 않음).
         * 끝나기 전이면 새 이름의 작업과 함께 남아 실행을 시도하므로 새 작업을 예약하거나 취소할 때 옛 이름도 취소함.
         */
        const val LEGACY_PERIODIC_WORK_NAME = "assistant_task_sync_periodic"

        /**
         * 모듈 분리 전 바로 동기화 작업의 고유 이름
         *
         * [LEGACY_PERIODIC_WORK_NAME]과 같이 옛 Worker 클래스 경로로 저장돼 있어 실행되면 `FAILED`로 끝나므로 함께 취소함.
         */
        const val LEGACY_SYNC_NOW_WORK_NAME = "assistant_task_sync_now"
    }
}
