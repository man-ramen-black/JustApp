package com.black.app.ui.common.selectapp

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * packageName으로 설치 앱 라벨·아이콘 조회
 *
 * 설치되지 않은 앱은 라벨 대신 packageName, 아이콘 대신 기본 아이콘을 씀.
 */
class InstalledAppResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * 앱 라벨 조회
     *
     * 아이콘은 조회하지 않으며, 설치되지 않은 앱은 packageName.
     */
    fun resolveLabel(packageName: String): String {
        val packageManager = context.packageManager
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (notFound: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    /** 앱 정보를 한 번 조회해 라벨과 아이콘을 함께 만든 항목 */
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
