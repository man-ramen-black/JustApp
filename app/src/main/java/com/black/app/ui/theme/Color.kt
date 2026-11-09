package com.black.app.ui.theme

import androidx.compose.ui.graphics.Color

/** 기존 colors.xml 다크 색상을 매핑한 Material3 고정 다크 테마 */
object BlackThemeColors {
    val WindowBackground = Color(0xFF000000)
    val Primary = Color(0xFF121212)
    val Accent = Color(0xFFDDDDDD)
    val TextSub = Color(0xFFBBBBBB)
    val TextHint = Color(0xFF888888)
}

/** 메인·도구 화면 공용 색 */
object AppColors {
    val ScreenBackground = Color(0xFF000000)
    val Title = Color(0xFFFFFFFF)
    val Caption = Color(0xFF595D64)
    val ItemName = Color(0xFFF5F5F5)
    val Icon = Color(0xFFD6D6D8)
    val ContainerBackground = Color(0xFF131316)
    val ContainerBorder = Color(0xFF222226)
    val Arrow = Color(0xFF43464D)
    val Divider = Color(0xFF1A1B1E)
    /** 밝은 상단바 미리보기 배경 */
    val LightPreviewBackground = Color(0xFFF2F2F4)
    /** 밝은 상단바 미리보기 아이콘 색 */
    val LightPreviewContent = Color(0xFF1A1A1C)
}
