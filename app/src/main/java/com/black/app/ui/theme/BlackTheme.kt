package com.black.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 기존 colors.xml 다크 색상을 매핑한 Material3 고정 다크 테마 */
object BlackThemeColors {
    val WindowBackground = Color(0xFF000000)
    val Primary = Color(0xFF121212)
    val Accent = Color(0xFFDDDDDD)
    val TextSub = Color(0xFFBBBBBB)
    val TextHint = Color(0xFF888888)
}

private val blackColorScheme = darkColorScheme(
    primary = BlackThemeColors.Primary,
    onPrimary = BlackThemeColors.Accent,
    secondary = BlackThemeColors.Primary,
    onSecondary = BlackThemeColors.Accent,
    background = BlackThemeColors.WindowBackground,
    onBackground = BlackThemeColors.Accent,
    surface = BlackThemeColors.WindowBackground,
    onSurface = BlackThemeColors.Accent,
    surfaceVariant = BlackThemeColors.Primary,
    onSurfaceVariant = BlackThemeColors.TextSub,
    outline = BlackThemeColors.TextHint,
)

@Composable
fun BlackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = blackColorScheme,
        content = content,
    )
}
