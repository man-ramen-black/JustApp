package com.black.app.assistanttask.arrival

import com.black.app.assistanttask.AssistantTask
import com.black.app.assistanttask.AssistantTaskDates
import com.black.app.assistanttask.AssistantTaskReminder
import com.black.app.usagelog.collect.HomeTransition
import java.util.TimeZone
import kotlin.math.abs

/** 집 도착 알림의 발송 판정과 알릴 Todo 선별 */
object AssistantTaskHomeArrival {

    /**
     * 집을 나선 뒤 이 시간이 지나야 도착으로 보는 최소 외출 시간
     *
     * 집 Wi-Fi가 잠깐 끊겼다 다시 붙은 것을 도착으로 잘못 알리지 않기 위함.
     */
    const val MIN_AWAY_MILLIS = 600_000L

    /**
     * 추정 출발 뒤 실제 도착을 알리지 않는 시간
     *
     * 마지막 Wi-Fi 상태가 집 연결로 저장된 채 재부팅하면 알림 접근 서비스가 Wi-Fi 연결보다 먼저 붙어 초기 Wi-Fi 목록이 비고,
     * 수집 켜짐 처리 때 추정 출발이 생긴 뒤 몇 초 만에 Wi-Fi 연결 보고가 실제 도착으로 들어옴.
     * 추정 출발 시각은 실제 출발이 아니라 상태를 맞춘 시각이라 마지막 실제 출발로 쓰지 않으므로, 실제 출발이 그날 아침이면
     * [MIN_AWAY_MILLIS] 판정을 통과해 계속 집에 있었는데도 알림이 뜸.
     * 함정: 집에서 서비스가 멈춘 채 외출했다가 밖에서 다시 붙은 뒤 이 시간 안에 실제로 집에 도착해도 알리지 않음.
     */
    const val INFERRED_LEAVE_SETTLE_MILLIS = 300_000L

    /**
     * 이 전환에 집 도착 알림을 띄울지
     *
     * 실제 도착(추정 아님)이며 [lastInferredLeaveTime]과 [INFERRED_LEAVE_SETTLE_MILLIS] 이상 떨어져 있고,
     * 출발 기록이 없거나 [lastLeaveTime]부터 [MIN_AWAY_MILLIS] 이상 지났을 때만 true.
     * 추정 도착은 [HomeTransition]에 적은 경우처럼 나중에 알게 된 상태를 맞춘 기록이라 지금 집에 막 도착했다는 뜻이 아님.
     * 기기 시계가 뒤로 가 실제 출발 시각이 전환 시각보다 늦으면 그 기록은 없는 것으로 봄(오래 외출했어도 알리지 못하는 일 방지).
     * 추정 출발만은 앞뒤 차의 절댓값으로 봄.
     * 부팅 직후 기기 시계가 몇 초 뒤로 보정되면 재부팅 뒤 Wi-Fi 연결 보고가 추정 출발보다 이른 시각으로 와, 기록 없음으로 보면 알림을 막지 못함.
     */
    fun shouldNotify(transition: HomeTransition, lastLeaveTime: Long?, lastInferredLeaveTime: Long?): Boolean {
        if (!transition.arrived || transition.inferred) return false
        // 추정 출발 앞뒤의 실제 도착은 재부팅 뒤 Wi-Fi 연결 보고라 알리지 않음
        if (lastInferredLeaveTime != null && abs(transition.time - lastInferredLeaveTime) < INFERRED_LEAVE_SETTLE_MILLIS) return false
        val sinceLeave = elapsedSince(lastLeaveTime, transition.time)
        return sinceLeave == null || sinceLeave >= MIN_AWAY_MILLIS
    }

    /** [recordTime]부터 [time]까지 경과 시간, 기록이 없거나 시계가 뒤로 가 음수면 null */
    private fun elapsedSince(recordTime: Long?, time: Long): Long? =
        recordTime?.let { time - it }?.takeIf { it >= 0 }

    /**
     * 알릴 Todo 선별
     *
     * 미완료이고 알림이 집 도착이거나 기한 날짜(기기 시간대 [zone])가 [today](`yyyy-MM-dd`) 이전·당일인 항목.
     * 기한 순서는 [AssistantTaskDates.sortedByDue]를 따르고, 기한 없는 항목은 뒤에 두되 화면의 기한 없음 구획처럼 `lastEditedTime` 내림차순.
     */
    fun targets(tasks: List<AssistantTask>, today: String, zone: TimeZone): List<AssistantTask> {
        // 미완료이며 집 도착 알림이거나 기한이 오늘까지인 항목
        val selected = tasks
            .filter { !it.done }
            .filter { task ->
                val due = task.due
                // yyyy-MM-dd 형식은 문자열 비교가 날짜 비교와 같음
                task.reminder == AssistantTaskReminder.HOME_ARRIVAL ||
                    (due != null && AssistantTaskDates.localDateOf(due, zone) <= today)
            }
        // 기한 있는 항목은 기한 순, 기한 없는 항목은 최근 수정 순으로 뒤에
        val (dueTasks, noDueTasks) = selected.partition { it.due != null }
        return AssistantTaskDates.sortedByDue(dueTasks, zone) + noDueTasks.sortedByDescending { it.lastEditedTime }
    }
}
