package com.black.app.ui.common.selectapp

import android.graphics.drawable.Drawable

/** 선택된 앱 한 건의 표시 모델 */
data class SelectedAppUiItem(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)
