package com.black.app.sleeproutine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.black.core.util.Util

/**
 * 수면 기록·YouTube·홈 화면 실행
 *
 * 서비스에서 다른 앱 화면을 띄우므로 Android 10 이상은 다른 앱 위에 표시 권한(백그라운드 실행 제한 예외)이 있어야 실제로 뜸.
 * 제한으로 막힌 실행은 예외 없이 무시되어 성공으로 반환됨
 */
class SleepRoutineLauncher(private val context: Context) {
    companion object {
        const val SLEEP_PACKAGE = "com.urbandroid.sleep"

        /** Tasker SleepTracking Task가 수면 기록 시작에 쓰던 Sleep as Android Activity */
        const val SLEEP_TRACK_START_ACTIVITY = "com.urbandroid.sleep.SleepTrackStartHelperActivity"

        fun createSleepTrackingIntent(): Intent =
            Intent().setComponent(ComponentName(SLEEP_PACKAGE, SLEEP_TRACK_START_ACTIVITY))

        /**
         * [url]을 여는 인텐트 생성
         *
         * [packageName]이 null이면 기본 앱(브라우저 등)으로 열림
         */
        fun createYoutubeIntent(url: String, packageName: String?): Intent =
            Intent(Intent.ACTION_VIEW, url.trim().toUri()).apply {
                if (packageName != null) setPackage(packageName)
            }

        fun createHomeIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    }

    /**
     * 수면 기록 시작
     *
     * Sleep as Android가 없으면 false
     */
    fun startSleepTracking(): Boolean = Util.startActivitySafely(context, createSleepTrackingIntent())

    /**
     * [url]을 YouTube 앱으로 열기
     *
     * 앱이 없으면 기본 앱으로 열고 그것도 실패하면 false
     */
    fun openYoutube(url: String): Boolean =
        Util.startActivitySafely(context, createYoutubeIntent(url, YoutubePlaybackController.YOUTUBE_PACKAGE)) ||
            Util.startActivitySafely(context, createYoutubeIntent(url, null))

    fun goHome(): Boolean = Util.startActivitySafely(context, createHomeIntent())
}
