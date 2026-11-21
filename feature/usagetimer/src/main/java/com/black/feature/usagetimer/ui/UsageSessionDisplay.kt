package com.black.feature.usagetimer.ui

import android.content.res.Resources
import com.black.core.system.AppUsageSession
import com.black.core.util.DataUtil
import com.black.feature.usagetimer.R
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** 구간 목록의 날짜 묶음 */
enum class UsageDay { TODAY, YESTERDAY }

/**
 * 화면에 보이는 사용 구간 한 줄
 *
 * @property endText 시작과 끝이 같은 분이면 null(시각 하나만 표시)
 * @property minutes 분 단위로 내린 끝 시각과 시작 시각의 차이
 */
data class UsageSessionRow(
    val startText: String,
    val endText: String?,
    val startsBeforePeriod: Boolean,
    val inProgress: Boolean,
    val minutes: Long,
)

/**
 * 같은 날짜에 시작한 줄 묶음
 *
 * @property totalMinutes 화면에서 숨긴 줄까지 포함한 그 날짜 줄 시간의 합
 */
data class UsageDayGroup(
    val day: UsageDay,
    val totalMinutes: Long,
    val rows: List<UsageSessionRow>,
)

/**
 * 한 앱의 사용 기록 표시 모델
 *
 * [groups]는 최신 날짜부터, 묶음 안 줄은 최신 구간부터. [totalMinutes]는 모든 줄 시간의 합.
 */
data class AppUsageDisplay(
    val totalMinutes: Long,
    val groups: List<UsageDayGroup>,
) {
    val rowCount: Int get() = groups.sumOf { it.rows.size }

    val hasSessions: Boolean get() = rowCount > 0

    /**
     * 최신 [limit]줄만 남긴 묶음
     *
     * 날짜 소계는 그대로 두고, 줄이 모두 빠진 날짜는 뺌.
     */
    fun visibleGroups(limit: Int): List<UsageDayGroup> {
        var remaining = limit
        val visible = ArrayList<UsageDayGroup>()
        for (group in groups) {
            if (remaining <= 0) {
                break
            }
            val rows = group.rows.take(remaining)
            remaining -= rows.size
            visible.add(group.copy(rows = rows))
        }
        return visible
    }
}

/**
 * 사용 구간을 화면 표시 모델로 변환
 *
 * 밀리초 합계를 분으로 내리면 줄 시간의 합과 어긋나므로, 줄 시간은 분 단위로 내린 끝 시각과 시작 시각의 차이로 계산하고
 * 날짜 소계와 합계는 그 줄 시간의 합으로 계산함. 시각과 날짜는 기기 기본 시간대 기준이고 오늘은 [queriedAtMillis]의 날짜.
 */
fun buildAppUsageDisplay(sessions: List<AppUsageSession>, queriedAtMillis: Long): AppUsageDisplay {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(queriedAtMillis).atZone(zone).toLocalDate()
    // 최신 순으로 정렬해 묶으면 오늘 묶음이 먼저 옴
    val sessionsByDay = sessions.sortedByDescending { it.start }.groupBy { session ->
        val startDate = Instant.ofEpochMilli(session.start).atZone(zone).toLocalDate()
        if (startDate == today) UsageDay.TODAY else UsageDay.YESTERDAY
    }
    val groups = sessionsByDay.map { (day, daySessions) ->
        val rows = daySessions.map(::toRow)
        UsageDayGroup(day = day, totalMinutes = rows.sumOf { it.minutes }, rows = rows)
    }
    return AppUsageDisplay(totalMinutes = groups.sumOf { it.totalMinutes }, groups = groups)
}

/**
 * 분 단위 사용 시간을 화면 문구로 변환
 *
 * 0분이면 구간이 있을 때([hasSessions]) "1분 미만", 없으면 "0분". 1시간 이상은 "시간 분"으로 표시.
 */
fun formatUsageMinutes(resources: Resources, minutes: Long, hasSessions: Boolean): String {
    val hours = minutes / 60L
    val restMinutes = minutes % 60L
    return when {
        hours >= 1L -> resources.getString(R.string.usage_timer_usage_hours_minutes, hours, restMinutes)
        minutes == 0L && hasSessions -> resources.getString(R.string.usage_timer_usage_under_minute)
        else -> resources.getString(R.string.usage_timer_usage_minutes, restMinutes)
    }
}

/** 구간 한 건을 줄로 변환 */
private fun toRow(session: AppUsageSession): UsageSessionRow {
    val minutes = Math.floorDiv(session.end, MINUTE_MILLIS) - Math.floorDiv(session.start, MINUTE_MILLIS)
    return UsageSessionRow(
        startText = clockText(session.start),
        endText = if (minutes == 0L) null else clockText(session.end),
        startsBeforePeriod = session.startsBeforePeriod,
        inProgress = session.inProgress,
        minutes = minutes,
    )
}

private fun clockText(timeMillis: Long): String = DataUtil.formatDateTime(CLOCK_PATTERN, Locale.US, timeMillis)

private const val CLOCK_PATTERN = "HH:mm"
private const val MINUTE_MILLIS = 60_000L
