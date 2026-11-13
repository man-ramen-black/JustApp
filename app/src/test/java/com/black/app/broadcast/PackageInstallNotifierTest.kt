package com.black.app.broadcast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.black.app.R
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_ACTIVITY
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_LABEL
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_PACKAGE
import com.black.core.util.NotificationUtil
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [PackageInstallNotifier]의 대화·대체 경로 알림 내용, 바로가기 정리 순서, 실행 인텐트와 알림 제외 조건, 표시 가능 여부, 채널 중요도·우선순위 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PackageInstallNotifierTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * 새로 설치된 런처 앱의 대화 알림 내용 검증
     *
     * Given: SDK 34, 알림 권한 허용, 런처 앱 설치
     * When: isUpdate=false로 show 호출
     * Then: 알림 1개, 제목=앱 이름, 메시지=설치되었습니다., 채널 PackageInstall(이름, 중요도 LOW), 우선순위 LOW, small icon ic_app_install, 자동 취소, shortcutId=PackageInstall:<패키지명>, MessagingStyle(사용자 JustApp, 보낸 사람 앱 이름·key 패키지명·아이콘, 본문 설치 메시지), large icon 없음, 동적 바로가기 없음
     */
    @Test
    fun test_01_newLauncherAppShowsConversationNotification() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        PackageInstallTestFixture.installLauncherApp(context)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)

        /** Then **/
        assertEquals(1, PackageInstallTestFixture.shadowNotificationManager(context).size())
        val notification = PackageInstallTestFixture.installNotification(context)
        assertEquals(LAUNCHER_LABEL, PackageInstallTestFixture.notificationTitle(notification))
        assertEquals("설치되었습니다.", PackageInstallTestFixture.notificationText(notification))
        assertEquals("PackageInstall", notification.channelId)
        val channel = PackageInstallTestFixture.notificationManager(context).getNotificationChannel("PackageInstall")
        assertEquals("앱 설치·업데이트", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals(NotificationCompat.PRIORITY_LOW, notification.priority)
        assertEquals(R.drawable.ic_app_install, notification.smallIcon.resId)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals("PackageInstall:$LAUNCHER_PACKAGE", notification.shortcutId)
        val messagingStyle = requireNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
        assertEquals("JustApp", messagingStyle.user.name.toString())
        val message = messagingStyle.messages.single()
        assertEquals("설치되었습니다.", message.text.toString())
        assertEquals(LAUNCHER_LABEL, message.person?.name.toString())
        assertEquals(LAUNCHER_PACKAGE, message.person?.key)
        assertNotNull(message.person?.icon)
        assertNull(notification.getLargeIcon())
        // 알림 표시 직후 런처 길게 누르기 메뉴에 남지 않도록 자기 바로가기 삭제
        assertTrue(PackageInstallTestFixture.dynamicShortcuts(context).isEmpty())
    }

    /**
     * 알림 터치 시 해당 앱 런처 액티비티 실행 인텐트 검증
     *
     * Given: 알림 권한 허용, 런처 앱 설치
     * When: show 호출
     * Then: content intent가 불변 액티비티 PendingIntent, 대상이 런처 액티비티
     */
    @Test
    fun test_02_contentIntentLaunchesApp() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        PackageInstallTestFixture.installLauncherApp(context)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)

        /** Then **/
        val notification = PackageInstallTestFixture.installNotification(context)
        val shadowContentIntent = shadowOf(notification.contentIntent)
        assertTrue(notification.contentIntent.isActivity)
        assertTrue(shadowContentIntent.isImmutable)
        assertEquals(ComponentName(LAUNCHER_PACKAGE, LAUNCHER_ACTIVITY), shadowContentIntent.savedIntent.component)
    }

    /**
     * 업데이트된 앱의 알림 메시지·small icon 검증
     *
     * Given: 알림 권한 허용, 런처 앱 설치
     * When: isUpdate=true로 show 호출
     * Then: 메시지=업데이트되었습니다., small icon ic_app_install(설치와 공통), 대화 메시지 본문=업데이트되었습니다.
     */
    @Test
    fun test_03_updatedAppShowsUpdatedMessage() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        PackageInstallTestFixture.installLauncherApp(context)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = true)

        /** Then **/
        val notification = PackageInstallTestFixture.installNotification(context)
        assertEquals("업데이트되었습니다.", PackageInstallTestFixture.notificationText(notification))
        assertEquals(R.drawable.ic_app_install, notification.smallIcon.resId)
        val messagingStyle = requireNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
        assertEquals("업데이트되었습니다.", messagingStyle.messages.single().text.toString())
    }

    /**
     * 런처 액티비티 없는 앱은 알림 제외 검증
     *
     * Given: 알림 권한 허용, 런처 액티비티 없는 앱 설치
     * When: show 호출
     * Then: 알림 없음, 바로가기 없음
     */
    @Test
    fun test_04_appWithoutLauncherShowsNothing() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        PackageInstallTestFixture.installApp(context, LAUNCHER_PACKAGE, LAUNCHER_LABEL)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)

        /** Then **/
        assertEquals(0, PackageInstallTestFixture.shadowNotificationManager(context).size())
        assertTrue(PackageInstallTestFixture.dynamicShortcuts(context).isEmpty())
    }

    /**
     * 설치되지 않은 패키지명은 예외 없이 알림 제외 검증
     *
     * Given: 알림 권한 허용, 패키지 미설치
     * When: show 호출
     * Then: 알림 없음
     */
    @Test
    fun test_05_notInstalledPackageShowsNothing() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)

        /** Then **/
        assertEquals(0, PackageInstallTestFixture.shadowNotificationManager(context).size())
    }

    /**
     * 알림 권한 거부 시 알림 제외 검증
     *
     * Given: 알림 권한 거부, 런처 앱 설치
     * When: show 호출
     * Then: 알림 없음, 바로가기 없음
     */
    @Test
    fun test_06_permissionDeniedShowsNothing() = runTest {
        /** Given **/
        PackageInstallTestFixture.denyNotificationPermission(context)
        PackageInstallTestFixture.installLauncherApp(context)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)

        /** Then **/
        assertEquals(0, PackageInstallTestFixture.shadowNotificationManager(context).size())
        assertTrue(PackageInstallTestFixture.dynamicShortcuts(context).isEmpty())
    }

    /**
     * 같은 앱 연속 통지 시 앞선 알림 대체 검증
     *
     * Given: 알림 권한 허용, 런처 앱 설치
     * When: 설치 후 업데이트로 show 2회 호출
     * Then: 알림 1개, 메시지=업데이트되었습니다., 동적 바로가기 없음
     */
    @Test
    fun test_07_samePackageReplacesNotification() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        PackageInstallTestFixture.installLauncherApp(context)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = true)

        /** Then **/
        assertEquals(1, PackageInstallTestFixture.shadowNotificationManager(context).size())
        val notification = PackageInstallTestFixture.installNotification(context)
        assertEquals("업데이트되었습니다.", PackageInstallTestFixture.notificationText(notification))
        assertTrue(PackageInstallTestFixture.dynamicShortcuts(context).isEmpty())
    }

    /**
     * 서로 다른 앱은 각자 알림 표시 검증
     *
     * Given: 알림 권한 허용, 런처 앱 2개 설치
     * When: 두 앱에 대해 show 호출
     * Then: 알림 2개, 각 패키지 ID에 해당 앱 이름과 해당 앱 shortcutId
     */
    @Test
    fun test_08_differentPackagesShowSeparateNotifications() = runTest {
        /** Given **/
        val secondPackage = "com.example.second"
        val secondLabel = "Second Sample"
        PackageInstallTestFixture.grantNotificationPermission(context)
        PackageInstallTestFixture.installLauncherApp(context)
        PackageInstallTestFixture.installLauncherApp(context, secondPackage, secondLabel)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = true)
        PackageInstallNotifier.show(context, secondPackage, isUpdate = true)

        /** Then **/
        assertEquals(2, PackageInstallTestFixture.shadowNotificationManager(context).size())
        val firstNotification = PackageInstallTestFixture.installNotification(context)
        val secondNotification = PackageInstallTestFixture.installNotification(context, secondPackage)
        assertEquals(LAUNCHER_LABEL, PackageInstallTestFixture.notificationTitle(firstNotification))
        assertEquals(secondLabel, PackageInstallTestFixture.notificationTitle(secondNotification))
        assertEquals("PackageInstall:$LAUNCHER_PACKAGE", firstNotification.shortcutId)
        assertEquals("PackageInstall:$secondPackage", secondNotification.shortcutId)
    }

    /**
     * Android 12 이하는 알림 권한 검사 없이 표시 검증
     *
     * Given: SDK 32, 알림 권한 미부여, 런처 앱 설치
     * When: show 호출
     * Then: 알림 1개
     */
    @Test
    @Config(sdk = [32])
    fun test_09_belowTiramisuShowsWithoutPermission() = runTest {
        /** Given **/
        PackageInstallTestFixture.installLauncherApp(context)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)

        /** Then **/
        assertEquals(1, PackageInstallTestFixture.shadowNotificationManager(context).size())
    }

    /**
     * 바로가기 게시 실패 시 large icon 알림으로 대체 검증
     *
     * Given: SDK 34, 알림 권한 허용, 런처 앱 설치, 활동별 최대 바로가기 수 0
     * When: isUpdate=false로 show 호출
     * Then: 알림 1개, 제목=앱 이름, 메시지=설치되었습니다., shortcutId 없음, MessagingStyle 없음, large icon 축소 크기, 바로가기 없음
     */
    @Test
    fun test_10_shortcutPublishFailureFallsBackToLargeIcon() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        PackageInstallTestFixture.installLauncherApp(context)
        PackageInstallTestFixture.shadowShortcutManager(context).setMaxShortcutCountPerActivity(0)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)

        /** Then **/
        assertEquals(1, PackageInstallTestFixture.shadowNotificationManager(context).size())
        val notification = PackageInstallTestFixture.installNotification(context)
        assertEquals(LAUNCHER_LABEL, PackageInstallTestFixture.notificationTitle(notification))
        assertEquals("설치되었습니다.", PackageInstallTestFixture.notificationText(notification))
        assertNull(notification.shortcutId)
        assertNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
        val largeIcon = (notification.getLargeIcon().loadDrawable(context) as BitmapDrawable).bitmap
        // Notification.Builder.build()가 large icon을 비공개 리소스 notification_right_icon_size(48dp)로 축소, android.R에 없어 하드코딩
        val reducedLargeIconSize = (48 * context.resources.displayMetrics.density).toInt()
        assertEquals(reducedLargeIconSize, largeIcon.width)
        assertEquals(reducedLargeIconSize, largeIcon.height)
        assertTrue(PackageInstallTestFixture.dynamicShortcuts(context).isEmpty())
    }

    /**
     * Android 11도 대화 알림 표시 후 자기 바로가기 삭제 검증
     *
     * Given: SDK 30, 런처 앱 설치
     * When: isUpdate=true로 show 호출
     * Then: 알림 1개, shortcutId=PackageInstall:<패키지명>, MessagingStyle 본문=업데이트되었습니다., large icon 없음, 동적 바로가기 없음
     */
    @Test
    @Config(sdk = [30])
    fun test_11_android11ShowsConversationNotification() = runTest {
        /** Given **/
        PackageInstallTestFixture.installLauncherApp(context)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = true)

        /** Then **/
        assertEquals(1, PackageInstallTestFixture.shadowNotificationManager(context).size())
        val notification = PackageInstallTestFixture.installNotification(context)
        assertEquals("PackageInstall:$LAUNCHER_PACKAGE", notification.shortcutId)
        val messagingStyle = requireNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
        assertEquals("업데이트되었습니다.", messagingStyle.messages.single().text.toString())
        assertNull(notification.getLargeIcon())
        assertTrue(PackageInstallTestFixture.dynamicShortcuts(context).isEmpty())
    }

    /**
     * Android 11 미만은 바로가기 없이 large icon 알림 검증
     *
     * Given: SDK 29, 런처 앱 설치
     * When: isUpdate=false로 show 호출
     * Then: 알림 1개, 메시지=설치되었습니다., shortcutId 없음, MessagingStyle 없음, large icon 있음, 동적 바로가기 없음
     */
    @Test
    @Config(sdk = [29])
    fun test_12_belowAndroid11ShowsLargeIconWithoutShortcut() = runTest {
        /** Given **/
        PackageInstallTestFixture.installLauncherApp(context)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)

        /** Then **/
        assertEquals(1, PackageInstallTestFixture.shadowNotificationManager(context).size())
        val notification = PackageInstallTestFixture.installNotification(context)
        assertEquals("설치되었습니다.", PackageInstallTestFixture.notificationText(notification))
        assertNull(notification.shortcutId)
        assertNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
        assertNotNull(notification.getLargeIcon())
        assertTrue(PackageInstallTestFixture.dynamicShortcuts(context).isEmpty())
    }

    /**
     * 바로가기 게시 → 알림 게시 → 바로가기 삭제 순서 검증
     *
     * 실기기 NotificationManagerService는 notify 호출 안에서 바로가기를 확인·캐시하므로, notify 시점에 바로가기가 없으면 대화 알림이 되지 않음.
     * Robolectric은 이 확인을 흉내 내지 않아 호출 순서를 직접 기록해 고정
     *
     * Given: SDK 34, 알림 권한 허용, 런처 앱 설치, NotificationUtil.showNotification 호출 시점의 동적 바로가기 ID 기록
     * When: isUpdate=false로 show 호출
     * Then: showNotification 호출 시점에 PackageInstall:<패키지명> 존재, 호출 뒤 동적 바로가기 없음, 알림 1개
     */
    @Test
    fun test_13_shortcutExistsAtNotifyAndRemovedAfter() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        PackageInstallTestFixture.installLauncherApp(context)
        var shortcutIdsAtNotify: List<String>? = null
        mockkObject(NotificationUtil)
        every { NotificationUtil.showNotification(any(), any(), any(), any(), any(), any(), any()) } answers {
            shortcutIdsAtNotify = PackageInstallTestFixture.dynamicShortcuts(context).map { it.id }
            callOriginal()
        }

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)

        /** Then **/
        assertEquals(listOf("PackageInstall:$LAUNCHER_PACKAGE"), shortcutIdsAtNotify)
        assertTrue(PackageInstallTestFixture.dynamicShortcuts(context).isEmpty())
        assertEquals(1, PackageInstallTestFixture.shadowNotificationManager(context).size())
    }

    /**
     * 알림 게시가 예외로 끝나도 바로가기 정리 검증
     *
     * Given: SDK 34, 알림 권한 허용, 런처 앱 설치, NotificationUtil.showNotification이 SecurityException 발생
     * When: show 호출
     * Then: SecurityException 전파, 동적 바로가기 없음
     */
    @Test
    fun test_14_notifyFailureStillRemovesShortcut() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        PackageInstallTestFixture.installLauncherApp(context)
        mockkObject(NotificationUtil)
        every { NotificationUtil.showNotification(any(), any(), any(), any(), any(), any(), any()) } throws SecurityException("notify denied")

        /** When **/
        assertThrows(SecurityException::class.java) {
            PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)
        }

        /** Then **/
        assertTrue(PackageInstallTestFixture.dynamicShortcuts(context).isEmpty())
    }

    /**
     * 설치 알림 채널이 아직 없으면 앱 알림 허용만으로 표시 가능으로 판단하는지 검증
     *
     * Given: SDK 34, 앱 알림 허용, PackageInstall 채널 없음
     * When: canShow 호출
     * Then: true
     */
    @Test
    fun test_15_canShowWhenChannelNotCreated() = runTest {
        /** Given **/
        PackageInstallTestFixture.shadowNotificationManager(context).setNotificationsEnabled(true)

        /** When **/
        val canShow = PackageInstallNotifier.canShow(context)

        /** Then **/
        assertTrue(canShow)
    }

    /**
     * 앱 알림은 허용인데 설치 알림 채널만 끈 경우 표시 불가로 판단하는지 검증
     *
     * Given: SDK 34, 앱 알림 허용, PackageInstall 채널 중요도 NONE
     * When: canShow 호출
     * Then: false
     */
    @Test
    fun test_16_cannotShowWhenChannelBlocked() = runTest {
        /** Given **/
        PackageInstallTestFixture.shadowNotificationManager(context).setNotificationsEnabled(true)
        PackageInstallTestFixture.notificationManager(context).createNotificationChannel(
            NotificationChannel("PackageInstall", "앱 설치·업데이트", NotificationManager.IMPORTANCE_NONE)
        )

        /** When **/
        val canShow = PackageInstallNotifier.canShow(context)

        /** Then **/
        assertFalse(canShow)
    }

    /**
     * 앱 알림이 꺼져 있으면 채널이 없어도 표시 불가로 판단하는지 검증
     *
     * Given: SDK 34, 앱 알림 끔, PackageInstall 채널 없음
     * When: canShow 호출
     * Then: false
     */
    @Test
    fun test_17_cannotShowWhenAppNotificationsDisabled() = runTest {
        /** Given **/
        PackageInstallTestFixture.shadowNotificationManager(context).setNotificationsEnabled(false)

        /** When **/
        val canShow = PackageInstallNotifier.canShow(context)

        /** Then **/
        assertFalse(canShow)
    }

    /**
     * 이전 버전이 DEFAULT로 만든 설치 알림 채널을 다음 알림 때 LOW로 낮추는지 검증
     *
     * 사용자가 채널 설정을 바꾸지 않은 채널만 시스템이 중요도를 낮추며, Robolectric은 사용자 변경 여부 없이 하향만 흉내 냄
     *
     * Given: SDK 34, 알림 권한 허용, 런처 앱 설치, PackageInstall 채널 중요도 DEFAULT
     * When: show 호출
     * Then: PackageInstall 채널 중요도 LOW, 알림 1개
     */
    @Test
    fun test_18_existingDefaultChannelLoweredToLow() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        PackageInstallTestFixture.installLauncherApp(context)
        PackageInstallTestFixture.notificationManager(context).createNotificationChannel(
            NotificationChannel("PackageInstall", "앱 설치·업데이트", NotificationManager.IMPORTANCE_DEFAULT)
        )

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)

        /** Then **/
        assertEquals(NotificationManager.IMPORTANCE_LOW, PackageInstallTestFixture.notificationManager(context).getNotificationChannel("PackageInstall").importance)
        assertEquals(1, PackageInstallTestFixture.shadowNotificationManager(context).size())
    }

    /**
     * 채널이 없는 Android 8 미만에서 알림 우선순위가 LOW인지 검증
     *
     * Given: SDK 25, 런처 앱 설치
     * When: show 호출
     * Then: 알림 1개, 우선순위 LOW
     */
    @Test
    @Config(sdk = [25])
    fun test_19_belowOreoShowsLowPriority() = runTest {
        /** Given **/
        PackageInstallTestFixture.installLauncherApp(context)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)

        /** Then **/
        assertEquals(1, PackageInstallTestFixture.shadowNotificationManager(context).size())
        val notification = PackageInstallTestFixture.installNotification(context)
        assertEquals(NotificationCompat.PRIORITY_LOW, notification.priority)
    }
}
