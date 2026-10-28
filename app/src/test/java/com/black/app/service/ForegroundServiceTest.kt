package com.black.app.service

import android.app.Notification
import androidx.core.content.ContextCompat
import com.black.app.R
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/** [ForegroundService] 상시 알림의 아이콘·색 지정 검증 */
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
     * 상시 알림이 단색 알림 아이콘과 색 지정으로 표시되는지 검증
     *
     * Given / When: ForegroundService 생성
     * Then: small icon이 매니페스트 NOTIFICATION_ICON(ic_notification), colorized, 색이 color_primary
     */
    @Test
    fun test_01_foregroundNotificationIsColorizedWithNotificationIcon() = runTest {
        /** Given / When **/
        val controller = Robolectric.buildService(ForegroundService::class.java).create()
        serviceController = controller

        /** Then **/
        val service = controller.get()
        val notification = shadowOf(service).lastForegroundNotification
        assertEquals(R.drawable.ic_notification, notification.smallIcon.resId)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals(ContextCompat.getColor(service, R.color.color_primary), notification.color)
    }
}
