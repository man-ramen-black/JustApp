package com.black.core.system

/** 기기 캘린더 하나 */
data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
)

/**
 * 기기 캘린더 일정이 시간 범위 안에서 갖는 발생 하나
 *
 * [begin]·[end]는 epoch 밀리초. 종일 일정([allDay])은 UTC 자정 기준 값.
 */
data class CalendarEventInstance(
    val eventId: Long,
    val calendarId: Long,
    val title: String?,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
)
