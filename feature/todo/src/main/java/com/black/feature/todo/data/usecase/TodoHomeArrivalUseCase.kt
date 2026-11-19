package com.black.feature.todo.data.usecase

import android.content.Context
import com.black.core.model.HomeTransition
import com.black.feature.todo.data.TodoSettingsRepository
import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoReminder
import com.black.feature.todo.model.sortedByDue
import com.black.feature.todo.worker.TodoHomeArrivalWorker
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * 집 전환 신호 처리
 *
 * 출발이면 실제·추정을 나눠 시각을 저장하고, 도착이 [shouldNotify]를 충족하면 도착 작업을 예약함.
 * 앱의 알림 리스너 Service가 Hilt 없이 만들어 쓰므로 저장소를 직접 만듦.
 * 출발 시각 저장이 실패하면 다음 도착은 그 전에 저장된 출발 시각으로 판정함.
 * 출발·도착 처리는 로그로 남기지 않음.
 * 실사용 앱(`stable`)도 `matchingFallbacks`로 core의 debug 변형을 써 core `BuildConfig.DEBUG`가 true라 core `Log`가 찍히고, 그 logcat 시각이 곧 출발·도착 시각임.
 * 집 도착 알림의 발송 판정과 알릴 Todo 선별은 companion에 둠.
 */
class TodoHomeArrivalUseCase(
    private val settings: TodoSettingsRepository,
    private val enqueueArrival: () -> Unit,
) {
    constructor(context: Context) : this(
        TodoSettingsRepository(context.applicationContext),
        { TodoHomeArrivalWorker.enqueue(context.applicationContext) },
    )

    suspend fun onHomeTransition(transition: HomeTransition) {
        // 추정 출발의 시각은 감시 시작, 수집 켜짐, 집 SSID 삭제처럼 상태를 맞춘 시각이라 실제 출발이 아니므로 마지막 출발로 저장하지 않고 따로 저장
        if (!transition.arrived) {
            if (transition.inferred) {
                settings.saveLastInferredHomeLeaveTime(transition.time)
            } else {
                settings.saveLastHomeLeaveTime(transition.time)
            }
            return
        }
        // 도착은 판정을 충족할 때만 작업 예약
        val lastLeaveTime = settings.getLastHomeLeaveTime()
        val lastInferredLeaveTime = settings.getLastInferredHomeLeaveTime()
        if (shouldNotify(transition, lastLeaveTime, lastInferredLeaveTime)) {
            enqueueArrival()
        }
    }

    companion object {

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
            if (!transition.arrived || transition.inferred) {
                return false
            }
            // 추정 출발 앞뒤의 실제 도착은 재부팅 뒤 Wi-Fi 연결 보고라 알리지 않음
            if (lastInferredLeaveTime != null && abs(transition.time - lastInferredLeaveTime) < INFERRED_LEAVE_SETTLE_MILLIS) {
                return false
            }
            val sinceLeave = elapsedSince(lastLeaveTime, transition.time)
            return sinceLeave == null || sinceLeave >= MIN_AWAY_MILLIS
        }

        /** [recordTime]부터 [time]까지 경과 시간, 기록이 없거나 시계가 뒤로 가 음수면 null */
        private fun elapsedSince(recordTime: Long?, time: Long): Long? =
            recordTime?.let { time - it }?.takeIf { it >= 0 }

        /**
         * 알릴 Todo 선별
         *
         * 미완료이고 알림이 집 도착이거나 기한 날짜(기기 시간대 [zone])가 [today] 이전·당일인 항목.
         * 기한 순서는 [sortedByDue]를 따르고, 기한 없는 항목은 뒤에 두되 화면의 기한 없음 구획처럼 `lastEditedTime` 내림차순.
         */
        fun targets(tasks: List<Todo>, today: LocalDate, zone: ZoneId): List<Todo> {
            // 미완료이며 집 도착 알림이거나 기한이 오늘까지인 항목
            val selected = tasks
                .filter { !it.done }
                .filter { task ->
                    val due = task.due
                    // 시각 기한은 기기 시간대 기준 날짜로 비교
                    task.reminder == TodoReminder.HOME_ARRIVAL ||
                        (due != null && due.localDate(zone) <= today)
                }
            // 기한 있는 항목은 기한 순, 기한 없는 항목은 최근 수정 순으로 뒤에
            val (dueTasks, noDueTasks) = selected.partition { it.due != null }
            return dueTasks.sortedByDue(zone) + noDueTasks.sortedByDescending { it.lastEditedTime }
        }
    }
}
