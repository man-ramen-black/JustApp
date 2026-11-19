package com.black.feature.todo.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.black.core.alarm.ExactAlarmScheduler
import com.black.core.util.Log
import com.black.core.util.runSuspendCatching
import com.black.feature.todo.data.TodoRepository
import com.black.feature.todo.data.TodoSettingsRepository
import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoDue
import com.black.feature.todo.model.TodoReminder
import com.black.feature.todo.receiver.TodoReminderReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/** Todo 시각 알림 다시 예약 창구 */
fun interface TodoReminderRescheduler {
    suspend fun reschedule()
}

/**
 * 저장본 기준 Todo 시각 알림 알람 예약기
 *
 * 알람마다 page ID를 담은 인텐트를 쓰고, 걸려 있을 수 있는 ID 집합을 저장해 다음 호출에서 빠진 알람을 취소함.
 * [dispatcher]는 화면이 메인 스레드에서 불러도 `PendingIntent`·[AlarmManager] 호출이 메인 스레드를 막지 않도록 다시 예약 본체를 돌리는 디스패처이며,
 * 테스트는 테스트 스케줄러에서 실행 순서를 제어하도록 테스트 디스패처를 넘김.
 * 그 호출들은 시스템 서버와의 Binder 왕복이 끝날 때까지 스레드를 막으므로 블로킹 호출용 [Dispatchers.IO]를 씀.
 */
class TodoReminderScheduler(
    private val context: Context,
    private val repository: TodoRepository,
    private val settings: TodoSettingsRepository,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Instant,
) : TodoReminderRescheduler {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context,
        TodoRepository(context),
        TodoSettingsRepository(context),
        Dispatchers.IO,
        Instant::now,
    )

    /**
     * 저장본으로 알람 다시 예약
     *
     * 지난 ID 중 [keptAlarmIds]에 없는 것은 취소하고 대상은 예약한 뒤 걸려 있을 수 있는 ID를 저장.
     * 기한이 막 지난 시각 알림 항목은 늦게 울릴 알람일 수 있어 취소하지 않고 저장 ID에도 남김.
     * 동기화 뒤·Todo 변경 뒤·수신기에서 거의 동시에 불릴 수 있어 프로세스 전역 [Mutex]로 한 번에 하나씩 돌림.
     * 저장소 예외는 클래스 이름만 로그에 남기고 던지지 않음.
     */
    override suspend fun reschedule() {
        // 잠금을 디스패처 안에서 잡아, 본체가 끝나면 호출자 디스패처로 돌아가기를 기다리지 않고 바로 풂
        withContext(dispatcher) {
            mutex.withLock {
                runSuspendCatching { rescheduleLocked() }.onFailure {
                    Log.w("[TodoReminderScheduler] 다시 예약 실패: ${it::class.java.simpleName}")
                }
            }
        }
    }

    /**
     * [reschedule]의 잠금 안 본체
     *
     * 항목 하나의 취소·예약 실패가 나머지를 막지 않도록 항목마다 예외를 처리함.
     * 저장 ID는 지난 ID에서 취소한 것을 빼고 PendingIntent를 만든 대상을 더한 집합이라,
     * 취소에 실패한 지난 ID와 예약에 실패한 대상은 남아 다음 호출에서 다시 다룸.
     * 알람을 건드린 뒤에는 예외나 취소가 와도 저장하도록 `finally`와 [NonCancellable]로 저장.
     */
    private suspend fun rescheduleLocked() {
        // 대상, 취소하지 않을 ID, 지난 예약 ID 읽기
        val tasks = repository.getAll()
        val currentTime = now()
        val targets = targets(tasks, currentTime)
        val keptIds = keptAlarmIds(tasks, currentTime)
        val liveIds = settings.getScheduledAlarmIds().toMutableSet()
        try {
            // 남길 ID에서 빠진 지난 알람 취소(늦게 울릴 수 있는 알람은 남김)
            (liveIds - keptIds).forEach { pageId ->
                if (cancel(pageId)) {
                    liveIds -= pageId
                }
            }

            // 대상 예약(정확한 알람 허용은 여기서 한 번 확인하고, 거부되면 남은 대상은 부정확한 알람으로 예약)
            val alarms = ExactAlarmScheduler(context)
            for (task in targets) {
                try {
                    val triggerAt = (task.due as TodoDue.DateTime).instant
                    val operation = requireNotNull(pendingIntent(task.pageId, PendingIntent.FLAG_UPDATE_CURRENT, triggerAt))
                    // PendingIntent가 생겼으므로 예약이 실패해도 다음 호출이 취소할 수 있게 추적
                    liveIds += task.pageId
                    // AlarmManager는 epoch 밀리초로 예약
                    alarms.schedule(triggerAt.toEpochMilli(), operation)
                } catch (failure: Exception) {
                    Log.w("[TodoReminderScheduler] 알람 예약 실패: ${failure::class.java.simpleName}")
                }
            }
        } finally {
            // 걸려 있을 수 있는 ID 저장
            withContext(NonCancellable) {
                settings.saveScheduledAlarmIds(liveIds).onFailure {
                    Log.w("[TodoReminderScheduler] 예약 ID 저장 실패: ${it::class.java.simpleName}")
                }
            }
        }
    }

    /**
     * [pageId]의 알람 취소
     *
     * @return 취소했거나 예약된 것이 없으면 true, 취소 중 예외가 나면 false
     */
    private fun cancel(pageId: String): Boolean = try {
        pendingIntent(pageId, PendingIntent.FLAG_NO_CREATE)?.let { operation -> ExactAlarmScheduler.cancel(context, operation) }
        true
    } catch (failure: Exception) {
        Log.w("[TodoReminderScheduler] 알람 취소 실패: ${failure::class.java.simpleName}")
        false
    }

    private fun pendingIntent(pageId: String, flag: Int, triggerAt: Instant? = null): PendingIntent? =
        PendingIntent.getBroadcast(context, 0, alarmIntent(context, pageId, triggerAt), flag or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        /** 알람 인텐트 액션 */
        const val ACTION_ALARM = "com.black.feature.todo.ALARM"

        /**
         * 다시 예약 호출끼리 서로 끼어들지 않게 하는 프로세스 전역 잠금
         *
         * 대상 읽기(`getAll`)부터 지난 ID 읽기, 취소·예약, 저장까지 잠금 안에서 돌려, 옛 저장본을 읽은 호출이 나중에 저장해 새 결과를 덮어쓰지 않게 함.
         */
        private val mutex = Mutex()

        private const val URI_SCHEME = "todo"
        private const val URI_AUTHORITY = "alarm"
        private const val EXTRA_TRIGGER_AT = "com.black.feature.todo.extra.TRIGGER_AT"

        /**
         * [pageId] 알람이 [TodoReminderReceiver]에 보내는 인텐트
         *
         * page ID는 `todo://alarm/<pageId>` 데이터의 경로에 인코딩해 담아 알람마다 PendingIntent가 구분됨.
         * 예약 시각 [triggerAt]은 epoch 밀리초 extra로 담음.
         * extra는 PendingIntent 구분에 쓰이지 않아, 같은 page ID를 다시 예약하면 `FLAG_UPDATE_CURRENT`가 새 예약 시각으로 바꿈.
         */
        internal fun alarmIntent(context: Context, pageId: String, triggerAt: Instant? = null): Intent =
            Intent(context, TodoReminderReceiver::class.java)
                .setAction(ACTION_ALARM)
                .setData(Uri.Builder().scheme(URI_SCHEME).authority(URI_AUTHORITY).appendPath(pageId).build())
                .apply {
                    if (triggerAt != null) {
                        putExtra(EXTRA_TRIGGER_AT, triggerAt.toEpochMilli())
                    }
                }

        /** [alarmIntent]가 담은 page ID, 데이터가 없으면 null */
        internal fun pageIdOf(intent: Intent): String? = intent.data?.lastPathSegment

        /** [alarmIntent]가 담은 예약 시각, 담지 않은 인텐트면 null */
        internal fun triggerAtOf(intent: Intent): Instant? =
            if (intent.hasExtra(EXTRA_TRIGGER_AT)) Instant.ofEpochMilli(intent.getLongExtra(EXTRA_TRIGGER_AT, 0L)) else null

        /**
         * 기한이 지난 뒤에도 지난 예약 알람을 취소하지 않고 두는 시간
         *
         * 정확한 알람 허용이 없으면 부정확한 알람(`setAndAllowWhileIdle`)이 기한보다 늦게 울림.
         * 대체로 1시간 안에 울리지만 Doze·앱 대기 버킷 제한으로 더 늦을 수 있음.
         * 그사이 동기화·화면 새로 고침·다른 알람 수신으로 다시 예약이 돌아 아직 울리지 않은 알람을 취소하면 알림이 사라지므로 이만큼 남겨 둠.
         * 이보다 늦게 울릴 알람은 유예가 지난 뒤의 다시 예약에서 취소될 수 있음.
         */
        val LATE_DELIVERY_GRACE: Duration = 1.hours

        /**
         * 울린 알람의 예약 시각과 저장본 기한을 같다고 보는 허용치
         *
         * 예약 시각과 1분 이하로 차이 나는 기한은 옮겨진 것으로 보지 않도록 둠.
         */
        val ALARM_TIME_TOLERANCE: Duration = 1.minutes

        /**
         * 시각 알림을 걸 항목
         *
         * 미완료, 알림 `TIME`, 기한이 [now]보다 뒤인 시각. 날짜만 있는 기한은 제외.
         */
        fun targets(tasks: List<Todo>, now: Instant): List<Todo> = tasks.filter { task ->
            isTimeReminder(task) && (task.due as TodoDue.DateTime).instant > now
        }

        /**
         * 지난 예약 알람을 취소하지 않고 둘 page ID
         *
         * 아직 시각 알림 항목이고 기한이 [now] − [LATE_DELIVERY_GRACE] 이후인 항목이라 [targets]를 모두 포함함.
         * 저장본에서 사라졌거나, 완료됐거나, 알림 방식이 바뀌었거나, 기한이 유예 시간보다 오래 지난 항목은 빠져 취소 대상이 됨.
         */
        fun keptAlarmIds(tasks: List<Todo>, now: Instant): Set<String> {
            val graceStart = now - LATE_DELIVERY_GRACE.toJavaDuration()
            return tasks
                .filter { task -> isTimeReminder(task) && (task.due as TodoDue.DateTime).instant >= graceStart }
                .map { task -> task.pageId }
                .toSet()
        }

        /**
         * 울린 알람이 저장본의 지금 기한 [due]에 맞는 알람인지 여부
         *
         * 예약 뒤 기한이 앞이나 뒤로 옮겨졌는데 다시 예약 전에 옛 알람이 울린 경우를 거르는 데 씀.
         * 알람의 예약 시각 [triggerAt]과 기한의 차가 [ALARM_TIME_TOLERANCE] 이하일 때만 맞음으로 봐, 늦게 울린 알람도 기한이 그대로면 알림.
         */
        fun isAlarmForCurrentDue(due: Instant, triggerAt: Instant): Boolean {
            val tolerance = ALARM_TIME_TOLERANCE.toJavaDuration()
            return due in (triggerAt - tolerance)..(triggerAt + tolerance)
        }

        /**
         * 시각 알림을 띄울 항목 여부
         *
         * 미완료, 알림 `TIME`, 시각까지 있는 기한. 알람이 울린 뒤 저장본을 다시 볼 때도 같은 조건을 씀.
         */
        fun isTimeReminder(task: Todo): Boolean =
            !task.done && task.reminder == TodoReminder.TIME && task.due is TodoDue.DateTime
    }
}
