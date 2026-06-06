package com.black.app.ui.common.selectapp

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** packageName → 설치 앱 라벨·아이콘 조회 (미설치 시 packageName·기본 아이콘 폴백) */
class InstalledAppResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun resolve(packageName: String): SelectedAppUiItem {
        val packageManager = context.packageManager
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            SelectedAppUiItem(
                packageName = packageName,
                label = packageManager.getApplicationLabel(applicationInfo).toString(),
                icon = packageManager.getApplicationIcon(applicationInfo),
            )
        } catch (notFound: PackageManager.NameNotFoundException) {
            SelectedAppUiItem(
                packageName = packageName,
                label = packageName,
                icon = packageManager.defaultActivityIcon,
            )
        }
    }
}
