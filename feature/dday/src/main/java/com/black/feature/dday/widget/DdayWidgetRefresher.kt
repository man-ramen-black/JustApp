package com.black.feature.dday.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.black.core.alarm.WindowAlarmScheduler
import com.black.feature.dday.worker.DdayCalendarChangeWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * D-Day 위젯 갱신과 갱신 예약
 *
 * 남은 날짜는 자정에 바뀌므로 다음 자정 알람을 걸고, 일정이 바뀐 때는 캘린더 콘텐츠 변경 작업으로 갱신함.
 * 알람은 정확한 알람 권한 없이 [WindowAlarmScheduler]의 창 알람을 쓰며 분 단위 폴링은 하지 않음.
 * 알람 예약·취소는 짧은 Binder 호출 한 번이라 수신기 콜백(메인 스레드)에서 바로 불러도 되며, 갱신([refresh])만 suspend라 코루틴에서 부름.
 */
class DdayWidgetRefresher @Inject constructor(@ApplicationContext context: Context) {

    private val context = context.applicationContext

    /** 놓인 위젯이 하나라도 있는지 */
    suspend fun hasWidgets(): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(DdayWidget::class.java).isNotEmpty()

    /**
     * 놓인 위젯 전부 갱신
     *
     * Glance 세션이 살아 있으면 `update()`는 `provideGlance`를 다시 부르지 않고 상태만 바꿔 다시 구성하므로,
     * 먼저 위젯 상태의 [DDAY_REFRESH_TOKEN_KEY]를 새 값으로 써서 [DdayWidgetRoot]가 일정과 스타일을 다시 읽게 함.
     */
    suspend fun refresh() {
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(DdayWidget::class.java)
        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { preferences ->
                preferences[DDAY_REFRESH_TOKEN_KEY] = UUID.randomUUID().toString()
            }
            DdayWidget().update(context, glanceId)
        }
    }

    /** [now] 기준 다음 날 0시에 [MIDNIGHT_WINDOW_MILLIS] 창으로 갱신 알람 예약 */
    fun scheduleNextMidnight(now: ZonedDateTime = ZonedDateTime.now()) {
        val triggerAtMillis = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
        WindowAlarmScheduler(context).schedule(triggerAtMillis, MIDNIGHT_WINDOW_MILLIS, midnightPendingIntent())
    }

    /**
     * 캘린더 콘텐츠 변경 감시 작업 예약
     *
     * 변경이 잦은 동기화 중에 매번 갱신하지 않도록 변경 뒤 5초(최대 1분) 모아서 실행됨.
     * 수신기에서 걸 때는 이미 대기 중인 감시를 유지하는 [ExistingWorkPolicy.KEEP]을 쓰고, 작업이 끝나며 다음 감시를 걸 때는
     * [ExistingWorkPolicy.APPEND_OR_REPLACE]를 씀. 실행 중인 자신을 `REPLACE`로 취소하지 않고 뒤에 이어 붙이기 위함.
     */
    fun watchCalendarChanges(policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
            .setTriggerContentUpdateDelay(CHANGE_UPDATE_DELAY_SECONDS, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(CHANGE_MAX_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        val request = OneTimeWorkRequestBuilder<DdayCalendarChangeWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(CALENDAR_CHANGE_WORK_NAME, policy, request)
    }

    /** 자정 알람과 캘린더 변경 감시 작업 취소 */
    fun cancelAll() {
        WindowAlarmScheduler.cancel(context, midnightPendingIntent())
        WorkManager.getInstance(context).cancelUniqueWork(CALENDAR_CHANGE_WORK_NAME)
    }

    private fun midnightPendingIntent(): PendingIntent {
        val intent = Intent(context, DdayWidgetReceiver::class.java).setAction(DdayWidgetReceiver.ACTION_MIDNIGHT_REFRESH)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CALENDAR_CHANGE_WORK_NAME = "dday_calendar_change"

        /** 자정 알람 창, 시스템이 이 안에서 배터리 효율을 위해 시각을 조정함 */
        const val MIDNIGHT_WINDOW_MILLIS = 600_000L
        private const val CHANGE_UPDATE_DELAY_SECONDS = 5L
        private const val CHANGE_MAX_DELAY_SECONDS = 60L
    }
}
