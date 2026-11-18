package com.black.app.assistanttask

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 비서 Todo의 기한 날짜 판정 함수 모음 */
object AssistantTaskDates {

    private const val DATE_PATTERN = "yyyy-MM-dd"
    private const val MINUTES_PER_HOUR = 60
    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    /**
     * 기한의 [zone] 기준 날짜(`yyyy-MM-dd`)
     *
     * 날짜만 있는 기한은 시간대와 무관하게 그 날짜 그대로.
     */
    fun localDateOf(due: AssistantTaskDue, zone: TimeZone): String = when (due) {
        is AssistantTaskDue.DateOnly -> due.date
        is AssistantTaskDue.DateTime -> formatDate(due.epochMillis, zone)
    }

    /**
     * 기한 정렬 기준
     *
     * [zone] 기준 날짜 오름차순이며 같은 날은 날짜만 있는 기한이 먼저, 그다음 시각 순.
     */
    fun dueComparator(zone: TimeZone): Comparator<AssistantTaskDue> = compareBy<AssistantTaskDue>(
        { localDateOf(it, zone) },
        { if (it is AssistantTaskDue.DateOnly) 0 else 1 },
        { (it as? AssistantTaskDue.DateTime)?.epochMillis ?: 0L },
    )

    /**
     * 기한 있는 Todo를 [dueComparator] 순서로 정렬
     *
     * Todo 화면 구획과 집 도착 알림 대상이 함께 씀. 기한 없는 항목이 섞이면 [IllegalArgumentException].
     */
    fun sortedByDue(tasks: List<AssistantTask>, zone: TimeZone): List<AssistantTask> =
        tasks.sortedWith(compareBy(dueComparator(zone)) { task: AssistantTask -> requireNotNull(task.due) })

    /** [epochMillis]의 [zone] 기준 날짜(`yyyy-MM-dd`) */
    fun formatDate(epochMillis: Long, zone: TimeZone): String =
        SimpleDateFormat(DATE_PATTERN, Locale.US).apply { timeZone = zone }.format(Date(epochMillis))

    /**
     * [date]의 [zone] 기준 벽시계 시각 [minutesOfDay]분(자정부터)의 epoch 밀리초
     *
     * 자정에 분을 더하지 않고 연·월·일·시·분을 달력에 직접 넣어, 서머타임 전환일에도 고른 시각을 가리킴.
     * [date]가 `yyyy-MM-dd` 형식의 실제 날짜가 아니면 null.
     */
    fun dateTimeMillis(date: String, minutesOfDay: Int, zone: TimeZone): Long? {
        // 형식과 실제 날짜 확인(서머타임이 없는 UTC로 읽어 자정이 건너뛰어지는 시간대에서도 날짜를 읽음)
        val format = SimpleDateFormat(DATE_PATTERN, Locale.US).apply {
            timeZone = UTC
            isLenient = false
        }
        val parsed = runCatching { format.parse(date) }.getOrNull() ?: return null
        val dateFields = Calendar.getInstance(UTC, Locale.US).apply { time = parsed }
        // 시간대 달력에 연·월·일·시·분 지정
        return Calendar.getInstance(zone, Locale.US).apply {
            clear()
            set(
                dateFields.get(Calendar.YEAR),
                dateFields.get(Calendar.MONTH),
                dateFields.get(Calendar.DAY_OF_MONTH),
                minutesOfDay / MINUTES_PER_HOUR,
                minutesOfDay % MINUTES_PER_HOUR,
            )
        }.timeInMillis
    }
}
