package com.black.core.service

import android.app.Notification
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.black.core.service.ServiceActivity.Companion.toStartServiceActivityIntent
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment

/** [ServiceActivity.createServiceIntent]의 시작 대상 검증과 전달 extras 한정 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ServiceActivityTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()

    /**
     * BaseForegroundService 하위 클래스 중지 인텐트는 대상과 EXTRA_STOP만 담아 전달하는지 검증
     *
     * Given: 다른 extras가 섞인 중지 인텐트를 ServiceActivity 인텐트로 변환
     * When: createServiceIntent 호출
     * Then: 대상 component가 같고 EXTRA_STOP만 true로 담김
     */
    @Test
    fun test_01_stopIntentForwardsOnlyStopExtra() = runTest {
        /** Given **/
        val source = BaseForegroundService.createStopIntent(context, TestForegroundService::class.java)
            .putExtra("other", "value")
            .toStartServiceActivityIntent()

        /** When **/
        val serviceIntent = ServiceActivity.createServiceIntent(context, source)

        /** Then **/
        assertNotNull(serviceIntent)
        assertEquals(ComponentName(context, TestForegroundService::class.java), serviceIntent!!.component)
        assertTrue(serviceIntent.getBooleanExtra(BaseForegroundService.EXTRA_STOP, false))
        assertEquals(setOf(BaseForegroundService.EXTRA_STOP), serviceIntent.extras?.keySet())
    }

    /**
     * 시작 인텐트는 extras 없이 대상만 담는지 검증
     *
     * Given: EXTRA_STOP 없는 시작 인텐트를 ServiceActivity 인텐트로 변환
     * When: createServiceIntent 호출
     * Then: 대상 component가 같고 EXTRA_STOP이 없음
     */
    @Test
    fun test_02_startIntentHasNoStopExtra() = runTest {
        /** Given **/
        val source = Intent(context, TestForegroundService::class.java)
            .toStartServiceActivityIntent()

        /** When **/
        val serviceIntent = ServiceActivity.createServiceIntent(context, source)

        /** Then **/
        assertEquals(ComponentName(context, TestForegroundService::class.java), serviceIntent?.component)
        assertFalse(serviceIntent!!.hasExtra(BaseForegroundService.EXTRA_STOP))
    }

    /**
     * BaseForegroundService 하위 클래스가 아닌 서비스는 시작하지 않는지 검증
     *
     * Given: 일반 Service 하위 클래스를 가리키는 인텐트
     * When: createServiceIntent 호출
     * Then: null 반환
     */
    @Test
    fun test_03_nonForegroundServiceIsRejected() = runTest {
        /** Given **/
        val source = Intent(context, TestPlainService::class.java)
            .toStartServiceActivityIntent()

        /** When **/
        val serviceIntent = ServiceActivity.createServiceIntent(context, source)

        /** Then **/
        assertNull(serviceIntent)
    }

    /**
     * 클래스 이름이 없거나 존재하지 않는 클래스면 시작하지 않는지 검증
     *
     * Given: 클래스 이름 extra가 없는 인텐트, 존재하지 않는 클래스를 가리키는 인텐트
     * When: 각각 createServiceIntent 호출
     * Then: 모두 null 반환
     */
    @Test
    fun test_04_missingOrUnknownClassIsRejected() = runTest {
        /** Given **/
        val noClassName = Intent()
        val unknownClass = Intent()
            .setComponent(ComponentName(context, "com.black.core.service.UnknownService"))
            .toStartServiceActivityIntent()

        /** When **/
        val noClassNameResult = ServiceActivity.createServiceIntent(context, noClassName)
        val unknownClassResult = ServiceActivity.createServiceIntent(context, unknownClass)

        /** Then **/
        assertNull(noClassNameResult)
        assertNull(unknownClassResult)
    }

    class TestForegroundService : BaseForegroundService() {
        override val type: Int = 0
        override val notificationId: Int = 1
        override fun onCreateNotification(): Notification = Notification()
    }

    class TestPlainService : Service() {
        override fun onBind(intent: Intent?): IBinder? = null
    }
}
