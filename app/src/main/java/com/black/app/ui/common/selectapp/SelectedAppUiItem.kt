package com.black.app.ui.common.selectapp

import android.graphics.drawable.Drawable
import com.black.app.util.InstalledAppResolver

/** 선택된 앱 한 건의 표시 모델 */
data class SelectedAppUiItem(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

/**
 * [packageName] 앱을 [InstalledAppResolver.find]로 찾아 만든 표시 모델
 *
 * 설치되지 않은 앱(선택한 뒤 삭제된 앱 등)도 목록에서 빠지지 않도록 packageName을 라벨로, 기기 기본 앱 아이콘을 아이콘으로 표시
 */
fun InstalledAppResolver.selectedAppUiItem(packageName: String): SelectedAppUiItem {
    val installedApp = find(packageName)
    return SelectedAppUiItem(
        packageName = packageName,
        label = installedApp?.label ?: packageName,
        icon = installedApp?.icon ?: defaultIcon(),
    )
}
