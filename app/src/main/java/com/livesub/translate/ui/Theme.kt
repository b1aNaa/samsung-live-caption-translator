package com.livesub.translate.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livesub.translate.settings.ThemeMode

// A restrained palette: near-neutral surfaces, one calm indigo accent, and
// colour saved for the hero. Fixed rather than wallpaper-derived so the app
// looks as designed on every phone.

private val Indigo = Color(0xFF4F5FE8)
private val IndigoLight = Color(0xFFA9B4FF)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EBFF),
    onPrimaryContainer = Color(0xFF1B2470),
    secondary = Color(0xFF14A38B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF5EF),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFF8B5CF6),
    tertiaryContainer = Color(0xFFEFE7FF),
    onTertiaryContainer = Color(0xFF2E1065),
    background = Color(0xFFF4F4F7),
    onBackground = Color(0xFF111217),
    surface = Color(0xFFF4F4F7),
    onSurface = Color(0xFF111217),
    surfaceVariant = Color(0xFFE6E7EC),
    onSurfaceVariant = Color(0xFF6B6E7B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0F0F4),
    surfaceContainerHigh = Color(0xFFEAEAEF),
    surfaceContainerHighest = Color(0xFFE3E3E9),
    outline = Color(0xFFB9BBC6),
    outlineVariant = Color(0xFFE7E7EC),
    error = Color(0xFFD23F3F),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF15206B),
    primaryContainer = Color(0xFF2A3380),
    onPrimaryContainer = Color(0xFFE3E6FF),
    secondary = Color(0xFF5FD9C2),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0D4A40),
    onSecondaryContainer = Color(0xFFD0F5EC),
    tertiary = Color(0xFFC4B5FD),
    tertiaryContainer = Color(0xFF3F2A7A),
    onTertiaryContainer = Color(0xFFEFE7FF),
    background = Color(0xFF0A0A0D),
    onBackground = Color(0xFFEDEDF2),
    surface = Color(0xFF0A0A0D),
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = Color(0xFF24252C),
    onSurfaceVariant = Color(0xFF9A9CA8),
    surfaceContainerLowest = Color(0xFF060608),
    surfaceContainerLow = Color(0xFF151519),
    surfaceContainer = Color(0xFF1B1B20),
    surfaceContainerHigh = Color(0xFF222228),
    surfaceContainerHighest = Color(0xFF2A2A31),
    outline = Color(0xFF4A4C57),
    outlineVariant = Color(0xFF24252B),
    error = Color(0xFFFF8A80),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        labelSmall = t.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/** Colours outside the Material scheme: status accents and the hero's night sky. */
@Immutable
data class ExtraColors(
    val ok: Color,
    val warn: Color,
    /** Hero base, top to bottom. */
    val heroTop: Color,
    val heroBottom: Color,
    /** Aurora glows drawn over the hero. */
    val glowA: Color,
    val glowB: Color,
    val onHero: Color,
    /** Hairline around cards. */
    val hairline: Color,
    val cardShadow: Color,
)

private val LightExtra = ExtraColors(
    ok = Color(0xFF1DA35A),
    warn = Color(0xFFE08A00),
    heroTop = Color(0xFF12142A),
    heroBottom = Color(0xFF1B1E3D),
    glowA = Color(0xFF5B6CFF),
    glowB = Color(0xFF22C7B0),
    onHero = Color.White,
    hairline = Color(0x14000000),
    cardShadow = Color(0x14101530),
)

private val DarkExtra = ExtraColors(
    ok = Color(0xFF4CD787),
    warn = Color(0xFFFFB648),
    heroTop = Color(0xFF14162C),
    heroBottom = Color(0xFF0E1020),
    glowA = Color(0xFF5B6CFF),
    glowB = Color(0xFF22C7B0),
    onHero = Color.White,
    hairline = Color(0x1AFFFFFF),
    cardShadow = Color(0x00000000),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

object AppTheme {
    val extra: ExtraColors
        @Composable get() = LocalExtraColors.current
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LiveSubTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode.isDark()
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
