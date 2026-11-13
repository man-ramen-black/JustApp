package com.black.core.broadcast

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

/** [ReceiverRegistration]의 applicationContext 등록, 중복 등록 방지, 해제와 재등록 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReceiverRegistrationTest : BaseTest() {

    companion object {
        private const val TEST_ACTION = "com.black.core.broadcast.TEST_ACTION"
    }

    private val application: Application = RuntimeEnvironment.getApplication()

    /** 수신한 action 기록 */
    private val receivedActions = mutableListOf<String?>()

    private val registration = ReceiverRegistration(IntentFilter(TEST_ACTION)) { _, intent ->
        receivedActions.add(intent.action)
    }

    /** 테스트 간 등록 상태 격리를 위한 각 테스트 후 해제 */
    @After
    fun unregisterReceiver() {
        registration.unregister(application)
    }

    /**
     * 콜백 홀더를 등록하면 applicationContext에 앱 내부 전용으로 등록돼 브로드캐스트를 받는지 검증
     *
     * Given: applicationContext 외 호출은 실패하는 Context
     * When: 그 Context로 register 후 앱 자신에게 브로드캐스트 전송
     * Then: 예외 없이 등록 상태, RECEIVER_NOT_EXPORTED로 등록된 리시버 1개, 콜백 1회 수신
     */
    @Test
    fun test_01_registerUsesApplicationContextAsNotExported() = runTest {
        /** Given **/
        val context = applicationOnlyContext()

        /** When **/
        registration.register(context)
        sendTestBroadcast()

        /** Then **/
        assertTrue(registration.isRegistered)
        val registered = registeredTestReceivers().single()
        assertTrue(registered.flags and Context.RECEIVER_NOT_EXPORTED != 0)
        assertEquals(listOf(TEST_ACTION), receivedActions)
    }

    /**
     * 해제 없이 다시 등록하면 등록을 하나로 유지해 한 번만 받는지 검증
     *
     * Given / When: register 2회 호출 후 브로드캐스트 전송
     * Then: 등록된 리시버 1개, 콜백 1회 수신
     */
    @Test
    fun test_02_registerTwiceKeepsSingleRegistration() = runTest {
        /** Given / When **/
        registration.register(application)
        registration.register(application)
        sendTestBroadcast()

        /** Then **/
        assertEquals(1, registeredTestReceivers().size)
        assertEquals(listOf(TEST_ACTION), receivedActions)
    }

    /**
     * 해제하면 등록이 사라지고 더 받지 않는지 검증
     *
     * Given: 등록
     * When: applicationContext 외 호출은 실패하는 Context로 unregister 후 브로드캐스트 전송
     * Then: 미등록 상태, 등록된 리시버 없음, 수신 없음
     */
    @Test
    fun test_03_unregisterStopsReceiving() = runTest {
        /** Given **/
        registration.register(application)

        /** When **/
        registration.unregister(applicationOnlyContext())
        sendTestBroadcast()

        /** Then **/
        assertFalse(registration.isRegistered)
        assertTrue(registeredTestReceivers().isEmpty())
        assertTrue(receivedActions.isEmpty())
    }

    /**
     * 등록 없이 해제를 호출해도 예외가 없는지 검증
     *
     * Given: 등록 없음
     * When: unregister 호출
     * Then: 예외 없음, 미등록 상태
     */
    @Test
    fun test_04_unregisterWithoutRegisterDoesNothing() = runTest {
        /** Given / When **/
        registration.unregister(application)

        /** Then **/
        assertFalse(registration.isRegistered)
        assertTrue(registeredTestReceivers().isEmpty())
    }

    /**
     * 해제한 뒤 다시 등록하면 다시 받는지 검증
     *
     * Given: 등록 후 해제
     * When: 다시 register 후 브로드캐스트 전송
     * Then: 등록 상태, 콜백 1회 수신
     */
    @Test
    fun test_05_registerAgainAfterUnregister() = runTest {
        /** Given **/
        registration.register(application)
        registration.unregister(application)

        /** When **/
        registration.register(application)
        sendTestBroadcast()

        /** Then **/
        assertTrue(registration.isRegistered)
        assertEquals(listOf(TEST_ACTION), receivedActions)
    }

    /**
     * 리시버 인스턴스를 넘기면 그 인스턴스를 그대로 등록하는지 검증
     *
     * Given: 리시버 인스턴스로 만든 홀더
     * When: register 호출
     * Then: 등록된 리시버가 넘긴 인스턴스
     */
    @Test
    fun test_06_receiverInstanceIsRegistered() = runTest {
        /** Given **/
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {}
        }
        val instanceRegistration = ReceiverRegistration(IntentFilter(TEST_ACTION), receiver)

        /** When **/
        instanceRegistration.register(application)

        /** Then **/
        assertSame(receiver, registeredTestReceivers().single().broadcastReceiver)
        instanceRegistration.unregister(application)
    }

    /** applicationContext만 애플리케이션을 돌려주고 그 외 호출은 base가 없어 실패하는 Context(호출부 Context로 직접 등록·해제하지 않는지 확인용) */
    private fun applicationOnlyContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = application
    }

    /** 앱 자신에게 테스트 action 브로드캐스트 전송 후 메인 루퍼 처리 */
    private fun sendTestBroadcast() {
        application.sendBroadcast(Intent(TEST_ACTION).setPackage(application.packageName))
        idleMainLooper()
    }

    /** 테스트 action으로 런타임 등록된 리시버 목록 */
    private fun registeredTestReceivers(): List<ShadowApplication.Wrapper> =
        shadowOf(application).registeredReceivers
            .filter { it.intentFilter.hasAction(TEST_ACTION) }
}
