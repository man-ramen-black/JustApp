package com.black.app.broadcast

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import com.black.app.R
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_ACTIVITY
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_LABEL
import com.black.app.broadcast.PackageInstallTestFixture.LAUNCHER_PACKAGE
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [PackageInstallNotifier]의 알림 내용·실행 인텐트와 알림 제외 조건 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PackageInstallNotifierTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * 새로 설치된 런처 앱의 알림 내용 검증
     *
     * Given: 알림 권한 허용, 런처 앱 설치
     * When: isUpdate=false로 show 호출
     * Then: 알림 1개, 제목=앱 이름, 메시지=설치되었습니다., 채널 PackageInstall(이름, 중요도), small icon ic_app_install, large icon 축소 크기, 자동 취소
     */
    @Test
    fun test_01_newLauncherAppShowsInstalledNotification() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        PackageInstallTestFixture.installLauncherApp(context)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = false)

        /** Then **/
        assertEquals(1, PackageInstallTestFixture.shadowNotificationManager(context).size())
        val notification = PackageInstallTestFixture.shadowNotificationManager(context).getNotification(LAUNCHER_PACKAGE.hashCode())
        assertEquals(LAUNCHER_LABEL, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("설치되었습니다.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("PackageInstall", notification.channelId)
        val channel = notificationManager().getNotificationChannel("PackageInstall")
        assertEquals("앱 설치·업데이트", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals(R.drawable.ic_app_install, notification.smallIcon.resId)
        val largeIcon = (notification.getLargeIcon().loadDrawable(context) as BitmapDrawable).bitmap
        // Notification.Builder.build()가 large icon을 비공개 리소스 notification_right_icon_size(48dp)로 축소, android.R에 없어 하드코딩
        val reducedLargeIconSize = (48 * context.resources.displayMetrics.density).toInt()
        assertEquals(reducedLargeIconSize, largeIcon.width)
        assertEquals(reducedLargeIconSize, largeIcon.height)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
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
        val notification = PackageInstallTestFixture.shadowNotificationManager(context).getNotification(LAUNCHER_PACKAGE.hashCode())
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
     * Then: 메시지=업데이트되었습니다., small icon ic_app_update
     */
    @Test
    fun test_03_updatedAppShowsUpdatedMessage() = runTest {
        /** Given **/
        PackageInstallTestFixture.grantNotificationPermission(context)
        PackageInstallTestFixture.installLauncherApp(context)

        /** When **/
        PackageInstallNotifier.show(context, LAUNCHER_PACKAGE, isUpdate = true)

        /** Then **/
        val notification = PackageInstallTestFixture.shadowNotificationManager(context).getNotification(LAUNCHER_PACKAGE.hashCode())
        assertEquals("업데이트되었습니다.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(R.drawable.ic_app_update, notification.smallIcon.resId)
    }

    /**
     * 런처 액티비티 없는 앱은 알림 제외 검증
     *
     * Given: 알림 권한 허용, 런처 액티비티 없는 앱 설치
     * When: show 호출
     * Then: 알림 없음
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
     * Then: 알림 없음
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
    }

    /**
     * 같은 앱 연속 통지 시 앞선 알림 대체 검증
     *
     * Given: 알림 권한 허용, 런처 앱 설치
     * When: 설치 후 업데이트로 show 2회 호출
     * Then: 알림 1개, 메시지=업데이트되었습니다.
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
        val notification = PackageInstallTestFixture.shadowNotificationManager(context).getNotification(LAUNCHER_PACKAGE.hashCode())
        assertEquals("업데이트되었습니다.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    /**
     * 서로 다른 앱은 각자 알림 표시 검증
     *
     * Given: 알림 권한 허용, 런처 앱 2개 설치
     * When: 두 앱에 대해 show 호출
     * Then: 알림 2개, 각 패키지 ID에 해당 앱 이름
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
        val firstNotification = PackageInstallTestFixture.shadowNotificationManager(context).getNotification(LAUNCHER_PACKAGE.hashCode())
        val secondNotification = PackageInstallTestFixture.shadowNotificationManager(context).getNotification(secondPackage.hashCode())
        assertEquals(LAUNCHER_LABEL, firstNotification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(secondLabel, secondNotification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
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

    /** NotificationManager 조회 */
    private fun notificationManager(): NotificationManager =
        context.getSystemService(NotificationManager::class.java)
}
