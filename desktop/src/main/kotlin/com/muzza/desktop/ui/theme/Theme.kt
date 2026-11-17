package com.muzza.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

// ---- Cute water theme (same colours as the phone app) ----
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

private val DeepWater = darkColorScheme(
    primary = Color(0xFF9CC8FF),
    onPrimary = Color(0xFF08294F),
    primaryContainer = Color(0xFF1D4F80),
    onPrimaryContainer = Color(0xFFEAF6FB),
    inversePrimary = Color(0xFF2470DF),
    secondary = Color(0xFF9CC8FF),
    onSecondary = Color(0xFF08294F),
    secondaryContainer = Color(0xFF16375C),
    onSecondaryContainer = Color(0xFFEAF6FB),
    tertiary = Color(0xFFF6C445),
    onTertiary = Color(0xFF08294F),
    tertiaryContainer = Color(0xFF6B5418),
    onTertiaryContainer = Color(0xFFF8DC79),
    background = Color(0xFF0A2647),
    onBackground = Color(0xFFEAF6FB),
    surface = Color(0xFF0A2647),
    onSurface = Color(0xFFEAF6FB),
    surfaceVariant = Color(0xFF123A63),
    onSurfaceVariant = Color(0xFFBFDDF2),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFEAF6FB),
    inverseOnSurface = Color(0xFF08294F),
    surfaceBright = Color(0xFF1D4F80),
    surfaceDim = Color(0xFF071C36),
    surfaceContainerLowest = Color(0xFF071C36),
    surfaceContainerLow = Color(0xFF16375C),
    surfaceContainer = Color(0xFF16375C),
    surfaceContainerHigh = Color(0xFF1A4068),
    surfaceContainerHighest = Color(0xFF1D4F80),
    outline = Color(0xFF5D9BE0),
    outlineVariant = Color(0xFF1D4F80),
)

private fun font(name: String, w: Int): androidx.compose.ui.text.font.Font {
    val bytes = Thread.currentThread().contextClassLoader.getResourceAsStream("fonts/$name-$w.ttf")!!.use { it.readBytes() }
    return Font("$name-$w", bytes, FontWeight(w))
}

private val Fredoka by lazy { FontFamily(font("fredoka", 500), font("fredoka", 600), font("fredoka", 700)) }
private val Nunito by lazy { FontFamily(font("nunito", 400), font("nunito", 700), font("nunito", 800)) }

private val WaterType by lazy {
    Typography().let { t ->
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
}

@Composable
fun MuzzaTheme(nightSea: Boolean, content: @Composable () -> Unit) {
    Sea.dark = nightSea
    CompositionLocalProvider(LocalWater provides true) {
        MaterialTheme(colorScheme = if (nightSea) DeepWater else Water, typography = WaterType, content = content)
    }
}
