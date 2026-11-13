package com.black.app.broadcast

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowShortcutManager

/** 알림 대상 앱 설치 상태·알림 권한을 Robolectric 섀도에 구성하는 테스트 헬퍼 */
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
        installApp(context, packageName, label)
        addLauncherActivity(context, packageName, "$packageName.MainActivity")
    }

    /** 런처 액티비티 없이 라벨·아이콘만 가진 앱 설치 */
    fun installApp(context: Context, packageName: String, label: String) {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    nonLocalizedLabel = label
                }
            }
        )
        setAppIcon(context, packageName)
    }

    /**
     * 이미 있는 [packageName] 앱에 아이콘 지정
     *
     * 패키지 정보를 다시 설치하지 않으므로 자기 앱처럼 덮어쓰면 안 되는 패키지에도 사용
     */
    fun setAppIcon(context: Context, packageName: String) {
        // Robolectric은 아이콘 미지정 시 null 반환(실기기는 기본 아이콘 반환)
        shadowOf(context.packageManager).setApplicationIcon(packageName, ColorDrawable(Color.RED))
    }

    /**
     * MAIN·LAUNCHER 인텐트 필터를 가진 액티비티 추가
     *
     * 액티비티가 이미 있으면 인텐트 필터만 추가
     */
    fun addLauncherActivity(context: Context, packageName: String, activityName: String) {
        val shadowPackageManager = shadowOf(context.packageManager)
        val component = ComponentName(packageName, activityName)
        shadowPackageManager.addActivityIfNotPresent(component)
        shadowPackageManager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        )
    }

    /** Android 13 이상 알림 권한 허용 */
    fun grantNotificationPermission(context: Context) {
        shadowOf(context.applicationContext as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Android 13 이상 알림 권한 거부 */
    fun denyNotificationPermission(context: Context) {
        shadowOf(context.applicationContext as Application)
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** NotificationManager 섀도 조회 */
    fun shadowNotificationManager(context: Context): ShadowNotificationManager =
        shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

    /** JustApp이 게시한 동적 바로가기 목록 조회 */
    fun dynamicShortcuts(context: Context): List<ShortcutInfo> =
        context.getSystemService(ShortcutManager::class.java).dynamicShortcuts

    /** ShortcutManager 섀도 조회 */
    fun shadowShortcutManager(context: Context): ShadowShortcutManager =
        shadowOf(context.getSystemService(ShortcutManager::class.java))
}
