package com.black.core.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.FileUriExposedException
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** [Util.startActivitySafely]의 NEW_TASK 지정, 시작 실패 처리와 [Util.startDeepLink] 위임 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UtilTest : BaseTest() {

    private val application: Application = RuntimeEnvironment.getApplication()

    /**
     * 시작에 성공하면 true를 돌려주고 받은 인텐트를 그대로 시작하는지 검증
     *
     * Given: 애플리케이션 컨텍스트, 액션과 데이터가 있는 인텐트
     * When: startActivitySafely 호출
     * Then: true 반환, 시작된 인텐트의 액션과 데이터가 같음
     */
    @Test
    fun test_01_returnsTrueAndStartsIntent() = runTest {
        /** Given **/
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))

        /** When **/
        val started = Util.startActivitySafely(application, intent)

        /** Then **/
        assertTrue(started)
        val startedIntent = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertEquals("https://example.com", startedIntent.dataString)
    }

    /**
     * Activity가 아닌 컨텍스트면 NEW_TASK를 더하고 호출부가 넣은 플래그는 유지하는지 검증
     *
     * Given: 애플리케이션 컨텍스트, FLAG_ACTIVITY_CLEAR_TOP을 넣은 인텐트
     * When: startActivitySafely 호출
     * Then: 시작된 인텐트에 FLAG_ACTIVITY_NEW_TASK와 FLAG_ACTIVITY_CLEAR_TOP 모두 포함
     */
    @Test
    fun test_02_addsNewTaskForNonActivityContext() = runTest {
        /** Given **/
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        /** When **/
        Util.startActivitySafely(application, intent)

        /** Then **/
        val flags = shadowOf(application).nextStartedActivity.flags
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP, flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    /**
     * Activity 컨텍스트면 NEW_TASK를 더하지 않는지 검증
     *
     * Given: Robolectric으로 띄운 Activity, 플래그 없는 인텐트
     * When: startActivitySafely 호출
     * Then: true 반환, 시작된 인텐트에 FLAG_ACTIVITY_NEW_TASK 없음
     */
    @Test
    fun test_03_keepsFlagsForActivityContext() = runTest {
        /** Given **/
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))

        /** When **/
        val started = Util.startActivitySafely(activity, intent)

        /** Then **/
        assertTrue(started)
        val startedIntent = shadowOf(activity).nextStartedActivity
        assertEquals(0, startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * 시작 대상이 없어 ActivityNotFoundException이 나면 false를 돌려주는지 검증
     *
     * Given: 등록된 Activity만 시작하도록 검사를 켠 애플리케이션, 받을 Activity가 없는 액션의 인텐트
     * When: startActivitySafely 호출
     * Then: 예외 없이 false 반환, 시작된 인텐트 없음
     */
    @Test
    fun test_04_returnsFalseWhenActivityNotFound() = runTest {
        /** Given **/
        shadowOf(application).checkActivities(true)
        val intent = Intent("com.black.core.test.action.NOT_FOUND")

        /** When **/
        val started = Util.startActivitySafely(application, intent)

        /** Then **/
        assertFalse(started)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    /**
     * 대상이 권한을 요구해 SecurityException이 나면 false를 돌려주는지 검증
     *
     * Given: startActivity가 SecurityException을 던지는 컨텍스트
     * When: startActivitySafely 호출
     * Then: 예외 없이 false 반환, startActivity 1회 호출
     */
    @Test
    fun test_05_returnsFalseWhenSecurityException() = runTest {
        /** Given **/
        val context: Context = mockk()
        every { context.startActivity(any()) } throws SecurityException("Permission Denial")
        val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")

        /** When **/
        val started = Util.startActivitySafely(context, intent)

        /** Then **/
        assertFalse(started)
        verify(exactly = 1) { context.startActivity(any()) }
    }

    /**
     * 외부에서 받은 인텐트에 file:// 데이터가 실려 FileUriExposedException이 나면 false를 돌려주는지 검증
     *
     * Given: startActivity가 FileUriExposedException을 던지는 컨텍스트, file:// 데이터를 가진 인텐트
     * When: startActivitySafely 호출
     * Then: 예외 없이 false 반환, startActivity 1회 호출
     */
    @Test
    fun test_06_returnsFalseWhenFileUriExposed() = runTest {
        /** Given **/
        val context: Context = mockk()
        every { context.startActivity(any()) } throws FileUriExposedException("file:///sdcard/a.txt exposed beyond app")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("file:///sdcard/a.txt"))

        /** When **/
        val started = Util.startActivitySafely(context, intent)

        /** Then **/
        assertFalse(started)
        verify(exactly = 1) { context.startActivity(any()) }
    }

    /**
     * 딥링크를 ACTION_VIEW 인텐트로 바꿔 startActivitySafely 처리대로 시작하는지 검증
     *
     * Given: 애플리케이션 컨텍스트, market 딥링크 문자열
     * When: startDeepLink 호출
     * Then: 시작된 인텐트가 ACTION_VIEW, data가 딥링크, FLAG_ACTIVITY_NEW_TASK 포함
     */
    @Test
    fun test_07_startDeepLinkStartsViewIntent() = runTest {
        /** Given **/
        val deepLink = "market://details?id=com.black.app"

        /** When **/
        Util.startDeepLink(application, deepLink)

        /** Then **/
        val startedIntent = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertEquals(deepLink, startedIntent.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * 나열하지 않은 런타임 예외(제조사 설정 앱 등)로 실패해도 false를 돌려주는지 검증
     *
     * Given: startActivity가 IllegalArgumentException을 던지는 컨텍스트
     * When: startActivitySafely 호출
     * Then: 예외 없이 false 반환, startActivity 1회 호출
     */
    @Test
    fun test_08_returnsFalseWhenOtherRuntimeException() = runTest {
        /** Given **/
        val context: Context = mockk()
        every { context.startActivity(any()) } throws IllegalArgumentException("unknown extra")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))

        /** When **/
        val started = Util.startActivitySafely(context, intent)

        /** Then **/
        assertFalse(started)
        verify(exactly = 1) { context.startActivity(any()) }
    }
}
