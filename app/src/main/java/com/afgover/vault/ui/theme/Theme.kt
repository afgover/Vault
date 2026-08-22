package com.afgover.vault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Vault teması: gece mavisi zemin üzerine aydınlık peri mavisi birincil ve
 * kasa-altını vurgu (tertiary). Katmanlar (background → surface →
 * surfaceVariant → container) birbirinden ayırt edilir ama bağırmaz;
 * hata/uyarı renkleri tam çift olarak tanımlı. Aydınlık tema aynı kimliğin
 * gün ışığı hâlidir, soluk bir kopyası değil.
 */

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DAEFF),
    onPrimary = Color(0xFF0E1240),
    primaryContainer = Color(0xFF3A4AA8),
    onPrimaryContainer = Color(0xFFDEE1FF),
    secondary = Color(0xFFC3C8F2),
    onSecondary = Color(0xFF2B2F55),
    secondaryContainer = Color(0xFF41467A),
    onSecondaryContainer = Color(0xFFE1E1FF),
    tertiary = Color(0xFFE8C36A),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5A4400),
    onTertiaryContainer = Color(0xFFFFE08F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E0F1E),
    onBackground = Color(0xFFE5E6F2),
    surface = Color(0xFF14152A),
    onSurface = Color(0xFFE5E6F2),
    surfaceVariant = Color(0xFF262947),
    onSurfaceVariant = Color(0xFFC5C7E0),
    surfaceContainerLowest = Color(0xFF0B0C18),
    surfaceContainerLow = Color(0xFF161730),
    surfaceContainer = Color(0xFF1B1D36),
    surfaceContainerHigh = Color(0xFF22243F),
    surfaceContainerHighest = Color(0xFF2A2C4A),
    outline = Color(0xFF8F92AC),
    outlineVariant = Color(0xFF3C3F5E),
    inverseSurface = Color(0xFFE5E6F2),
    inverseOnSurface = Color(0xFF1B1D36),
    inversePrimary = Color(0xFF4356C9)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4356C9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF595E8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E1FF),
    onSecondaryContainer = Color(0xFF161B45),
    tertiary = Color(0xFF775A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08F),
    onTertiaryContainer = Color(0xFF251A00),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F7FE),
    onBackground = Color(0xFF191A28),
    surface = Color(0xFFFDFCFF),
    onSurface = Color(0xFF191A28),
    surfaceVariant = Color(0xFFE2E1F0),
    onSurfaceVariant = Color(0xFF45475C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F0FA),
    surfaceContainer = Color(0xFFEBEBF6),
    surfaceContainerHigh = Color(0xFFE5E5F1),
    surfaceContainerHighest = Color(0xFFDFDFEC),
    outline = Color(0xFF75778D),
    outlineVariant = Color(0xFFC5C7DD),
    inverseSurface = Color(0xFF2E2F3E),
    inverseOnSurface = Color(0xFFF0F0FA),
    inversePrimary = Color(0xFF9DAEFF)
)

/** Başlıklar bir tık daha karakterli, gövde ferah, etiketler okunur. */
private val VaultTypography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodySmall = t.bodySmall.copy(lineHeight = 18.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
    )
}

/** Kartlar belirgin yuvarlak, diyaloglar yumuşak, çipler hap. */
private val VaultShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = VaultTypography,
        shapes = VaultShapes,
        content = content
    )
}
