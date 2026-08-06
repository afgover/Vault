package com.afgover.vault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B9EFF),
    onPrimary = Color(0xFF10123A),
    secondary = Color(0xFFB8C2FF),
    background = Color(0xFF12131F),
    surface = Color(0xFF1A1B2E),
    surfaceVariant = Color(0xFF2A2C45),
    onBackground = Color(0xFFEAEAF2),
    onSurface = Color(0xFFEAEAF2)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4356C9),
    onPrimary = Color.White,
    secondary = Color(0xFF5B6BD6),
    background = Color(0xFFF6F6FB),
    surface = Color.White
)

@Composable
fun VaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
