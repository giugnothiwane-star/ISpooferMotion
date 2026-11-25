package com.chessoverlay.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette de la direction artistique : noir profond, graphite, violet électrique, bleu cyan. */
object Palette {
    val Abyss = Color(0xFF06060A)          // noir profond (fond)
    val Night = Color(0xFF0C0C13)
    val Graphite = Color(0xFF14141D)       // surfaces
    val GraphiteHigh = Color(0xFF1C1C28)
    val GraphiteLine = Color(0xFF2A2A3B)   // contours
    val Violet = Color(0xFF8B5CF6)         // violet électrique
    val VioletSoft = Color(0xFFA78BFA)
    val VioletDeep = Color(0xFF5B21B6)
    val Cyan = Color(0xFF22D3EE)           // bleu cyan
    val CyanSoft = Color(0xFF67E8F9)
    val TextPrimary = Color(0xFFF2F1F8)
    val TextSecondary = Color(0xFFB4B2C6)
    val TextMuted = Color(0xFF7D7A93)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Danger = Color(0xFFF87171)
    val Info = Color(0xFF60A5FA)

    val Accent: Brush get() = Brush.linearGradient(listOf(Violet, Cyan))
    val AccentSoft: Brush get() = Brush.linearGradient(listOf(Violet.copy(alpha = 0.55f), Cyan.copy(alpha = 0.55f)))
    val Backdrop: Brush
        get() = Brush.verticalGradient(listOf(Color(0xFF0D0B19), Abyss, Color(0xFF05080D)))
}

private val AppColors = darkColorScheme(
    primary = Palette.Violet,
    onPrimary = Color.White,
    primaryContainer = Palette.VioletDeep,
    onPrimaryContainer = Color.White,
    secondary = Palette.Cyan,
    onSecondary = Color(0xFF00151A),
    secondaryContainer = Color(0xFF0E3A44),
    onSecondaryContainer = Palette.CyanSoft,
    tertiary = Palette.VioletSoft,
    background = Palette.Abyss,
    onBackground = Palette.TextPrimary,
    surface = Palette.Graphite,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.GraphiteHigh,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainer = Palette.Graphite,
    surfaceContainerHigh = Palette.GraphiteHigh,
    surfaceContainerHighest = Color(0xFF232332),
    surfaceContainerLow = Palette.Night,
    surfaceContainerLowest = Palette.Abyss,
    outline = Palette.GraphiteLine,
    outlineVariant = Color(0xFF22222F),
    error = Palette.Danger,
    onError = Color.Black,
)

private val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ChessOverlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppTypography, shapes = AppShapes) {
        // Couleur de texte par défaut claire (pas de Surface autour des écrans à fond dégradé).
        CompositionLocalProvider(LocalContentColor provides Palette.TextPrimary, content = content)
    }
}
