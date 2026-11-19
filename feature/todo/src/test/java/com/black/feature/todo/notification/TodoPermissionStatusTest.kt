package com.black.feature.todo.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.black.core.util.NotificationUtil
import com.black.test.BaseTest
import com.black.test.NotificationTestUtil
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * [TodoPermissionStatus]의 Todo 알림 표시 가능 여부와 정확한 알람 허용 여부 검증
 *
 * Todo 알림 표시 가능 여부는 앱 알림 허용과 Todo 채널 두 개(집 도착·시각 알림)의 켜짐을 함께 봄.
 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TodoPermissionStatusTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val permissionStatus = TodoPermissionStatus(context)

    /**
     * 앱 알림이 허용되고 Todo 채널이 꺼지지 않았으면 표시 가능한지 검증
     *
     * Given: 알림 권한 허용, 집 도착 채널 중요도 DEFAULT, 시각 알림 채널은 아직 없음
     * When: canShowTodoNotifications
     * Then: true
     */
    @Test
    fun test_01_enabledChannelsCanShow() = runTest {
        /** Given **/
        NotificationTestUtil.grantNotificationPermission(context)
        NotificationUtil.createNotificationChannel(
            context,
            TodoNotifier.HOME_ARRIVAL_CHANNEL_ID,
            "x",
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
        )

        /** When **/
        val canShow = permissionStatus.canShowTodoNotifications()

        /** Then **/
        assertTrue(canShow)
    }

    /**
     * 앱 알림이 허용돼도 집 도착 채널만 꺼져 있으면 표시 불가인지 검증
     *
     * Given: 알림 권한 허용, 집 도착 채널 중요도 NONE
     * When: canShowTodoNotifications
     * Then: false
     */
    @Test
    fun test_02_homeArrivalChannelDisabledCannotShow() = runTest {
        /** Given **/
        NotificationTestUtil.grantNotificationPermission(context)
        NotificationUtil.createNotificationChannel(
            context,
            TodoNotifier.HOME_ARRIVAL_CHANNEL_ID,
            "x",
            NotificationManagerCompat.IMPORTANCE_NONE,
        )

        /** When **/
        val canShow = permissionStatus.canShowTodoNotifications()

        /** Then **/
        assertFalse(canShow)
    }

    /**
     * 앱 알림이 허용돼도 시각 알림 채널만 꺼져 있으면 표시 불가인지 검증
     *
     * Given: 알림 권한 허용, 시각 알림 채널 중요도 NONE
     * When: canShowTodoNotifications
     * Then: false
     */
    @Test
    fun test_03_timeChannelDisabledCannotShow() = runTest {
        /** Given **/
        NotificationTestUtil.grantNotificationPermission(context)
        NotificationUtil.createNotificationChannel(
            context,
            TodoNotifier.TIME_CHANNEL_ID,
            "x",
            NotificationManagerCompat.IMPORTANCE_NONE,
        )

        /** When **/
        val canShow = permissionStatus.canShowTodoNotifications()

        /** Then **/
        assertFalse(canShow)
    }

    /**
     * 앱 알림이 꺼져 있으면 채널과 상관없이 표시 불가인지 검증
     *
     * Given: 알림 권한 허용, 앱 알림 끔
     * When: canShowTodoNotifications
     * Then: false
     */
    @Test
    fun test_04_appNotificationsDisabledCannotShow() = runTest {
        /** Given **/
        NotificationTestUtil.grantNotificationPermission(context)
        NotificationTestUtil.shadowNotificationManager(context).setNotificationsEnabled(false)

        /** When **/
        val canShow = permissionStatus.canShowTodoNotifications()

        /** Then **/
        assertFalse(canShow)
    }

    /**
     * 정확한 알람 허용 여부를 그대로 반환하는지 검증
     *
     * Given: 정확한 알람 거부, 이어서 허용
     * When: 각각 canScheduleExactAlarms
     * Then: false, 이어서 true
     */
    @Test
    fun test_05_canScheduleExactAlarmsFollowsPermission() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        /** When / Then **/
        assertFalse(permissionStatus.canScheduleExactAlarms())

        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        /** When / Then **/
        assertTrue(permissionStatus.canScheduleExactAlarms())
    }
}
