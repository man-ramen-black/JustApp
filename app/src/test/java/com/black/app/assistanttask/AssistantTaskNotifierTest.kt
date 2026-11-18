package com.black.app.assistanttask

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.core.app.NotificationManagerCompat
import com.black.app.broadcast.PackageInstallTestFixture
import com.black.app.ui.MainActivity
import com.black.core.util.NotificationUtil
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.TimeZone

/** [AssistantTaskNotifier]의 집 도착·시각 알림 내용, 눌렀을 때 인텐트, 대상 없음·권한 없음 처리, 시각 알림 제거 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssistantTaskNotifierTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifier = AssistantTaskNotifier(context)

    private fun task(title: String) = AssistantTask(
        title, title, AssistantTaskKind.TASK, false, null, AssistantTaskReminder.HOME_ARRIVAL, "", 0L, "",
    )

    private fun shown(): Notification? =
        PackageInstallTestFixture.shadowNotificationManager(context).getNotification(7)

    /** 기한이 서울 2026-11-20 09:30인 시각 알림 Todo */
    private fun timeTask(pageId: String, title: String) = AssistantTask(
        pageId, title, AssistantTaskKind.TASK, false, AssistantTaskDue.DateTime(1_795_134_600_000L),
        AssistantTaskReminder.TIME, "", 0L, "",
    )

    /** 기본 시간대를 서울로 바꿔 [block]을 돌린 뒤 되돌림 */
    private fun inSeoul(block: () -> Unit) {
        val defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        try {
            block()
        } finally {
            TimeZone.setDefault(defaultZone)
        }
    }

    /**
     * 대상 둘이면 알림 하나를 개수 제목과 이름 목록으로 띄우는지 검증
     *
     * Given: 알림 허용, 대상 두 개
     * When: showHomeArrival
     * Then: true, ID 7 알림 하나, 제목 `집 도착 Todo 2개`, 본문 `가, 나`, 누르면 todo 딥링크
     */
    @Test
    fun test_01_showsOneNotificationWithCountTitleAndDeeplink() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)

        /** When **/
        val result = notifier.showHomeArrival(listOf(task("가"), task("나")))

        /** Then **/
        assertTrue(result)
        assertEquals(1, PackageInstallTestFixture.shadowNotificationManager(context).allNotifications.size)
        val notification = requireNotNull(shown())
        assertEquals("집 도착 Todo 2개", PackageInstallTestFixture.notificationTitle(notification))
        assertEquals("가, 나", PackageInstallTestFixture.notificationText(notification))
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals("black://navigate/todo", intent.data.toString())
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(context.packageName, intent.component?.packageName)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    /**
     * 대상이 없으면 알림을 띄우지 않는지 검증
     *
     * Given: 알림 허용, 빈 목록
     * When: showHomeArrival
     * Then: false, 알림 없음
     */
    @Test
    fun test_02_noTasksShowsNothing() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)

        /** When **/
        val result = notifier.showHomeArrival(emptyList())

        /** Then **/
        assertFalse(result)
        assertNull(shown())
    }

    /**
     * 알림이 꺼져 있으면 띄우지 않는지 검증
     *
     * Given: 앱 알림 끔
     * When: showHomeArrival
     * Then: false, 알림 없음
     */
    @Test
    fun test_03_notificationsDisabledShowsNothing() = runTest {
        /** Given **/
        PackageInstallTestFixture.shadowNotificationManager(context).setNotificationsEnabled(false)

        /** When **/
        val result = notifier.showHomeArrival(listOf(task("가")))

        /** Then **/
        assertFalse(result)
        assertNull(shown())
    }

    /**
     * 채널 이름에 "할 일"이 없는지 검증
     *
     * Given: 알림 표시 뒤 생성된 채널
     * When: 채널 이름 조회
     * Then: `Todo 집 도착 알림`이고 "할 일"을 포함하지 않음
     */
    @Test
    fun test_04_channelNameHasNoKoreanTaskWord() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        notifier.showHomeArrival(listOf(task("가")))

        /** When **/
        val channel = PackageInstallTestFixture.notificationManager(context).getNotificationChannel("AssistantTaskHomeArrival")

        /** Then **/
        assertNotNull(channel)
        assertEquals("Todo 집 도착 알림", channel.name.toString())
        assertEquals(NotificationManagerCompat.IMPORTANCE_DEFAULT, channel.importance)
        assertFalse(channel.name.toString().contains("할 일"))
    }

    /**
     * 대상이 6개면 펼친 줄은 앞 5개만 보이는지 검증
     *
     * Given: 알림 허용, 대상 6개
     * When: showHomeArrival
     * Then: 제목 `집 도착 Todo 6개`, EXTRA_TEXT_LINES 5줄
     */
    @Test
    fun test_05_inboxShowsFirstFiveLines() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)

        /** When **/
        notifier.showHomeArrival(listOf("1", "2", "3", "4", "5", "6").map(::task))

        /** Then **/
        val notification = requireNotNull(shown())
        assertEquals("집 도착 Todo 6개", PackageInstallTestFixture.notificationTitle(notification))
        assertEquals(5, notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.size)
    }

    /**
     * 이 채널만 꺼져 있으면 띄우지 않는지 검증
     *
     * Given: 앱 알림 허용, 이 채널 중요도 NONE
     * When: showHomeArrival
     * Then: false, 알림 없음
     */
    @Test
    fun test_06_disabledChannelShowsNothing() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        NotificationUtil.createNotificationChannel(context, "AssistantTaskHomeArrival", "x", NotificationManagerCompat.IMPORTANCE_NONE)

        /** When **/
        val result = notifier.showHomeArrival(listOf(task("가")))

        /** Then **/
        assertFalse(result)
        assertNull(shown())
    }

    /**
     * 이름이 빈 Todo는 `제목 없음`으로 보이는지 검증
     *
     * Given: 알림 허용, 이름이 공백인 대상
     * When: showHomeArrival
     * Then: 본문 `제목 없음`
     */
    @Test
    fun test_07_blankTitleShowsUntitled() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)

        /** When **/
        notifier.showHomeArrival(listOf(task(" ")))

        /** Then **/
        assertEquals("제목 없음", PackageInstallTestFixture.notificationText(requireNotNull(shown())))
    }

    /**
     * 시각 알림이 Todo 이름 제목과 기한 시각 본문으로 page ID별 알림을 띄우는지 검증
     *
     * Given: 알림 허용, 서울 시간대, 기한 09:30인 두 Todo
     * When: 두 Todo로 showTimeReminder
     * Then: 둘 다 true, page ID hashCode 알림 둘, 제목 `장보기`, 본문 `09:30`, 누르면 MainActivity로 todo 딥링크
     */
    @Test
    fun test_08_timeReminderShowsTitleTimeAndDeeplink() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)

        inSeoul {
            /** When **/
            val first = notifier.showTimeReminder(timeTask("page-1", "장보기"))
            val second = notifier.showTimeReminder(timeTask("page-2", "빨래"))

            /** Then **/
            assertTrue(first)
            assertTrue(second)
            val shadowManager = PackageInstallTestFixture.shadowNotificationManager(context)
            assertEquals(2, shadowManager.allNotifications.size)
            val notification = requireNotNull(shadowManager.getNotification("page-1".hashCode()))
            assertEquals("장보기", PackageInstallTestFixture.notificationTitle(notification))
            assertEquals("09:30", PackageInstallTestFixture.notificationText(notification))
            val intent = shadowOf(notification.contentIntent).savedIntent
            assertEquals("black://navigate/todo", intent.data.toString())
            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertEquals(context.packageName, intent.component?.packageName)
            assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        }
    }

    /**
     * 시각 알림 채널이 `Todo 시각 알림`이고 중요도가 높은지 검증
     *
     * Given: 시각 알림 표시 뒤 생성된 채널
     * When: 채널 조회
     * Then: 이름 `Todo 시각 알림`, IMPORTANCE_HIGH, "할 일" 없음
     */
    @Test
    fun test_09_timeReminderChannelIsHighImportance() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        notifier.showTimeReminder(timeTask("page-1", "장보기"))

        /** When **/
        val channel = PackageInstallTestFixture.notificationManager(context).getNotificationChannel("AssistantTaskTime")

        /** Then **/
        assertNotNull(channel)
        assertEquals("Todo 시각 알림", channel.name.toString())
        assertEquals(NotificationManagerCompat.IMPORTANCE_HIGH, channel.importance)
        assertFalse(channel.name.toString().contains("할 일"))
    }

    /**
     * 이름이 빈 Todo의 시각 알림 제목이 `제목 없음`인지 검증
     *
     * Given: 알림 허용, 이름이 공백인 시각 알림 Todo
     * When: showTimeReminder
     * Then: 제목 `제목 없음`
     */
    @Test
    fun test_10_timeReminderBlankTitleShowsUntitled() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)

        /** When **/
        notifier.showTimeReminder(timeTask("page-1", " "))

        /** Then **/
        val notification = PackageInstallTestFixture.shadowNotificationManager(context).getNotification("page-1".hashCode())
        assertEquals("제목 없음", PackageInstallTestFixture.notificationTitle(requireNotNull(notification)))
    }

    /**
     * 앱 알림이나 시각 알림 채널이 꺼져 있으면 시각 알림을 띄우지 않는지 검증
     *
     * Given: 앱 알림 끔, 이어서 앱 알림은 켜고 시각 알림 채널만 끔
     * When: 각각 showTimeReminder
     * Then: 둘 다 false, 알림 없음
     */
    @Test
    fun test_11_timeReminderDisabledShowsNothing() = runTest {
        /** Given **/
        val shadowManager = PackageInstallTestFixture.shadowNotificationManager(context)
        shadowManager.setNotificationsEnabled(false)

        /** When **/
        val appDisabled = notifier.showTimeReminder(timeTask("page-1", "장보기"))

        /** Then **/
        assertFalse(appDisabled)
        assertEquals(0, shadowManager.allNotifications.size)

        /** Given **/
        shadowManager.setNotificationsEnabled(true)
        PackageInstallTestFixture.grantNotificationPermission(context)
        NotificationUtil.createNotificationChannel(context, "AssistantTaskTime", "x", NotificationManagerCompat.IMPORTANCE_NONE)

        /** When **/
        val channelDisabled = notifier.showTimeReminder(timeTask("page-1", "장보기"))

        /** Then **/
        assertFalse(channelDisabled)
        assertEquals(0, shadowManager.allNotifications.size)
    }

    /**
     * 기한에 시각이 없으면 시각 알림을 띄우지 않는지 검증
     *
     * Given: 알림 허용, 기한이 날짜만인 Todo와 기한 없는 Todo
     * When: 각각 showTimeReminder
     * Then: 둘 다 false, 알림 없음
     */
    @Test
    fun test_12_timeReminderWithoutDueTimeShowsNothing() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        val dateOnly = timeTask("page-1", "장보기").copy(due = AssistantTaskDue.DateOnly("2026-11-20"))
        val noDue = timeTask("page-2", "빨래").copy(due = null)

        /** When **/
        val dateOnlyResult = notifier.showTimeReminder(dateOnly)
        val noDueResult = notifier.showTimeReminder(noDue)

        /** Then **/
        assertFalse(dateOnlyResult)
        assertFalse(noDueResult)
        assertEquals(0, PackageInstallTestFixture.shadowNotificationManager(context).allNotifications.size)
    }

    /**
     * 시각 알림 제거가 그 Todo의 시각 알림만 지우고 다른 Todo의 시각 알림과 집 도착 알림은 남기는지 검증
     *
     * Given: 알림 허용, page-1·page-2 시각 알림과 집 도착 알림 표시
     * When: page-1로 cancelTimeReminder
     * Then: page-1 알림 없음, page-2 알림과 ID 7 집 도착 알림 유지
     */
    @Test
    fun test_13_cancelTimeReminderRemovesOnlyThatTask() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        notifier.showTimeReminder(timeTask("page-1", "장보기"))
        notifier.showTimeReminder(timeTask("page-2", "빨래"))
        notifier.showHomeArrival(listOf(task("가")))

        /** When **/
        notifier.cancelTimeReminder("page-1")

        /** Then **/
        val shadowManager = PackageInstallTestFixture.shadowNotificationManager(context)
        assertNull(shadowManager.getNotification("page-1".hashCode()))
        assertNotNull(shadowManager.getNotification("page-2".hashCode()))
        assertNotNull(shown())
        assertEquals(2, shadowManager.allNotifications.size)
    }
}
