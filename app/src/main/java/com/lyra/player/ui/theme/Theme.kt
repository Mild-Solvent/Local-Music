package com.lyra.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.lyra.player.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lyra.player.data.ThemeMode

val Violet = Color(0xFFB58CFF)
val Rose = Color(0xFFFF6FA8)

private val Dark = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF22104A),
    primaryContainer = Color(0xFF3B2470),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Rose,
    onSecondary = Color(0xFF4A0024),
    secondaryContainer = Color(0xFF5E1F3D),
    onSecondaryContainer = Color(0xFFFFD9E4),
    tertiary = Color(0xFF7FD8C8),
    background = Color(0xFF0F0D16),
    onBackground = Color(0xFFE8E1F0),
    surface = Color(0xFF0F0D16),
    onSurface = Color(0xFFE8E1F0),
    surfaceVariant = Color(0xFF2A2536),
    onSurfaceVariant = Color(0xFFC9C0D6),
    surfaceContainerLowest = Color(0xFF0A0910),
    surfaceContainerLow = Color(0xFF16131F),
    surfaceContainer = Color(0xFF1B1825),
    surfaceContainerHigh = Color(0xFF242030),
    surfaceContainerHighest = Color(0xFF2E293B),
    outline = Color(0xFF7A7287),
    outlineVariant = Color(0xFF3A3446),
)

private val Light = lightColorScheme(
    primary = Color(0xFF6A3FD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF22004F),
    secondary = Color(0xFFC2185B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E4),
    onSecondaryContainer = Color(0xFF3E0020),
    tertiary = Color(0xFF00897B),
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
    surfaceContainerLow = Color(0xFFF5F1FB),
    surfaceContainer = Color(0xFFEFEAF6),
    surfaceContainerHigh = Color(0xFFE9E4F1),
    surfaceContainerHighest = Color(0xFFE3DEEB),
)

private val Type = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    )
}

// ---- Cute water theme ----
private val Water = lightColorScheme(
    primary = Color(0xFF1B4FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF74D0E0),
    onPrimaryContainer = Color(0xFF08294F),
    inversePrimary = Color(0xFF74D0E0),
    secondary = Color(0xFF1B4FA8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFAF2E0),
    onSecondaryContainer = Color(0xFF2470DF),
    tertiary = Color(0xFFF6C445),
    onTertiary = Color(0xFF08294F),
    tertiaryContainer = Color(0xFFF8DC79),
    onTertiaryContainer = Color(0xFF08294F),
    background = Color(0xFF049DEB),
    onBackground = Color(0xFF08294F),
    surface = Color(0xFF049DEB),
    onSurface = Color(0xFF08294F),
    surfaceVariant = Color(0xFFCBE6E1),
    onSurfaceVariant = Color(0xFF08294F),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF08294F),
    inverseOnSurface = Color(0xFFFAF2E0),
    surfaceBright = Color(0xFFFFFBF0),
    surfaceDim = Color(0xFFCBE6E1),
    surfaceContainerLowest = Color(0xFFFFFBF0),
    surfaceContainerLow = Color(0xFFFAF2E0),
    surfaceContainer = Color(0xFFFAF2E0),
    surfaceContainerHigh = Color(0xFFF3EBD6),
    surfaceContainerHighest = Color(0xFFCBE6E1),
    outline = Color(0xFF2470DF),
    outlineVariant = Color(0xFF74D0E0),
    error = Color(0xFFB3261E),
)

@OptIn(ExperimentalTextApi::class)
private fun vf(res: Int, w: Int) = Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

private val Fredoka = FontFamily(vf(R.font.fredoka, 500), vf(R.font.fredoka, 600), vf(R.font.fredoka, 700))
private val Nunito = FontFamily(vf(R.font.nunito, 400), vf(R.font.nunito, 600), vf(R.font.nunito, 700), vf(R.font.nunito, 800))

private val WaterType = Typography().let { t ->
    fun TextStyle.round(w: Int) = copy(fontFamily = Fredoka, fontWeight = FontWeight(w))
    fun TextStyle.soft(w: Int) = copy(fontFamily = Nunito, fontWeight = FontWeight(w))
    t.copy(
        displayLarge = t.displayLarge.round(700), displayMedium = t.displayMedium.round(700), displaySmall = t.displaySmall.round(700),
        headlineLarge = t.headlineLarge.round(700), headlineMedium = t.headlineMedium.round(700), headlineSmall = t.headlineSmall.round(700),
        titleLarge = t.titleLarge.round(600), titleMedium = t.titleMedium.soft(800), titleSmall = t.titleSmall.soft(800),
        bodyLarge = t.bodyLarge.soft(700), bodyMedium = t.bodyMedium.soft(700), bodySmall = t.bodySmall.soft(700),
        labelLarge = t.labelLarge.soft(800), labelMedium = t.labelMedium.soft(800), labelSmall = t.labelSmall.soft(800),
    )
}

@Composable
fun LyraTheme(mode: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    if (mode == ThemeMode.WATER) {
        CompositionLocalProvider(LocalWater provides true) { MaterialTheme(colorScheme = Water, typography = WaterType, content = content) }
        return
    }
    val dark = when (mode) { ThemeMode.SYSTEM, ThemeMode.WATER -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = Type, content = content)
}
