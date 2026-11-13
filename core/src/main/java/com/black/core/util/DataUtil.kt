package com.black.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DataUtil {

    private const val SEC_IN_MS = 1000
    private const val MIN_IN_MS = SEC_IN_MS * 60
    private const val HOUR_IN_MS = MIN_IN_MS * 60

    /** [formatDateTime]이 재사용하는 패턴·로케일별 포맷 객체(SimpleDateFormat은 스레드 안전하지 않아 스레드마다 따로 보관) */
    private val dateFormats = object : ThreadLocal<HashMap<Pair<String, Locale>, SimpleDateFormat>>() {
        override fun initialValue() = HashMap<Pair<String, Locale>, SimpleDateFormat>()
    }

    /**
     * [timeMillis] 시각(기본값 현재 시각)을 [pattern]·[locale]로 포맷한 날짜·시각 텍스트
     *
     * 플로팅 시계처럼 짧은 주기로 불러도 포맷 객체를 새로 만들지 않도록 재사용하고, 시간대는 호출마다 그 시점의 기본 시간대 적용
     */
    fun formatDateTime(pattern: String, locale: Locale, timeMillis: Long = System.currentTimeMillis()): String {
        val dateFormat = dateFormats.get()!!.getOrPut(pattern to locale) { SimpleDateFormat(pattern, locale) }
        // 포맷 객체는 생성 시점의 기본 시간대를 유지하므로, 재사용 중 기기 시간대가 바뀌어도 반영되도록 매번 지정
        dateFormat.timeZone = TimeZone.getDefault()
        return dateFormat.format(Date(timeMillis))
    }

    /**
     * ms를 시간 텍스트로 노출
     * 패턴이 HH:mm:ss이고 3일인 경우 72:00:00 반환
     */
    fun milliSecondsToTimeString(pattern: String, milliSeconds: Long) : String {
        var remainPattern = pattern
        var remainTime = milliSeconds
        if (remainPattern.contains("H")) {
            val hours = remainTime / HOUR_IN_MS
            remainTime %= HOUR_IN_MS
            remainPattern = if (remainPattern.contains("HH")) {
                val hoursText = if (hours < 10) {
                    "0$hours"
                } else {
                    hours.toString()
                }
                remainPattern.replace("HH", hoursText)
            } else {
                remainPattern.replace("H", hours.toString())
            }
        }

        if (remainPattern.contains("m")) {
            val minutes = remainTime / MIN_IN_MS
            remainTime %= MIN_IN_MS
            remainPattern = if (remainPattern.contains("mm")) {
                val minutesText = if (minutes < 10) {
                    "0$minutes"
                } else {
                    minutes.toString()
                }
                remainPattern.replace("mm", minutesText)
            } else {
                remainPattern.replace("m", minutes.toString())
            }
        }

        if (remainPattern.contains("s")) {
            val seconds = remainTime / SEC_IN_MS
            remainTime %= SEC_IN_MS
            remainPattern = if (remainPattern.contains("ss")) {
                val secondsText = if (seconds < 10) {
                    "0$seconds"
                } else {
                    seconds.toString()
                }
                remainPattern.replace("ss", secondsText)
            } else {
                remainPattern.replace("s", seconds.toString())
            }
        }

        return try {
            SimpleDateFormat(remainPattern, Locale.US).format(Date(milliSeconds))
        } catch(e: IllegalArgumentException) {
            Log.w(e)
            ""
        }
    }

}
