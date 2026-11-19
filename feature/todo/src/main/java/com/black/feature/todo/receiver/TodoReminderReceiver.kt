package com.black.feature.todo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.black.core.util.ComponentExtensions.launchAsync
import com.black.core.util.Log
import com.black.core.util.runSuspendCatching
import com.black.feature.todo.data.TodoRepository
import com.black.feature.todo.model.TodoDue
import com.black.feature.todo.notification.TodoNotifier
import com.black.feature.todo.notification.TodoReminderRescheduler
import com.black.feature.todo.notification.TodoReminderScheduler
import java.time.Instant

/**
 * Todo 시각 알림 알람 수신기
 *
 * 예약 뒤 완료되거나 알림 방식이 바뀌었을 수 있어 저장본을 다시 보고, 아직 시각 알림 항목일 때만 알림.
 * 예약 뒤 기한이 앞이나 뒤로 옮겨졌는데 다시 예약 전에 옛 알람이 울렸으면 알리지 않음.
 * 알림 여부와 상관없이 저장본으로 다시 예약해, 옮겨진 기한의 알람을 걸고 더는 시각 알림 대상이 아닌 항목의 예약 ID를 정리함.
 * 울린 항목이 아직 시각 알림 대상이면 [TodoReminderScheduler.LATE_DELIVERY_GRACE] 동안 저장 ID에 남고, 유예가 지난 뒤의 다시 예약에서 빠짐.
 * 예약 시각이 없는 인텐트는 알리지 않고 다시 예약만 함.
 * 매니페스트에서 exported=false라 예약기가 만든 인텐트만 오고 예약기는 예약 시각을 늘 담아, 이런 인텐트를 위한 대체 판정은 두지 않음.
 */
class TodoReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 예약기가 만든 알람 인텐트만 처리
        if (intent.action != TodoReminderScheduler.ACTION_ALARM) {
            return
        }
        val pageId = TodoReminderScheduler.pageIdOf(intent) ?: return
        val triggerAt = TodoReminderScheduler.triggerAtOf(intent)

        // 의존성 준비(저장본과 알림기는 알림 판정에서 필요할 때만 만듦)
        val applicationContext = context.applicationContext
        val alarms = createRescheduler(applicationContext)
        launchAsync {
            // 예약 시각이 없으면 지금 기한의 알람인지 가릴 수 없어 알리지 않음
            if (triggerAt != null) {
                notifyIfTimeReminder(applicationContext, pageId, triggerAt)
            }
            alarms.reschedule()
        }
    }

    /**
     * 저장본의 [pageId] 항목이 아직 시각 알림 항목이고 울린 알람이 지금 기한의 알람이면 알림
     *
     * 기한과 예약 시각 [triggerAt]의 비교는 [TodoReminderScheduler.isAlarmForCurrentDue]가 맡음.
     * 저장본 예외는 다시 예약이 이어지도록 클래스 이름만 로그에 남기고 던지지 않음.
     */
    private suspend fun notifyIfTimeReminder(context: Context, pageId: String, triggerAt: Instant) {
        runSuspendCatching {
            // 저장본 재확인
            val task = createRepository(context).getById(pageId) ?: return
            if (!TodoReminderScheduler.isTimeReminder(task)) {
                return
            }
            // 기한이 옮겨졌으면 옛 알람이라 알리지 않음(새 기한 알람은 이어지는 다시 예약이 맡음)
            val due = (task.due as TodoDue.DateTime).instant
            if (!TodoReminderScheduler.isAlarmForCurrentDue(due, triggerAt)) {
                return
            }
            // 알림
            TodoNotifier(context).showTimeReminder(task)
        }.onFailure {
            Log.w("[TodoReminderReceiver] 시각 알림 실패: ${it::class.java.simpleName}")
        }
    }

    companion object {
        /** 저장본 생성 함수, 테스트에서 바꿔 넣음 */
        @VisibleForTesting
        internal var createRepository: (Context) -> TodoRepository = ::TodoRepository

        /** 다시 예약기 생성 함수, 테스트에서 바꿔 넣음 */
        @VisibleForTesting
        internal var createRescheduler: (Context) -> TodoReminderRescheduler = ::TodoReminderScheduler
    }
}
