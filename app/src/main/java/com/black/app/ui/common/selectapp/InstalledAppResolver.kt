package com.black.app.ui.common.selectapp

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** packageName → 설치 앱 라벨·아이콘 조회 (미설치 시 packageName·기본 아이콘 폴백) */
class InstalledAppResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 라벨만 조회(아이콘 조회 없음, 미설치 시 packageName) */
    fun resolveLabel(packageName: String): String {
        val packageManager = context.packageManager
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (notFound: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun resolve(packageName: String): SelectedAppUiItem {
        val packageManager = context.packageManager
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            SelectedAppUiItem(
                packageName = packageName,
                label = resolveLabel(packageName),
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
