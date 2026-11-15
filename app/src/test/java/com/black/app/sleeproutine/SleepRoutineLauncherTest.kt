package com.black.app.sleeproutine

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Shadows.shadowOf

/** [SleepRoutineLauncher]의 Intent 생성과 수면 기록 실행 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SleepRoutineLauncherTest : BaseTest() {

    private val sleepComponent = ComponentName(
        "com.urbandroid.sleep",
        "com.urbandroid.sleep.SleepTrackStartHelperActivity",
    )

    /**
     * 수면 기록 Intent 컴포넌트 검증
     *
     * Given: 없음
     * When: createSleepTrackingIntent 호출
     * Then: Sleep as Android 시작 Activity 컴포넌트
     */
    @Test
    fun test_01_sleepTrackingIntentComponent() = runTest {
        /** Given / When **/
        val intent = SleepRoutineLauncher.createSleepTrackingIntent()

        /** Then **/
        assertEquals(sleepComponent, intent.component)
    }

    /**
     * YouTube Intent 구성 검증
     *
     * Given: 앞뒤 공백이 있는 URL
     * When: 패키지 지정·미지정으로 createYoutubeIntent 호출
     * Then: ACTION_VIEW, 공백 제거된 data, 패키지는 지정 여부대로
     */
    @Test
    fun test_02_youtubeIntent() = runTest {
        /** Given / When **/
        val withPackage = SleepRoutineLauncher.createYoutubeIntent(
            " https://youtu.be/abc ",
            YoutubePlaybackController.YOUTUBE_PACKAGE,
        )
        val withoutPackage = SleepRoutineLauncher.createYoutubeIntent(" https://youtu.be/abc ", null)

        /** Then **/
        assertEquals(Intent.ACTION_VIEW, withPackage.action)
        assertEquals("https://youtu.be/abc", withPackage.dataString)
        assertEquals(YoutubePlaybackController.YOUTUBE_PACKAGE, withPackage.`package`)
        assertNull(withoutPackage.`package`)
    }

    /**
     * 홈 Intent 구성 검증
     *
     * Given: 없음
     * When: createHomeIntent 호출
     * Then: ACTION_MAIN, CATEGORY_HOME
     */
    @Test
    fun test_03_homeIntent() = runTest {
        /** Given / When **/
        val intent = SleepRoutineLauncher.createHomeIntent()

        /** Then **/
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_HOME))
    }

    /**
     * startSleepTracking이 수면 기록 Activity를 새 태스크로 실행하는지 검증
     *
     * Given: Activity 존재 확인을 켜고 수면 기록 Activity를 등록한 Application Context의 Launcher
     * When: startSleepTracking 호출
     * Then: true, 시작된 Intent가 수면 기록 컴포넌트이며 NEW_TASK 포함
     */
    @Test
    fun test_04_startSleepTrackingLaunchesActivity() = runTest {
        /** Given **/
        val application = ApplicationProvider.getApplicationContext<Context>() as Application
        shadowOf(application).checkActivities(true)
        shadowOf(application.packageManager).addActivityIfNotPresent(sleepComponent)
        val launcher = SleepRoutineLauncher(application)

        /** When **/
        val result = launcher.startSleepTracking()

        /** Then **/
        val started = shadowOf(application).nextStartedActivity
        assertTrue(result)
        assertEquals(sleepComponent, started.component)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /**
     * Sleep as Android가 없으면 startSleepTracking이 false인지 검증
     *
     * Given: Activity 존재 확인을 켜고 수면 기록 Activity는 등록하지 않음
     * When: startSleepTracking 호출
     * Then: false, 시작된 Activity 없음
     */
    @Test
    fun test_05_startSleepTrackingFalseWithoutActivity() = runTest {
        /** Given **/
        val application = ApplicationProvider.getApplicationContext<Context>() as Application
        shadowOf(application).checkActivities(true)
        val launcher = SleepRoutineLauncher(application)

        /** When **/
        val result = launcher.startSleepTracking()

        /** Then **/
        assertFalse(result)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    /**
     * YouTube 앱이 있으면 YouTube 패키지 지정으로 여는지 검증
     *
     * Given: YouTube 패키지 지정 인텐트가 해석되는 환경
     * When: openYoutube 호출
     * Then: true, 시작된 Intent의 패키지가 YouTube
     */
    @Test
    fun test_06_openYoutubeUsesYoutubeAppWhenAvailable() = runTest {
        /** Given **/
        val application = ApplicationProvider.getApplicationContext<Context>() as Application
        shadowOf(application).checkActivities(true)
        registerResolvable(application, SleepRoutineLauncher.createYoutubeIntent(YOUTUBE_URL, YoutubePlaybackController.YOUTUBE_PACKAGE))
        val launcher = SleepRoutineLauncher(application)

        /** When **/
        val result = launcher.openYoutube(YOUTUBE_URL)

        /** Then **/
        assertTrue(result)
        assertEquals(YoutubePlaybackController.YOUTUBE_PACKAGE, shadowOf(application).nextStartedActivity.`package`)
    }

    /**
     * YouTube 앱이 없으면 패키지 없는 인텐트로 다시 여는지 검증
     *
     * Given: 패키지 없는 인텐트만 해석되고 YouTube 패키지 지정 인텐트는 해석되지 않는 환경
     * When: openYoutube 호출
     * Then: true, 시작된 Intent는 패키지 없는 ACTION_VIEW이며 한 번만 시작됨
     */
    @Test
    fun test_07_openYoutubeFallsBackToDefaultApp() = runTest {
        /** Given **/
        val application = ApplicationProvider.getApplicationContext<Context>() as Application
        shadowOf(application).checkActivities(true)
        registerResolvable(application, SleepRoutineLauncher.createYoutubeIntent(YOUTUBE_URL, null))
        val launcher = SleepRoutineLauncher(application)

        /** When **/
        val result = launcher.openYoutube(YOUTUBE_URL)

        /** Then **/
        val started = shadowOf(application).nextStartedActivity
        assertTrue(result)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertNull(started.`package`)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    /**
     * 열 수 있는 앱이 전혀 없으면 openYoutube가 false인지 검증
     *
     * Given: 어떤 인텐트도 해석되지 않는 환경
     * When: openYoutube 호출
     * Then: false, 시작된 Activity 없음
     */
    @Test
    fun test_08_openYoutubeFalseWhenNothingResolves() = runTest {
        /** Given **/
        val application = ApplicationProvider.getApplicationContext<Context>() as Application
        shadowOf(application).checkActivities(true)
        val launcher = SleepRoutineLauncher(application)

        /** When **/
        val result = launcher.openYoutube(YOUTUBE_URL)

        /** Then **/
        assertFalse(result)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    /** [intent]를 해석 가능한 Activity가 있는 것으로 등록 */
    private fun registerResolvable(application: Application, intent: Intent) {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = intent.`package` ?: "com.android.browser"
                name = "ResolvedActivity"
            }
        }
        shadowOf(application.packageManager).addResolveInfoForIntent(intent, resolveInfo)
    }

    private companion object {
        const val YOUTUBE_URL = "https://youtu.be/abc"
    }
}
