package com.black.code.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.WindowManager
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*

object Util {
    private val RANDOM_CHAR_LIST : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    fun pxToDp(context: Context, px: Int) : Float {
        return px.toFloat() / context.resources.displayMetrics.density
    }

    fun dpToPx(context: Context, dp: Float) : Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
    }

    fun vibrateOneShot(context: Context, milliseconds: Long) {
        val vibrator : Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(milliseconds)
        }
    }

    /**
     * https://myksb1223.github.io/develop_diary/2019/03/28/Screen-size-in-Android.html
     */
    fun getScreenSize(context: Context) : Point {
        val metrics = context.resources.displayMetrics
        return Point(metrics.widthPixels, metrics.heightPixels)
    }

    fun turnScreenOn(activity: Activity) {
        with(activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
            } else {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
    }

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
}