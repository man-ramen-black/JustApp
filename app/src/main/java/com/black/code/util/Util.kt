package com.black.code.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.*
import java.security.SecureRandom
import java.text.DecimalFormat
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

    fun withComma(number: Int) : String {
        return DecimalFormat("#,###").format(number)
    }

    /**
     * 클립보드 텍스트 반환
     */
    fun getClipData(context: Context): String? {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboardManager.hasPrimaryClip()) {
            return null
        }

        val clipData = clipboardManager.primaryClip ?: run {
            return null
        }

        if (clipData.itemCount <= 0) {
            return null
        }

        return clipData.getItemAt(0).text?.toString()
    }

    /**
     * 텍스트 공유
     */
    fun share(context: Context, text: String){
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            if(context !is Activity){
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        try {
            context.startActivity(Intent.createChooser(intent, null))
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }
    }

    /**
     * 몰입 모드(전체 화면, 상단바, 하단바 숨김) 설정
     */
    fun setImmersiveMode(window: Window?, isActivated: Boolean) {
        window ?: run {
            Log.w("window is null")
            return
        }

        // R 이상
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.decorView.windowInsetsController

            // 상단바, 하단바 노출 설정
            if (isActivated) {
                controller?.hide(WindowInsets.Type.statusBars())
                controller?.hide(WindowInsets.Type.navigationBars())

                // 호출하지 않을 경우 컷아웃 영역까지 확장되지 않고, cutout inset이 onApplyWindowInsets으로 들어오지 않음
                window.setDecorFitsSystemWindows(false)
                // immersiveMode 설정 시 시스템바 노출 규칙 적용
                // SYSTEM_UI_FLAG_IMMERSIVE_STICKY 와 동일한 옵션 (가장자리 스와이프 시 잠깐 노출)
                controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            } else {
                controller?.show(WindowInsets.Type.statusBars())
                controller?.show(WindowInsets.Type.navigationBars())

            }
        }
        // R 미만
        else {
            if (isActivated) {
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            } else {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    /**
     * Cutout 설정
     * @param cutoutMode ex) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
     */
    fun setCutout(window: Window?, cutoutMode: Int) {
        window ?: run {
            Log.w("window is null")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }

        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = cutoutMode
        }
    }
}