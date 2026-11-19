package com.black.core.system

import android.app.NotificationManager
import android.content.Context
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [DoNotDisturbController]의 방해 금지 켜기·끄기 동작 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DoNotDisturbControllerTest : BaseTest() {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var controller: DoNotDisturbController

    @Before
    fun setupController() {
        context = RuntimeEnvironment.getApplication()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        controller = DoNotDisturbController(context)
    }

    /**
     * 접근 권한이 있으면 켜기·끄기가 필터에 반영되는지 검증
     *
     * Given: 방해 금지 접근 허용
     * When: enable 후 disable 호출하며 각 시점의 필터 기록
     * Then: enable은 true와 PRIORITY, disable 뒤는 ALL
     */
    @Test
    fun test_01_enableAndDisableWithAccess() = runTest {
        /** Given **/
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)

        /** When **/
        val enabled = controller.enable()
        val filterAfterEnable = notificationManager.currentInterruptionFilter
        controller.disable()
        val filterAfterDisable = notificationManager.currentInterruptionFilter

        /** Then **/
        assertTrue(enabled)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, filterAfterEnable)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, filterAfterDisable)
    }

    /**
     * 접근 권한이 없으면 켜기가 실패하고 필터가 바뀌지 않는지 검증
     *
     * Given: 방해 금지 접근 없음, 현재 필터 기록
     * When: enable 호출
     * Then: false, 필터 변화 없음
     */
    @Test
    fun test_02_noAccessEnableDoesNothing() = runTest {
        /** Given **/
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(false)
        val before = notificationManager.currentInterruptionFilter

        /** When **/
        val enabled = controller.enable()
        val after = notificationManager.currentInterruptionFilter

        /** Then **/
        assertFalse(enabled)
        assertEquals(before, after)
    }

    /**
     * 접근 권한이 없으면 끄기가 필터를 바꾸지 않는지 검증
     *
     * Given: 접근 허용 상태에서 필터를 PRIORITY로 만든 뒤 접근 해제
     * When: disable 호출
     * Then: 필터가 PRIORITY로 유지됨
     */
    @Test
    fun test_03_noAccessDisableKeepsFilter() = runTest {
        /** Given **/
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        controller.enable()
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(false)

        /** When **/
        controller.disable()
        val after = notificationManager.currentInterruptionFilter

        /** Then **/
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, after)
    }
}
