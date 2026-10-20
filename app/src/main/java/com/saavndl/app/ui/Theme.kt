package com.saavndl.app.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.foundation.shape.RoundedCornerShape

val Teal = Color(0xFF2DD4BF)
val TealDeep = Color(0xFF0F766E)
val Violet = Color(0xFF8B9DFF)

private val Dark = darkColorScheme(
    primary = Teal, onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF0F3D38), onPrimaryContainer = Color(0xFFB2F5EA),
    secondary = Violet, onSecondary = Color(0xFF101A4D),
    secondaryContainer = Color(0xFF232E6B), onSecondaryContainer = Color(0xFFDDE1FF),
    background = Color(0xFF0B0F14), onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0B0F14), onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1A222C), onSurfaceVariant = Color(0xFF9AA7B4),
    surfaceContainerLowest = Color(0xFF080B0F), surfaceContainerLow = Color(0xFF10161D),
    surfaceContainer = Color(0xFF141B23), surfaceContainerHigh = Color(0xFF1A222C),
    surfaceContainerHighest = Color(0xFF222B36),
    outline = Color(0xFF3A4755), outlineVariant = Color(0xFF26303B),
    error = Color(0xFFFF6B6B)
)

private val Amoled = Dark.copy(
    background = Color.Black, surface = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF080A0D),
    surfaceContainer = Color(0xFF0E1216), surfaceContainerHigh = Color(0xFF151A20),
    surfaceContainerHighest = Color(0xFF1C222A)
)

private val Light = lightColorScheme(
    primary = TealDeep, onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F5EA), onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4B5BD6), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE1FF), onSecondaryContainer = Color(0xFF0A1250),
    background = Color(0xFFF6FAF9), onBackground = Color(0xFF0E1A19),
    surface = Color(0xFFF6FAF9), onSurface = Color(0xFF0E1A19),
    surfaceVariant = Color(0xFFE1ECEA), onSurfaceVariant = Color(0xFF52625F),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F6F5),
    surfaceContainer = Color(0xFFE9F1F0), surfaceContainerHigh = Color(0xFFE1ECEA),
    surfaceContainerHighest = Color(0xFFD8E5E3),
    outline = Color(0xFF8A9996), outlineVariant = Color(0xFFC9D6D3)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun SaavnTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark", "amoled" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val scheme = when {
        mode == "amoled" -> Amoled
        mode == "dynamic" && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (view.context as Activity).window
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
