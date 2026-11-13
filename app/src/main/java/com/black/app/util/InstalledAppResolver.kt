package com.black.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.black.app.ui.common.selectapp.SelectedAppUiItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 설치된 앱의 라벨·아이콘
 *
 * 화면 모델([SelectedAppUiItem])과 분리해 알림 같은 화면 밖 코드에서도 사용
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

/**
 * 설치 앱 라벨·아이콘 조회
 *
 * 알림 미리보기([resolve])와 실제 설치 알림([find])이 같은 조회를 쓰도록 한곳에 둠.
 * 조회는 PackageManager 호출이라 목록처럼 많은 앱을 조회하면 호출부가 IO 스레드에서 실행
 */
class InstalledAppResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** packageName → 설치 앱 라벨·아이콘 조회 (미설치 시 packageName·기본 아이콘 폴백) */
    fun resolve(packageName: String): SelectedAppUiItem {
        val installedApp = find(packageName)
        return SelectedAppUiItem(
            packageName = packageName,
            label = installedApp?.label ?: packageName,
            icon = installedApp?.icon ?: context.packageManager.defaultActivityIcon,
        )
    }

    /** [packageName] 앱의 라벨·아이콘 조회, 설치되지 않았으면 null */
    fun find(packageName: String): InstalledApp? {
        val applicationInfo = try {
            context.packageManager.getApplicationInfo(packageName, 0)
        } catch (notFound: PackageManager.NameNotFoundException) {
            return null
        }
        return find(applicationInfo)
    }

    /**
     * 이미 가진 [applicationInfo]로 라벨·아이콘 조회
     *
     * queryIntentActivities 결과처럼 [ApplicationInfo]가 있으면 패키지 정보를 다시 조회하지 않도록 사용
     */
    fun find(applicationInfo: ApplicationInfo): InstalledApp {
        val packageManager = context.packageManager
        return InstalledApp(
            packageName = applicationInfo.packageName,
            label = packageManager.getApplicationLabel(applicationInfo).toString(),
            icon = packageManager.getApplicationIcon(applicationInfo),
        )
    }
}
