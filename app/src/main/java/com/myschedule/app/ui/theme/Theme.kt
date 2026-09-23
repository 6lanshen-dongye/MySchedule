package com.myschedule.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF3B7CF0)
val Bg = Color(0xFFF4F6FA)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0B3B9E),
    secondary = Color(0xFF5A6B8C),
    onSecondary = Color.White,
    background = Bg,
    onBackground = Color(0xFF1B2032),
    surface = Color.White,
    onSurface = Color(0xFF1B2032),
    surfaceVariant = Color(0xFFEEF1F7),
    onSurfaceVariant = Color(0xFF667085),
    outlineVariant = Color(0xFFE3E8F0),
    error = Color(0xFFE53935)
)

/** 个人应用固定浅色主题,观感与超级课程表一致 */
@Composable
fun MyScheduleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
