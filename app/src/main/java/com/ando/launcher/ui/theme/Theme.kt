package com.ando.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AndoBackground = Color(0xFF0B0B0F)
val AndoSurface = Color(0xFF16161C)
val AndoSurfaceVariant = Color(0xFF1F1F27)
val AndoOnSurface = Color(0xFFF2F2F5)
val AndoOnSurfaceMuted = Color(0xFF9A9AA5)

private val AndoDarkScheme = darkColorScheme(
    background = AndoBackground,
    surface = AndoSurface,
    surfaceVariant = AndoSurfaceVariant,
    onBackground = AndoOnSurface,
    onSurface = AndoOnSurface,
    onSurfaceVariant = AndoOnSurfaceMuted,
    primary = Color(0xFFFF7A1A),
)

@Composable
fun AndoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AndoDarkScheme,
        typography = AndoTypography,
        content = content,
    )
}
