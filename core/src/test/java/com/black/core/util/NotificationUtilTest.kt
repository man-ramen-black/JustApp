package com.black.core.util

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [NotificationUtil]의 기본 알림 아이콘 선택과 상시 실행 알림 구성 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotificationUtilTest : BaseTest() {

    companion object {
        private const val TEST_ACTION = "com.black.core.util.TEST_ACTION"
    }

    private val context: Context = RuntimeEnvironment.getApplication()

    /**
     * NOTIFICATION_ICON meta-data가 있으면 그 아이콘을 기본 small icon으로 사용하는지 검증
     *
     * Given: 앱 meta-data에 NOTIFICATION_ICON 지정
     * When: 아이콘 지정 없이 createNotification 호출
     * Then: small icon이 meta-data 아이콘
     */
    @Test
    fun test_01_metaDataIconUsedAsDefaultSmallIcon() = runTest {
        /** Given **/
        val notificationIcon = android.R.drawable.stat_notify_more
        mutableApplicationInfo().metaData = Bundle().apply {
            putInt(NotificationUtil.META_DATA_NOTIFICATION_ICON, notificationIcon)
        }

        /** When **/
        val notification = NotificationUtil.createNotification(context, "channel", "title", "message")

        /** Then **/
        assertEquals(notificationIcon, notification.smallIcon.resId)
    }

    /**
     * NOTIFICATION_ICON meta-data가 없으면 앱 아이콘을 기본 small icon으로 사용하는지 검증
     *
     * Given: 앱 meta-data 없음, 앱 아이콘 지정
     * When: 아이콘 지정 없이 createNotification 호출
     * Then: small icon이 앱 아이콘
     */
    @Test
    fun test_02_applicationIconUsedWithoutMetaData() = runTest {
        /** Given **/
        val applicationIcon = android.R.drawable.sym_def_app_icon
        mutableApplicationInfo().apply {
            metaData = null
            icon = applicationIcon
        }

        /** When **/
        val notification = NotificationUtil.createNotification(context, "channel", "title", "message")

        /** Then **/
        assertEquals(applicationIcon, notification.smallIcon.resId)
    }

    /**
     * 상시 실행 알림이 중요도 낮음 채널에 지속 알림으로 만들어지고 터치 시 지정한 Activity 인텐트를 시작하는지 검증
     *
     * Given: 터치 시 시작할 인텐트, 요청 코드
     * When: createOngoingNotification 호출
     * Then: 채널 중요도 LOW·이름 지정, 지속 알림, contentIntent가 같은 인텐트·요청 코드의 갱신 불가 Activity PendingIntent
     */
    @Test
    fun test_03_ongoingNotificationUsesLowChannelAndContentIntent() = runTest {
        /** Given **/
        val contentIntent = Intent(TEST_ACTION).setPackage(context.packageName)
        val requestCode = 7

        /** When **/
        val notification = NotificationUtil.createOngoingNotification(
            context,
            "ongoing",
            "Ongoing",
            "title",
            "message",
            contentIntent,
            requestCode,
        )

        /** Then **/
        val channel = checkNotNull(NotificationManagerCompat.from(context).getNotificationChannelCompat("ongoing"))
        assertEquals(NotificationManagerCompat.IMPORTANCE_LOW, channel.importance)
        assertEquals("Ongoing", channel.name)
        assertEquals("ongoing", notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        val pendingIntent = shadowOf(notification.contentIntent)
        assertTrue(pendingIntent.isActivityIntent)
        assertTrue(contentIntent.filterEquals(pendingIntent.savedIntent))
        assertEquals(requestCode, pendingIntent.requestCode)
        assertEquals(PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE, pendingIntent.flags)
    }

    /**
     * 상시 실행 알림에 One UI "그 외 알림" 분류를 막는 액션 버튼과 색 지정이 없는지 검증
     *
     * Given / When: createOngoingNotification 호출
     * Then: actions가 null, colorized 아님, 색이 COLOR_DEFAULT
     */
    @Test
    fun test_04_ongoingNotificationHasNoActionsOrColor() = runTest {
        /** Given / When **/
        val notification = NotificationUtil.createOngoingNotification(
            context,
            "ongoing",
            "Ongoing",
            "title",
            "message",
            Intent(TEST_ACTION),
            1,
        )

        /** Then **/
        assertNull(notification.actions)
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals(Notification.COLOR_DEFAULT, notification.color)
    }

    /**
     * PackageManager가 돌려주는 자기 앱 ApplicationInfo 원본
     *
     * 이 객체를 수정하면 getApplicationInfo 결과에 반영
     */
    private fun mutableApplicationInfo(): ApplicationInfo =
        checkNotNull(shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).applicationInfo)
}
