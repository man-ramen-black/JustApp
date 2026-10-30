package com.black.app.service

import android.app.Notification
import android.os.PowerManager
import com.black.app.R
import com.black.app.model.datastore.ForegroundServiceDataStore
import com.black.test.BaseTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
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
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/** [ForegroundService] 상시 알림의 아이콘, 색·액션 미지정과 실행 상태 노출, 종료 후 재시작 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ForegroundServiceTest : BaseTest() {

    private val application = RuntimeEnvironment.getApplication()
    private val dataStore = ForegroundServiceDataStore(application)
    private var serviceController: ServiceController<ForegroundService>? = null

    /** 서비스가 등록한 리시버 해제를 위한 각 테스트 후 서비스 종료, 프로세스 단일 DataStore의 실행 설정 초기화 */
    @After
    fun destroyService() {
        serviceController?.destroy()
        runTest { dataStore.updateActivated(false) }
    }

    /**
     * 상시 알림이 단색 알림 아이콘으로 표시되고 One UI "그 외 알림" 구역 분류를 막는 색 지정이 없는지 검증
     *
     * Given / When: ForegroundService 생성
     * Then: small icon이 매니페스트 NOTIFICATION_ICON(ic_notification), colorized 아님, 색이 COLOR_DEFAULT
     */
    @Test
    fun test_01_foregroundNotificationIsNotColorizedWithNotificationIcon() = runTest {
        /** Given / When **/
        val controller = Robolectric.buildService(ForegroundService::class.java).create()
        serviceController = controller

        /** Then **/
        val service = controller.get()
        val notification = shadowOf(service).lastForegroundNotification
        assertEquals(R.drawable.ic_notification, notification.smallIcon.resId)
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals(Notification.COLOR_DEFAULT, notification.color)
    }

    /**
     * 상시 알림에 One UI "그 외 알림" 분류를 막는 액션 버튼이 없는지 검증
     *
     * Given / When: ForegroundService 생성
     * Then: 알림 actions가 null
     */
    @Test
    fun test_02_foregroundNotificationHasNoActions() = runTest {
        /** Given / When **/
        val controller = Robolectric.buildService(ForegroundService::class.java).create()
        serviceController = controller

        /** Then **/
        val notification = shadowOf(controller.get()).lastForegroundNotification
        assertNull(notification.actions)
    }

    /**
     * 서비스 생성·종료에 따라 running 상태가 바뀌는지 검증
     *
     * Given / When: ForegroundService 생성
     * Then: running true, 종료 후 false
     */
    @Test
    fun test_03_runningReflectsCreateAndDestroy() = runTest {
        /** Given / When **/
        val controller = Robolectric.buildService(ForegroundService::class.java).create()

        /** Then **/
        assertTrue(ForegroundService.running.value)

        /** When **/
        controller.destroy()

        /** Then **/
        assertFalse(ForegroundService.running.value)
    }

    /**
     * 실행 설정이 켜진 채 종료되면 다시 시작을 요청하는지 검증
     *
     * Given: 실행 설정 true, 배터리 최적화 예외, ForegroundService 생성
     * When: 서비스 종료
     * Then: ForegroundService 시작 인텐트 기록
     */
    @Test
    fun test_04_destroyRestartsWhenActivated() = runTest {
        /** Given **/
        dataStore.updateActivated(true)
        shadowOf(application.getSystemService(PowerManager::class.java))
            .setIgnoringBatteryOptimizations(application.packageName, true)
        val controller = Robolectric.buildService(ForegroundService::class.java).create()

        /** When **/
        controller.destroy()

        /** Then **/
        // 재시작 확인은 서비스 수명과 분리된 Dispatchers.Default 범위에서 DataStore를 읽으므로 실제 시간으로 대기
        val startedService = withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                var intent = shadowOf(application).peekNextStartedService()
                while (intent == null) {
                    delay(50)
                    intent = shadowOf(application).peekNextStartedService()
                }
                intent
            }
        }
        assertEquals(ForegroundService::class.java.name, startedService.component?.className)
    }
}
