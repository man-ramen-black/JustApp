package com.black.app.assistanttask.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.black.app.assistanttask.AssistantTaskDue
import com.black.app.model.AssistantTaskRepository
import com.black.app.model.AssistantTaskSettingsRepository
import com.black.core.util.Log
import com.black.core.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Todo 시각 알림 다시 예약 창구 */
fun interface AssistantTaskAlarmRescheduler {
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
class AssistantTaskAlarmScheduler(
    private val context: Context,
    private val repository: AssistantTaskRepository,
    private val settings: AssistantTaskSettingsRepository,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : AssistantTaskAlarmRescheduler {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context,
        AssistantTaskRepository(context),
        AssistantTaskSettingsRepository(context),
        Dispatchers.IO,
        System::currentTimeMillis,
    )

    /**
     * 저장본으로 알람 다시 예약
     *
     * 지난 ID 중 [AssistantTaskAlarms.keptAlarmIds]에 없는 것은 취소하고 대상은 예약한 뒤 걸려 있을 수 있는 ID를 저장.
     * 기한이 막 지난 시각 알림 항목은 늦게 울릴 알람일 수 있어 취소하지 않고 저장 ID에도 남김.
     * 동기화 뒤·Todo 변경 뒤·수신기에서 거의 동시에 불릴 수 있어 프로세스 전역 [Mutex]로 한 번에 하나씩 돌림.
     * 저장소 예외는 클래스 이름만 로그에 남기고 던지지 않음.
     */
    override suspend fun reschedule() {
        // 잠금을 디스패처 안에서 잡아, 본체가 끝나면 호출자 디스패처로 돌아가기를 기다리지 않고 바로 풂
        withContext(dispatcher) {
            mutex.withLock {
                try {
                    rescheduleLocked()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w("[AssistantTaskAlarmScheduler] 다시 예약 실패: ${failure::class.java.simpleName}")
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
        val nowMillis = now()
        val targets = AssistantTaskAlarms.targets(tasks, nowMillis)
        val keptIds = AssistantTaskAlarms.keptAlarmIds(tasks, nowMillis)
        val liveIds = settings.getScheduledAlarmIds().toMutableSet()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            // 남길 ID에서 빠진 지난 알람 취소(늦게 울릴 수 있는 알람은 남김)
            (liveIds - keptIds).forEach { pageId -> if (cancel(alarmManager, pageId)) liveIds -= pageId }

            // 대상 예약
            var exact = PermissionHelper.canScheduleExactAlarms(context)
            for (task in targets) {
                try {
                    val triggerAt = (task.due as AssistantTaskDue.DateTime).epochMillis
                    val operation = requireNotNull(pendingIntent(task.pageId, PendingIntent.FLAG_UPDATE_CURRENT, triggerAt))
                    // PendingIntent가 생겼으므로 예약이 실패해도 다음 호출이 취소할 수 있게 추적
                    liveIds += task.pageId
                    if (exact && setExactAlarm(alarmManager, triggerAt, operation)) continue
                    // 대체 호출이 던져도 다음 항목이 정확한 알람을 다시 시도하지 않도록 부정확한 알람 전에 끔
                    exact = false
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
                } catch (failure: Exception) {
                    Log.w("[AssistantTaskAlarmScheduler] 알람 예약 실패: ${failure::class.java.simpleName}")
                }
            }
        } finally {
            // 걸려 있을 수 있는 ID 저장
            withContext(NonCancellable) {
                settings.saveScheduledAlarmIds(liveIds).onFailure {
                    Log.w("[AssistantTaskAlarmScheduler] 예약 ID 저장 실패: ${it::class.java.simpleName}")
                }
            }
        }
    }

    /**
     * 정확한 알람 하나를 예약하고 성공 여부 반환
     *
     * 허용 확인 뒤 사용자가 허용을 끄면 정확한 알람이 [SecurityException]을 던지므로, false를 돌려줘 그 항목부터 부정확한 알람으로 예약하게 함.
     */
    private fun setExactAlarm(alarmManager: AlarmManager, triggerAt: Long, operation: PendingIntent): Boolean = try {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        true
    } catch (revoked: SecurityException) {
        Log.w("[AssistantTaskAlarmScheduler] 정확한 알람 거부, 부정확한 알람으로 예약")
        false
    }

    /**
     * [pageId]의 알람 취소
     *
     * @return 취소했거나 예약된 것이 없으면 true, 취소 중 예외가 나면 false
     */
    private fun cancel(alarmManager: AlarmManager, pageId: String): Boolean = try {
        pendingIntent(pageId, PendingIntent.FLAG_NO_CREATE)?.let { operation ->
            alarmManager.cancel(operation)
            operation.cancel()
        }
        true
    } catch (failure: Exception) {
        Log.w("[AssistantTaskAlarmScheduler] 알람 취소 실패: ${failure::class.java.simpleName}")
        false
    }

    private fun pendingIntent(pageId: String, flag: Int, triggerAtMillis: Long? = null): PendingIntent? =
        PendingIntent.getBroadcast(context, 0, alarmIntent(context, pageId, triggerAtMillis), flag or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        /** 알람 인텐트 액션 */
        const val ACTION_ALARM = "com.black.app.assistanttask.ALARM"

        /**
         * 다시 예약 호출끼리 서로 끼어들지 않게 하는 프로세스 전역 잠금
         *
         * 대상 읽기(`getAll`)부터 지난 ID 읽기, 취소·예약, 저장까지 잠금 안에서 돌려, 옛 저장본을 읽은 호출이 나중에 저장해 새 결과를 덮어쓰지 않게 함.
         */
        private val mutex = Mutex()

        private const val URI_SCHEME = "assistanttask"
        private const val URI_AUTHORITY = "alarm"
        private const val EXTRA_TRIGGER_AT = "com.black.app.assistanttask.extra.TRIGGER_AT"

        /**
         * [pageId] 알람이 [AssistantTaskAlarmReceiver]에 보내는 인텐트
         *
         * page ID는 `assistanttask://alarm/<pageId>` 데이터의 경로에 인코딩해 담아 알람마다 PendingIntent가 구분됨.
         * 예약 시각 [triggerAtMillis]는 extra로 담음.
         * extra는 PendingIntent 구분에 쓰이지 않아, 같은 page ID를 다시 예약하면 `FLAG_UPDATE_CURRENT`가 새 예약 시각으로 바꿈.
         */
        internal fun alarmIntent(context: Context, pageId: String, triggerAtMillis: Long? = null): Intent =
            Intent(context, AssistantTaskAlarmReceiver::class.java)
                .setAction(ACTION_ALARM)
                .setData(Uri.Builder().scheme(URI_SCHEME).authority(URI_AUTHORITY).appendPath(pageId).build())
                .apply { if (triggerAtMillis != null) putExtra(EXTRA_TRIGGER_AT, triggerAtMillis) }

        /** [alarmIntent]가 담은 page ID, 데이터가 없으면 null */
        internal fun pageIdOf(intent: Intent): String? = intent.data?.lastPathSegment

        /** [alarmIntent]가 담은 예약 시각, 담지 않은 인텐트면 null */
        internal fun triggerAtOf(intent: Intent): Long? =
            if (intent.hasExtra(EXTRA_TRIGGER_AT)) intent.getLongExtra(EXTRA_TRIGGER_AT, 0L) else null
    }
}
