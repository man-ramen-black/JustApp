package com.black.app.assistanttask.alarm

import com.black.app.assistanttask.AssistantTask
import com.black.app.assistanttask.AssistantTaskDue
import com.black.app.assistanttask.AssistantTaskReminder
import kotlin.math.abs

/** Todo 시각 알림 대상 판정 */
object AssistantTaskAlarms {

    /**
     * 기한이 지난 뒤에도 지난 예약 알람을 취소하지 않고 두는 시간
     *
     * 정확한 알람 허용이 없으면 부정확한 알람(`setAndAllowWhileIdle`)이 기한보다 늦게 울림.
     * 대체로 1시간 안에 울리지만 Doze·앱 대기 버킷 제한으로 더 늦을 수 있음.
     * 그사이 동기화·화면 새로 고침·다른 알람 수신으로 다시 예약이 돌아 아직 울리지 않은 알람을 취소하면 알림이 사라지므로 이만큼 남겨 둠.
     * 이보다 늦게 울릴 알람은 유예가 지난 뒤의 다시 예약에서 취소될 수 있음.
     */
    const val LATE_DELIVERY_GRACE_MILLIS = 3_600_000L

    /**
     * 울린 알람의 예약 시각과 저장본 기한을 같다고 보는 허용치
     *
     * 예약 시각과 1분 이하로 차이 나는 기한은 옮겨진 것으로 보지 않도록 둠.
     */
    const val ALARM_TIME_TOLERANCE_MILLIS = 60_000L

    /**
     * 시각 알림을 걸 항목
     *
     * 미완료, 알림 `TIME`, 기한이 [nowMillis]보다 뒤인 시각. 날짜만 있는 기한은 제외.
     */
    fun targets(tasks: List<AssistantTask>, nowMillis: Long): List<AssistantTask> = tasks.filter { task ->
        isTimeReminder(task) && (task.due as AssistantTaskDue.DateTime).epochMillis > nowMillis
    }

    /**
     * 지난 예약 알람을 취소하지 않고 둘 page ID
     *
     * 아직 시각 알림 항목이고 기한이 [nowMillis] − [LATE_DELIVERY_GRACE_MILLIS] 이후인 항목이라 [targets]를 모두 포함함.
     * 저장본에서 사라졌거나, 완료됐거나, 알림 방식이 바뀌었거나, 기한이 유예 시간보다 오래 지난 항목은 빠져 취소 대상이 됨.
     */
    fun keptAlarmIds(tasks: List<AssistantTask>, nowMillis: Long): Set<String> = tasks
        .filter { task ->
            isTimeReminder(task) && (task.due as AssistantTaskDue.DateTime).epochMillis >= nowMillis - LATE_DELIVERY_GRACE_MILLIS
        }
        .map { task -> task.pageId }
        .toSet()

    /**
     * 울린 알람이 저장본의 지금 기한 [dueMillis]에 맞는 알람인지 여부
     *
     * 예약 뒤 기한이 앞이나 뒤로 옮겨졌는데 다시 예약 전에 옛 알람이 울린 경우를 거르는 데 씀.
     * 알람의 예약 시각 [triggerAtMillis]와 기한의 차가 [ALARM_TIME_TOLERANCE_MILLIS] 이하일 때만 맞음으로 봐, 늦게 울린 알람도 기한이 그대로면 알림.
     */
    fun isAlarmForCurrentDue(dueMillis: Long, triggerAtMillis: Long): Boolean =
        abs(dueMillis - triggerAtMillis) <= ALARM_TIME_TOLERANCE_MILLIS

    /**
     * 시각 알림을 띄울 항목 여부
     *
     * 미완료, 알림 `TIME`, 시각까지 있는 기한. 알람이 울린 뒤 저장본을 다시 볼 때도 같은 조건을 씀.
     */
    fun isTimeReminder(task: AssistantTask): Boolean =
        !task.done && task.reminder == AssistantTaskReminder.TIME && task.due is AssistantTaskDue.DateTime
}
