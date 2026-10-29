package com.black.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

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
