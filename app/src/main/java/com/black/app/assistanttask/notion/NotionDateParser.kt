package com.black.app.assistanttask.notion

import com.black.app.assistanttask.AssistantTaskDue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 노션 날짜 문자열 해석
 *
 * minSdk 24에 디슈가링이 없어 `java.time` 대신 정규식과 [Calendar]를 사용.
 */
object NotionDateParser {
    private val DATE_PATTERN = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val DATE_TIME_PATTERN =
        Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?(Z|[+-]\d{2}:\d{2})?$""")

    /**
     * 기한 `start` 값 해석
     *
     * 시각에 오프셋이 없으면 [timeZoneId], 그것도 없으면 [defaultZone] 기준. 형식이 맞지 않으면 null.
     */
    fun parseDue(
        start: String?,
        timeZoneId: String?,
        defaultZone: TimeZone = TimeZone.getDefault(),
    ): AssistantTaskDue? {
        if (start.isNullOrEmpty()) return null
        val dateMatch = DATE_PATTERN.matchEntire(start)
        if (dateMatch != null) {
            val (year, month, day) = dateMatch.destructured
            val valid = toEpochMillis(year, month, day, "0", "0", "0", "", TimeZone.getTimeZone("UTC")) != null
            return if (valid) AssistantTaskDue.DateOnly(start) else null
        }
        // 알 수 없는 ID는 TimeZone이 GMT로 돌려주므로 요청한 ID와 같은 경우만 사용
        val fallbackZone = timeZoneId?.takeIf { it.isNotEmpty() }?.let { id ->
            TimeZone.getTimeZone(id).takeIf { it.id == id }
        } ?: defaultZone
        return parseDateTime(start, fallbackZone)?.let { AssistantTaskDue.DateTime(it) }
    }

    /** `2026-11-18T03:12:00.000Z` 형식의 시각을 epoch millis로 변환. 실패하면 null */
    fun parseTimestamp(value: String?): Long? {
        if (value.isNullOrEmpty()) return null
        return parseDateTime(value, TimeZone.getTimeZone("UTC"))
    }

    /** epoch millis를 UTC `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` 문자열로 변환 */
    fun formatTimestamp(epochMillis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(epochMillis)
    }

    /** epoch millis를 [zone] 기준 `yyyy-MM-dd'T'HH:mm:ss` 문자열로 변환. 오프셋은 붙이지 않음 */
    fun formatLocalDateTime(epochMillis: Long, zone: TimeZone): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        format.timeZone = zone
        return format.format(epochMillis)
    }

    private fun parseDateTime(value: String, fallbackZone: TimeZone): Long? {
        val match = DATE_TIME_PATTERN.matchEntire(value) ?: return null
        val (year, month, day, hour, minute, second, fraction, offset) = match.destructured
        val zone = when {
            offset.isEmpty() -> fallbackZone
            offset == "Z" -> TimeZone.getTimeZone("UTC")
            else -> TimeZone.getTimeZone("GMT$offset")
        }
        return toEpochMillis(year, month, day, hour, minute, second.ifEmpty { "0" }, fraction, zone)
    }

    /**
     * 엄격(non-lenient) 해석
     *
     * 존재하지 않는 날짜·시각이면 null.
     */
    private fun toEpochMillis(
        year: String, month: String, day: String,
        hour: String, minute: String, second: String,
        fraction: String, zone: TimeZone,
    ): Long? {
        val millis = fraction.padEnd(3, '0').take(3).toInt()
        val calendar = Calendar.getInstance(zone, Locale.US)
        calendar.isLenient = false
        calendar.clear()
        return try {
            calendar.set(year.toInt(), month.toInt() - 1, day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
            calendar.set(Calendar.MILLISECOND, millis)
            calendar.timeInMillis
        } catch (exception: IllegalArgumentException) {
            null
        }
    }
}
