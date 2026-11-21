package com.black.feature.dday.model

import com.black.core.system.CalendarEventInstance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 오늘부터 며칠 뒤 시작하는 일정까지 위젯에 올리는지 */
const val DDAY_LOOKAHEAD_DAYS = 365L

/**
 * 불러오는 최대 일정 수이자 [DdayStyleField.ITEM_COUNT]로 고를 수 있는 최댓값
 *
 * 위젯 목록은 항목마다 RemoteViews를 만들어 한 번에 Binder로 보내므로, 캘린더를 많이 켜면 1MB 한도를 넘어 갱신이 실패함.
 * 캘린더 16개를 켠 1년 치 일정(약 300개)이 약 660KB였음.
 */
const val DDAY_MAX_ITEMS = 50

private val dateFormatter = DateTimeFormatter.ofPattern("M/d EEE", Locale.US)
private val timeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.US)

/**
 * 일정이 시작하는 날짜
 *
 * 종일 일정은 UTC 자정으로 저장되므로 [zone]과 무관하게 UTC 날짜를 쓰고,
 * 시각 일정은 [zone] 기준 날짜를 씀. 종일 일정에 [zone]을 쓰면 음의 오프셋 시간대에서 하루 앞당겨짐.
 */
fun eventStartDate(begin: Long, allDay: Boolean, zone: ZoneId): LocalDate {
    val eventZone = if (allDay) ZoneOffset.UTC else zone
    return Instant.ofEpochMilli(begin).atZone(eventZone).toLocalDate()
}

/** [startDate]까지 남은 날짜 문구, 당일은 `D-Day`, 이후는 `D-N` */
fun ddayLabel(startDate: LocalDate, today: LocalDate): String {
    val days = startDate.toEpochDay() - today.toEpochDay()
    return if (days <= 0L) "D-Day" else "D-$days"
}

/**
 * 위젯에 표시할 날짜·시각 문구
 *
 * 종일 일정은 `9/4 Sun`, 시각 일정은 `9/9 Fri PM 3:00`. 종일 일정의 날짜는 [eventStartDate]와 같은 이유로 UTC 기준.
 */
fun formatEventDate(begin: Long, allDay: Boolean, zone: ZoneId): String {
    val dateTime = Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone)
    val date = dateTime.format(dateFormatter)
    if (allDay) {
        return date
    }
    return "$date ${dateTime.format(timeFormatter)}"
}

/**
 * 위젯에 올릴 일정만 골라 정렬
 *
 * 제외 캘린더와 시작일이 [today] 이전이거나 [DDAY_LOOKAHEAD_DAYS] 이후인 일정은 뺌.
 * 날짜만 비교하므로 오늘 이미 시작한 일정도 남음.
 * 같은 `eventId`(반복 일정)는 가장 이른 회차 하나만 남기고,
 * 정렬은 시작일, 종일 먼저, `begin`, 제목(null은 빈 문자열) 순이며 앞에서 [DDAY_MAX_ITEMS]개만 남김.
 */
fun selectUpcoming(
    instances: List<CalendarEventInstance>,
    excludedCalendarIds: Set<Long>,
    today: LocalDate,
    zone: ZoneId,
): List<CalendarEventInstance> {
    val lastDate = today.plusDays(DDAY_LOOKAHEAD_DAYS)
    return instances
        .filter { it.calendarId !in excludedCalendarIds }
        .filter { eventStartDate(it.begin, it.allDay, zone) in today..lastDate }
        .groupBy { it.eventId }
        .map { (_, sameEvent) -> sameEvent.minBy { it.begin } }
        .sortedWith(
            compareBy<CalendarEventInstance> { eventStartDate(it.begin, it.allDay, zone) }
                .thenBy { !it.allDay }
                .thenBy { it.begin }
                .thenBy { it.title.orEmpty() },
        )
        .take(DDAY_MAX_ITEMS)
}

/**
 * 캘린더에 조회할 `begin..end` 밀리초 범위
 *
 * [today] 0시 하루 전부터 `today + 366일` 0시 하루 뒤까지. 종일 일정은 UTC 자정으로 저장돼
 * [zone]에 따라 범위 경계에서 빠질 수 있어 앞뒤로 하루씩 넓히고, 실제 선별은 [selectUpcoming]이 날짜로 함.
 */
fun queryRange(today: LocalDate, zone: ZoneId): LongRange {
    val start = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = today.plusDays(DDAY_LOOKAHEAD_DAYS + 2).atStartOfDay(zone).toInstant().toEpochMilli()
    return start..end
}
