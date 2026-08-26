package com.afgover.vault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sekuvo teması: pirinç birincil, sıcak nötrler üzerine.
 *
 * Renk markanın kendisiyle aynı: kategorideki her rakip mavi, pirinç ise
 * kasa donanımının rengi — ürünün ne olduğunu renk de söylüyor. Ton bilinçli
 * olarak kırık (parlak altın dolandırıcılık estetiğine kayıyor).
 *
 * Koyu tema **sıcak kömür**, siyah değil: zemin parlaklığı eski gece mavisinin
 * üç katı. Bir kasa uygulamasının ciddi olması gerekiyor ama günde on kez
 * açılan bir aracın karanlık bir kuyu gibi hissettirmesi gerekmiyor.
 * Aydınlık tema sıcak kâğıt; aynı kimliğin gün ışığı hâli, soluk kopyası değil.
 *
 * Üçüncül renk adaçayı yeşili: tek renkli bir palet monotonlaşırdı ve olumlu
 * durumların (doğrulandı, tamamlandı) pirinçten ayrışması gerekiyor.
 *
 * Tüm metin/zemin çiftleri WCAG AA ve üstü ölçüldü; en zayıf halka açık
 * temadaki pirinç metnin krem sayfa üzerindeki hâli (4,54:1).
 */

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF0C87A),
    onPrimary = Color(0xFF3F2E00),
    primaryContainer = Color(0xFF6B5210),
    onPrimaryContainer = Color(0xFFFFE7B0),
    secondary = Color(0xFFD5C4A4),
    onSecondary = Color(0xFF382F1A),
    secondaryContainer = Color(0xFF4F452E),
    onSecondaryContainer = Color(0xFFF2E0BF),
    tertiary = Color(0xFFB2CFB6),
    onTertiary = Color(0xFF1E3626),
    tertiaryContainer = Color(0xFF344D3B),
    onTertiaryContainer = Color(0xFFCEEBD3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF211D18),
    onBackground = Color(0xFFEDE3D3),
    surface = Color(0xFF302A23),
    onSurface = Color(0xFFEDE3D3),
    surfaceVariant = Color(0xFF4E463A),
    onSurfaceVariant = Color(0xFFD3C7B2),
    surfaceContainerLowest = Color(0xFF1A1713),
    surfaceContainerLow = Color(0xFF2C2721),
    surfaceContainer = Color(0xFF332D26),
    surfaceContainerHigh = Color(0xFF3B342C),
    surfaceContainerHighest = Color(0xFF463E35),
    outline = Color(0xFF9B9282),
    outlineVariant = Color(0xFF4E463A),
    inverseSurface = Color(0xFFEDE3D3),
    inverseOnSurface = Color(0xFF332E27),
    inversePrimary = Color(0xFF856520)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF856520),
    onPrimary = Color.White,
    // Dolu düğmelerin rengi. primary METİN olarak da kullanılıyor (59 metin/
    // çerçeve düğmesi rengini ondan alır) ve beyaz üzerinde okunması gerektiği
    // için koyu kalmak zorunda — o tonda büyük dolu yüzeyler zeytin gibi
    // görünüyordu. Dolgu bu yüzden ayrıldı: altın yüzey + koyu yazı (7,2:1).
    primaryContainer = Color(0xFFD9B45F),
    onPrimaryContainer = Color(0xFF241A00),
    secondary = Color(0xFF6A5C43),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E3C6),
    onSecondaryContainer = Color(0xFF241A07),
    tertiary = Color(0xFF3F5A47),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCEEBD3),
    onTertiaryContainer = Color(0xFF0A2013),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    // Sayfa sıcak kâğıt, İÇERİK beyaz: kayıt kartları, alanlar ve diyaloglar
    // (surfaceContainer*) beyaza oturur; krem zemin onları çerçeveler. Metin
    // beyaz üstünde okunur, kâğıt üstünde ezik durmuyor.
    background = Color.White,
    onBackground = Color(0xFF221D13),
    surface = Color.White,
    onSurface = Color(0xFF221D13),
    surfaceVariant = Color(0xFFEBE2D2),
    onSurfaceVariant = Color(0xFF4E4639),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,   // diyaloglar da beyaz
    surfaceContainerHighest = Color(0xFFF5EFE4),
    outline = Color(0xFF7F7767),
    outlineVariant = Color(0xFFD1C7B4),
    inverseSurface = Color(0xFF33302A),
    inverseOnSurface = Color(0xFFF6EFE3),
    inversePrimary = Color(0xFFF0C87A)
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
        shapes = VaultShapes
    ) {
        // Zemini BURADA boya. XML teması android:Theme.Material.NoActionBar'dan
        // türüyor ve onun pencere zemini sabit gri (#303030); Scaffold kullanan
        // ekranlar kendi zeminini çizdiği için bu görünmüyordu, ama kilit açma
        // ve PIN ekranları düz Column olduğu için altlarından o gri sızıyordu —
        // tema hangi renkte olursa olsun. Tek bir Surface bütün ekranları
        // Compose paletine bağlar.
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

/**
 * Dolu düğmelerin rengi: altın yüzey, koyu yazı.
 *
 * Material'ın varsayılanı `primary`yi kap rengi yapar; bu uygulamada primary
 * aynı zamanda metin rengi olduğu için koyu olmak zorunda ve dolu bir düğme
 * olarak ağır görünüyor. Kap `primaryContainer`a alınınca düğme altın oluyor,
 * yazının kontrastı da artıyor.
 */
@Composable
fun vaultButtonColors() = androidx.compose.material3.ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
)
