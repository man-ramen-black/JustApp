package com.black.core.service

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.os.Looper
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [GlobalActionRequest]의 뒤로 가기·화면 잠금 요청 인텐트와, 등록한 리시버가 요청을 접근성 서비스 전역 동작으로 실행하는지 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GlobalActionRequestTest : BaseTest() {

    private val application: Application = RuntimeEnvironment.getApplication()

    /** 리시버 등록에 애플리케이션 컨텍스트를 쓰고 전역 동작 호출만 기록하는 접근성 서비스 */
    private val service: AccessibilityService = mockk(relaxed = true)

    init {
        // mockk { } 블록 안에서는 application이 Service.getApplication()으로 해석돼 블록 밖에서 지정
        every { service.applicationContext } returns application
    }

    /**
     * 뒤로 가기 요청 인텐트가 이 앱에만 전달되도록 패키지를 지정하는지 검증
     *
     * Given: 애플리케이션 컨텍스트
     * When: createBackIntent 호출
     * Then: 인텐트 패키지가 앱 패키지
     */
    @Test
    fun test_01_backIntentTargetsOwnPackage() = runTest {
        /** Given / When **/
        val intent = GlobalActionRequest.createBackIntent(application)

        /** Then **/
        assertEquals(application.packageName, intent.`package`)
    }

    /**
     * 등록한 리시버가 뒤로 가기 요청을 받으면 전역 뒤로 가기를 실행하는지 검증
     *
     * Given: 접근성 서비스에 등록한 요청 리시버
     * When: 뒤로 가기 요청 브로드캐스트 전송
     * Then: performGlobalAction(GLOBAL_ACTION_BACK) 1회
     */
    @Test
    fun test_02_receiverPerformsBackOnRequest() = runTest {
        /** Given **/
        val receiver = GlobalActionRequest.createReceiver(service)
        receiver.register(service)

        /** When **/
        application.sendBroadcast(GlobalActionRequest.createBackIntent(application))
        shadowOf(Looper.getMainLooper()).idle()

        /** Then **/
        verify(exactly = 1) { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        receiver.unregister(service)
    }

    /**
     * 해제한 리시버는 요청을 받아도 전역 동작을 실행하지 않는지 검증
     *
     * Given: 등록 후 해제한 요청 리시버
     * When: 뒤로 가기 요청 브로드캐스트 전송
     * Then: performGlobalAction 호출 없음
     */
    @Test
    fun test_03_unregisteredReceiverIgnoresRequest() = runTest {
        /** Given **/
        val receiver = GlobalActionRequest.createReceiver(service)
        receiver.register(service)
        receiver.unregister(service)

        /** When **/
        application.sendBroadcast(GlobalActionRequest.createBackIntent(application))
        shadowOf(Looper.getMainLooper()).idle()

        /** Then **/
        verify(exactly = 0) { service.performGlobalAction(any()) }
    }

    /**
     * 화면 잠금 요청 인텐트가 이 앱에만 전달되도록 패키지를 지정하는지 검증
     *
     * Given: 애플리케이션 컨텍스트
     * When: createLockScreenIntent 호출
     * Then: 인텐트 패키지가 앱 패키지, 액션은 뒤로 가기 요청과 다름
     */
    @Test
    fun test_04_lockScreenIntentTargetsOwnPackage() = runTest {
        /** Given / When **/
        val intent = GlobalActionRequest.createLockScreenIntent(application)

        /** Then **/
        assertEquals(application.packageName, intent.`package`)
        assertNotEquals(GlobalActionRequest.createBackIntent(application).action, intent.action)
    }

    /**
     * 등록한 리시버가 화면 잠금 요청을 받으면 전역 화면 잠금을 실행하는지 검증
     *
     * Given: API 34, 접근성 서비스에 등록한 요청 리시버
     * When: 화면 잠금 요청 브로드캐스트 전송
     * Then: performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) 1회, 뒤로 가기는 실행하지 않음
     */
    @Test
    fun test_05_receiverPerformsLockScreenOnRequest() = runTest {
        /** Given **/
        val receiver = GlobalActionRequest.createReceiver(service)
        receiver.register(service)

        /** When **/
        application.sendBroadcast(GlobalActionRequest.createLockScreenIntent(application))
        shadowOf(Looper.getMainLooper()).idle()

        /** Then **/
        verify(exactly = 1) { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }
        verify(exactly = 0) { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        receiver.unregister(service)
    }

    /**
     * 화면 잠금 전역 동작이 없는 API 28 미만에서는 화면 잠금 요청을 받아도 전역 동작을 실행하지 않는지 검증
     *
     * Given: API 25, 접근성 서비스에 등록한 요청 리시버
     * When: 화면 잠금 요청 브로드캐스트 전송, 이어서 뒤로 가기 요청 브로드캐스트 전송
     * Then: 화면 잠금 요청 뒤 performGlobalAction 호출 없음, 뒤로 가기 요청 뒤 performGlobalAction(GLOBAL_ACTION_BACK)만 1회
     */
    @Test
    @Config(sdk = [25])
    fun test_06_receiverSkipsLockScreenBelowApi28() = runTest {
        /** Given **/
        // API 33 미만에서 ContextCompat가 앱 내부 전용 리시버 등록·수신에 요구하는 권한, 실제 앱은 androidx core가 병합 매니페스트에 선언
        shadowOf(application).grantPermissions("${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val receiver = GlobalActionRequest.createReceiver(service)
        receiver.register(service)

        /** When **/
        application.sendBroadcast(GlobalActionRequest.createLockScreenIntent(application))
        shadowOf(Looper.getMainLooper()).idle()

        /** Then **/
        verify(exactly = 0) { service.performGlobalAction(any()) }

        /** When **/
        application.sendBroadcast(GlobalActionRequest.createBackIntent(application))
        shadowOf(Looper.getMainLooper()).idle()

        /** Then **/
        verify(exactly = 1) {
            service.performGlobalAction(any())
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        receiver.unregister(service)
    }
}
