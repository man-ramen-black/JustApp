package com.black.core.system

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
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

/** [SystemActions]의 홈 화면 실행과 접근성 서비스 켜짐·API 수준별 뒤로 가기·화면 잠금 요청 전송 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SystemActionsTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()

    /** 앱 모듈이 선언하는 접근성 서비스를 core 테스트에서 대신 등록한 뒤 켜기 */
    private fun enableAccessibilityService() {
        val service = ComponentName(context.packageName, "${context.packageName}.TestAccessibilityService")
        shadowOf(context.packageManager).apply {
            addServiceIfNotPresent(service)
            addIntentFilterForService(service, IntentFilter(AccessibilityService.SERVICE_INTERFACE))
        }
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            service.flattenToString(),
        )
    }

    /** 활성 접근성 서비스 설정 비우기 */
    private fun disableAccessibilityService() {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
    }

    /**
     * 홈 Intent 구성 검증
     *
     * Given: 없음
     * When: createHomeIntent 호출
     * Then: ACTION_MAIN, CATEGORY_HOME
     */
    @Test
    fun test_01_homeIntent() = runTest {
        /** Given / When **/
        val intent = SystemActions.createHomeIntent()

        /** Then **/
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_HOME))
    }

    /**
     * Activity가 아닌 컨텍스트에서 새 태스크로 홈 화면을 여는지 검증
     *
     * Given: 애플리케이션 컨텍스트
     * When: goHome 호출
     * Then: true, ACTION_MAIN, CATEGORY_HOME, FLAG_ACTIVITY_NEW_TASK 포함 인텐트 시작
     */
    @Test
    fun test_02_goHomeStartsHomeInNewTask() = runTest {
        /** Given / When **/
        val started = SystemActions.goHome(context)

        /** Then **/
        assertTrue(started)
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_HOME))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /**
     * 접근성 서비스가 꺼져 있으면 뒤로 가기 요청을 보내지 않고 false를 반환하는지 검증
     *
     * Given: 접근성 서비스 꺼짐
     * When: requestBack 호출
     * Then: false, 브로드캐스트 없음
     */
    @Test
    fun test_03_requestBackReturnsFalseWhenAccessibilityServiceDisabled() = runTest {
        /** Given **/
        disableAccessibilityService()

        /** When **/
        val requested = SystemActions.requestBack(context)

        /** Then **/
        assertFalse(requested)
        assertTrue(shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents.isEmpty())
    }

    /**
     * 접근성 서비스가 켜져 있으면 앱 패키지로 한정한 뒤로 가기 요청을 보내는지 검증
     *
     * Given: 접근성 서비스 켜짐
     * When: requestBack 호출
     * Then: true, 뒤로 가기 요청 인텐트 1건(앱 패키지 지정)
     */
    @Test
    fun test_04_requestBackBroadcastsRequestWhenAccessibilityServiceEnabled() = runTest {
        /** Given **/
        enableAccessibilityService()

        /** When **/
        val requested = SystemActions.requestBack(context)

        /** Then **/
        assertTrue(requested)
        val intents = shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents
        assertEquals(1, intents.size)
        assertEquals(GlobalActionRequest.createBackIntent(context).action, intents[0].action)
        assertEquals(context.packageName, intents[0].`package`)
    }

    /**
     * 접근성 서비스가 꺼져 있으면 화면 잠금 요청을 보내지 않고 false를 반환하는지 검증
     *
     * Given: SDK 34, 접근성 서비스 꺼짐
     * When: requestLockScreen 호출
     * Then: false, 브로드캐스트 없음
     */
    @Test
    fun test_05_requestLockScreenReturnsFalseWhenAccessibilityServiceDisabled() = runTest {
        /** Given **/
        disableAccessibilityService()

        /** When **/
        val requested = SystemActions.requestLockScreen(context)

        /** Then **/
        assertFalse(requested)
        assertTrue(shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents.isEmpty())
    }

    /**
     * 접근성 서비스가 켜져 있으면 앱 패키지로 한정한 화면 잠금 요청을 보내는지 검증
     *
     * Given: SDK 34, 접근성 서비스 켜짐
     * When: requestLockScreen 호출
     * Then: true, 화면 잠금 요청 인텐트 1건(앱 패키지 지정)
     */
    @Test
    fun test_06_requestLockScreenBroadcastsRequestWhenAccessibilityServiceEnabled() = runTest {
        /** Given **/
        enableAccessibilityService()

        /** When **/
        val requested = SystemActions.requestLockScreen(context)

        /** Then **/
        assertTrue(requested)
        val intents = shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents
        assertEquals(1, intents.size)
        assertEquals(GlobalActionRequest.createLockScreenIntent(context).action, intents[0].action)
        assertEquals(context.packageName, intents[0].`package`)
    }

    /**
     * 화면 잠금 전역 동작이 없는 API 28 미만에서는 요청을 보내지 않고 false를 반환하는지 검증
     *
     * Given: SDK 25, 접근성 서비스 켜짐
     * When: requestLockScreen 호출
     * Then: false, 브로드캐스트 없음
     */
    @Test
    @Config(sdk = [25])
    fun test_07_requestLockScreenReturnsFalseBelowApi28() = runTest {
        /** Given **/
        enableAccessibilityService()

        /** When **/
        val requested = SystemActions.requestLockScreen(context)

        /** Then **/
        assertFalse(requested)
        assertTrue(shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents.isEmpty())
    }
}
