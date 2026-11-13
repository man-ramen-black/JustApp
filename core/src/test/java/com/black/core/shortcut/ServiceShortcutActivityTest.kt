package com.black.core.shortcut

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.black.core.service.BaseForegroundService
import com.black.core.service.ServiceActivity
import com.black.core.service.ServiceActivityTarget
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment

/** [ServiceShortcutActivity.createShortcutIntent]의 ServiceActivity 경유 바로가기 인텐트 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ServiceShortcutActivityTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()

    /**
     * 시작 서비스 인텐트를 ACTION_VIEW를 단 ServiceActivity 인텐트로 바꾸고 ServiceActivity가 같은 서비스 시작 인텐트로 되돌리는지 검증
     *
     * Given: 서비스 시작 인텐트
     * When: createShortcutIntent 호출 후 ServiceActivity.createServiceIntent로 변환
     * Then: 바로가기 인텐트 action ACTION_VIEW·대상 ServiceActivity, 변환 결과 대상 서비스·EXTRA_STOP 없음
     */
    @Test
    fun test_01_startShortcutIntentTargetsServiceActivity() = runTest {
        /** Given **/
        val serviceIntent = Intent(context, TestForegroundService::class.java)

        /** When **/
        val shortcutIntent = ServiceShortcutActivity.createShortcutIntent(serviceIntent)
        val forwardedIntent = ServiceActivity.createServiceIntent(context, shortcutIntent)

        /** Then **/
        assertEquals(Intent.ACTION_VIEW, shortcutIntent.action)
        assertEquals(ServiceActivity::class.java.name, shortcutIntent.component?.className)
        assertEquals(ComponentName(context, TestForegroundService::class.java), forwardedIntent?.component)
        assertFalse(forwardedIntent!!.hasExtra(BaseForegroundService.EXTRA_STOP))
    }

    /**
     * 중지 인텐트로 만든 바로가기 인텐트가 ServiceActivity를 거쳐 중지 extra를 전달하는지 검증
     *
     * Given: 서비스 중지 인텐트
     * When: createShortcutIntent 호출 후 ServiceActivity.createServiceIntent로 변환
     * Then: 바로가기 인텐트 action ACTION_VIEW, 변환 결과 대상 서비스·EXTRA_STOP true
     */
    @Test
    fun test_02_stopShortcutIntentForwardsStopExtra() = runTest {
        /** Given **/
        val stopIntent = BaseForegroundService.createStopIntent(context, TestForegroundService::class.java)

        /** When **/
        val shortcutIntent = ServiceShortcutActivity.createShortcutIntent(stopIntent)
        val forwardedIntent = ServiceActivity.createServiceIntent(context, shortcutIntent)

        /** Then **/
        assertEquals(Intent.ACTION_VIEW, shortcutIntent.action)
        assertEquals(ComponentName(context, TestForegroundService::class.java), forwardedIntent?.component)
        assertTrue(forwardedIntent!!.getBooleanExtra(BaseForegroundService.EXTRA_STOP, false))
    }

    class TestForegroundService : BaseForegroundService(), ServiceActivityTarget {
        override val type: Int = 0
        override val notificationId: Int = 1
        override fun onCreateNotification(): Notification = Notification()
    }
}
