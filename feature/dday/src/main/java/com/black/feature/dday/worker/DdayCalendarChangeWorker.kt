package com.black.feature.dday.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.black.core.util.Log
import com.black.core.util.runSuspendCatching
import com.black.feature.dday.widget.DdayWidgetRefresher

/**
 * 캘린더 변경 뒤 D-Day 위젯을 갱신하고 다음 변경 감시를 거는 작업
 *
 * 콘텐츠 URI 트리거 작업은 한 번 실행되면 끝나므로 직접 다시 걸어야 함.
 * 갱신 도중 생긴 캘린더 변경을 놓치지 않도록 갱신보다 먼저 다음 감시를 검.
 * 다시 걸 때 `REPLACE`를 쓰면 실행 중인 자신을 취소하므로 [ExistingWorkPolicy.APPEND_OR_REPLACE]로 뒤에 이어 붙임.
 * `TodoSyncWorker`처럼 hilt-work를 쓰지 않고 기본 팩토리 생성자를 씀.
 */
class DdayCalendarChangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val refresher = DdayWidgetRefresher(applicationContext)
        refresher.watchCalendarChanges(ExistingWorkPolicy.APPEND_OR_REPLACE)
        runSuspendCatching { refresher.refresh() }.onFailure {
            Log.w("[DdayCalendarChangeWorker] 위젯 갱신 실패: ${it::class.java.simpleName}")
        }
        return Result.success()
    }
}
