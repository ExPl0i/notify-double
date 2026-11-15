package com.notifydouble.receiver.ui.theme

// Palette, type scale and components follow the "Soft Fintech" design of leager-app
// (github.com/Expl0iD-Dev-Team/leager-app): rounded cards with hairline borders,
// a single accent used only as a fill, and the Manrope typeface.

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.notifydouble.receiver.R

@Immutable
data class ReceiverColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    /** Fills only (buttons, the status card, active states); never a text color. */
    val accent: Color,
    /** Text and icons drawn on top of [accent]. */
    val onAccent: Color,
    val accentSoft: Color,
    val danger: Color,
    val isDark: Boolean,
)

private val Mint = Color(0xFF3DDC97)
private val OnMint = Color(0xFF04140C)

private val DarkColors = ReceiverColors(
    bg = Color(0xFF0F1013),
    surface = Color(0xFF181A1F),
    surface2 = Color(0xFF22252B),
    border = Color(0x12FFFFFF),
    text = Color(0xFFF5F6F8),
    muted = Color(0xFF9BA0AA),
    faint = Color(0xFF5F646E),
    accent = Mint,
    onAccent = OnMint,
    accentSoft = Mint.copy(alpha = 0.16f),
    danger = Color(0xFFFF5C5C),
    isDark = true,
)

private val LightColors = ReceiverColors(
    bg = Color(0xFFF3F4F7),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEBEDF1),
    border = Color(0x140B0C0F),
    text = Color(0xFF111317),
    muted = Color(0xFF6A707C),
    faint = Color(0xFFA3A8B2),
    accent = Mint,
    onAccent = OnMint,
    accentSoft = Mint.copy(alpha = 0.22f),
    danger = Color(0xFFE5484D),
    isDark = false,
)

val LocalReceiverColors = staticCompositionLocalOf { DarkColors }

private val fontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val manrope = GoogleFont("Manrope")

/** Manrope has full Cyrillic coverage. Without Google Play services it falls back to the system font. */
val AppFont = FontFamily(
    Font(googleFont = manrope, fontProvider = fontsProvider, weight = FontWeight.Normal),
    Font(googleFont = manrope, fontProvider = fontsProvider, weight = FontWeight.Medium),
    Font(googleFont = manrope, fontProvider = fontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = manrope, fontProvider = fontsProvider, weight = FontWeight.Bold),
    Font(googleFont = manrope, fontProvider = fontsProvider, weight = FontWeight.ExtraBold),
)

private val AppTypography = Typography(
    displayMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.6).sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, lineHeight = 14.sp),
)

@Composable
fun ReceiverTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        background = colors.bg,
        surface = colors.surface,
        surfaceVariant = colors.surface2,
        onBackground = colors.text,
        onSurface = colors.text,
        onSurfaceVariant = colors.muted,
        outline = colors.border,
        error = colors.danger,
    )
    CompositionLocalProvider(LocalReceiverColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

val MaterialTheme.receiver: ReceiverColors
    @Composable get() = LocalReceiverColors.current
