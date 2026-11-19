package com.black.feature.appinstallnotification.notification

import android.app.Notification
import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import com.black.test.NotificationTestUtil
import com.black.test.TestUtil
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowShortcutManager

/**
 * 알림 대상 앱 설치 상태·설치 알림·바로가기를 Robolectric 섀도에 구성하고 조회하는 테스트 헬퍼
 *
 * 알림 권한과 게시된 알림의 일반 조회는 [NotificationTestUtil]이 맡음
 */
object PackageInstallTestFixture {
    const val LAUNCHER_PACKAGE = "com.example.launcher"
    const val LAUNCHER_LABEL = "Launcher Sample"
    const val LAUNCHER_ACTIVITY = "com.example.launcher.MainActivity"

    /** 라벨·아이콘·런처 액티비티를 가진 앱 설치 */
    fun installLauncherApp(
        context: Context,
        packageName: String = LAUNCHER_PACKAGE,
        label: String = LAUNCHER_LABEL,
    ) {
        TestUtil.installApp(context, packageName, label)
        TestUtil.addLauncherActivity(context, packageName, "$packageName.MainActivity")
    }

    /** [PackageInstallNotifier]의 패키지별 ID 규칙으로 [packageName] 앱의 설치 알림 조회, 없으면 실패 */
    fun installNotification(context: Context, packageName: String = LAUNCHER_PACKAGE): Notification =
        requireNotNull(NotificationTestUtil.shadowNotificationManager(context).getNotification(PackageInstallNotifier.notificationId(packageName))) {
            "install notification not found : $packageName"
        }

    /** JustApp이 게시한 동적 바로가기 목록 조회 */
    fun dynamicShortcuts(context: Context): List<ShortcutInfo> =
        context.getSystemService(ShortcutManager::class.java).dynamicShortcuts

    /** ShortcutManager 섀도 조회 */
    fun shadowShortcutManager(context: Context): ShadowShortcutManager =
        shadowOf(context.getSystemService(ShortcutManager::class.java))
}
