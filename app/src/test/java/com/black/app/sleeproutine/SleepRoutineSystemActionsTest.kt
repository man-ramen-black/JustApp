package com.black.app.sleeproutine

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.black.app.service.AppAccessibilityService
import com.black.core.service.GlobalActionRequest
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [SleepRoutineSystemActions.lockScreen]의 접근성 서비스 켜짐·API 수준별 화면 잠금 요청 전송 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SleepRoutineSystemActionsTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun enableAccessibilityService() {
        val serviceName = ComponentName(context, AppAccessibilityService::class.java).flattenToString()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, serviceName)
    }

    /**
     * 접근성 서비스가 꺼져 있으면 요청을 보내지 않고 false를 반환하는지 검증
     *
     * Given: SDK 34, 접근성 서비스 꺼짐
     * When: lockScreen 호출
     * Then: false, 브로드캐스트 없음
     */
    @Test
    @Config(sdk = [34])
    fun test_01_lockScreenReturnsFalseWhenAccessibilityServiceDisabled() = runTest {
        /** Given **/
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        val actions = SleepRoutineSystemActions(context)

        /** When **/
        val locked = actions.lockScreen()

        /** Then **/
        assertFalse(locked)
        assertTrue(shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents.isEmpty())
    }

    /**
     * 접근성 서비스가 켜져 있으면 앱 패키지로 한정한 화면 잠금 요청을 보내는지 검증
     *
     * Given: SDK 34, 접근성 서비스 켜짐
     * When: lockScreen 호출
     * Then: true, 화면 잠금 요청 인텐트 1건(앱 패키지 지정)
     */
    @Test
    @Config(sdk = [34])
    fun test_02_lockScreenBroadcastsRequestWhenAccessibilityServiceEnabled() = runTest {
        /** Given **/
        enableAccessibilityService()
        val actions = SleepRoutineSystemActions(context)

        /** When **/
        val locked = actions.lockScreen()

        /** Then **/
        assertTrue(locked)
        val intents = shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents
        assertEquals(1, intents.size)
        assertEquals(GlobalActionRequest.createLockScreenIntent(context).action, intents[0].action)
        assertEquals(context.packageName, intents[0].`package`)
    }

    /**
     * 화면 잠금 전역 동작이 없는 API 28 미만에서는 요청을 보내지 않고 false를 반환하는지 검증
     *
     * Given: SDK 25, 접근성 서비스 켜짐
     * When: lockScreen 호출
     * Then: false, 브로드캐스트 없음
     */
    @Test
    @Config(sdk = [25])
    fun test_03_lockScreenReturnsFalseBelowApi28() = runTest {
        /** Given **/
        enableAccessibilityService()
        val actions = SleepRoutineSystemActions(context)

        /** When **/
        val locked = actions.lockScreen()

        /** Then **/
        assertFalse(locked)
        assertTrue(shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents.isEmpty())
    }
}
