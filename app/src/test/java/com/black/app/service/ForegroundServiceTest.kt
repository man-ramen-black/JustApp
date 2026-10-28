package com.black.app.service

import android.app.Notification
import com.black.app.R
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/** [ForegroundService] 상시 알림의 아이콘, 색·액션 미지정 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ForegroundServiceTest : BaseTest() {

    private var serviceController: ServiceController<ForegroundService>? = null

    /** 서비스가 등록한 리시버 해제를 위한 각 테스트 후 서비스 종료 */
    @After
    fun destroyService() {
        serviceController?.destroy()
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
}
