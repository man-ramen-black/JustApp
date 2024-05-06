package com.black.core.util

import java.security.SecureRandom
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataUtil {
    private val RANDOM_CHAR_LIST : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    private const val SEC_IN_MS = 1000
    private const val MIN_IN_MS = SEC_IN_MS * 60
    private const val HOUR_IN_MS = MIN_IN_MS * 60

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
            e.printStackTrace()
            ""
        }
    }

    fun generateRandomString(length: Int): String {
        val random = SecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)

        return bytes.indices
            .map { RANDOM_CHAR_LIST[random.nextInt(RANDOM_CHAR_LIST.size)] }
            .joinToString("")
    }

    fun withComma(number: Int) : String {
        return DecimalFormat("#,###").format(number)
    }
}