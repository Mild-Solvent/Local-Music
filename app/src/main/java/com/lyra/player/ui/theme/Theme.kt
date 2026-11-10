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

@Composable
fun LyraTheme(mode: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = Type, content = content)
}
