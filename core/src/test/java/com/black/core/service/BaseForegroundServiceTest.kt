package com.black.core.service

import android.app.Notification
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** [BaseForegroundService]의 onDestroy 재시작 훅 기본 동작과 재정의 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BaseForegroundServiceTest : BaseTest() {

    private val application = RuntimeEnvironment.getApplication()

    /** 재시작 훅을 재정의하지 않은 서비스 */
    class DefaultRestartService : BaseForegroundService() {
        override val type: Int = 0
        override val notificationId: Int = 1
        override fun onCreateNotification(): Notification = Notification()
    }

    /** 재시작 훅을 재정의해 호출 횟수만 세는 서비스 */
    class CustomRestartService : BaseForegroundService() {
        var restartCount = 0
            private set

        override val type: Int = 0
        override val notificationId: Int = 2
        override fun onCreateNotification(): Notification = Notification()

        override fun restartIfNeeded() {
            restartCount++
        }
    }

    /**
     * 중지 인텐트 없이 종료되면 같은 서비스를 다시 시작하는지 검증
     *
     * Given: 시작 명령을 받은 기본 서비스
     * When: 서비스 종료
     * Then: 같은 서비스 시작 인텐트 기록
     */
    @Test
    fun test_01_destroyRestartsWithoutStopIntent() = runTest {
        /** Given **/
        val controller = Robolectric.buildService(DefaultRestartService::class.java)
            .create()
            .startCommand(0, 1)

        /** When **/
        controller.destroy()

        /** Then **/
        val startedService = shadowOf(application).nextStartedService
        assertEquals(DefaultRestartService::class.java.name, startedService.component?.className)
    }

    /**
     * 중지 인텐트로 멈춘 뒤 종료되면 다시 시작하지 않는지 검증
     *
     * Given: EXTRA_STOP을 담은 시작 명령을 받은 기본 서비스
     * When: 서비스 종료
     * Then: 서비스 시작 인텐트 없음
     */
    @Test
    fun test_02_destroyAfterStopIntentDoesNotRestart() = runTest {
        /** Given **/
        val stopIntent = BaseForegroundService.createStopIntent(application, DefaultRestartService::class.java)
        val controller = Robolectric.buildService(DefaultRestartService::class.java, stopIntent)
            .create()
            .startCommand(0, 1)

        /** When **/
        controller.destroy()

        /** Then **/
        assertNull(shadowOf(application).nextStartedService)
    }

    /**
     * 재시작 훅을 재정의하면 onDestroy가 기본 재시작 대신 재정의한 훅을 부르는지 검증
     *
     * Given: 재시작 훅을 재정의하고 시작 명령을 받은 서비스
     * When: 서비스 종료
     * Then: 재정의한 훅 1회 호출, 기본 재시작 인텐트 없음
     */
    @Test
    fun test_03_destroyCallsOverriddenRestartHook() = runTest {
        /** Given **/
        val controller = Robolectric.buildService(CustomRestartService::class.java)
            .create()
            .startCommand(0, 1)

        /** When **/
        controller.destroy()

        /** Then **/
        assertEquals(1, controller.get().restartCount)
        assertNull(shadowOf(application).nextStartedService)
    }
}
