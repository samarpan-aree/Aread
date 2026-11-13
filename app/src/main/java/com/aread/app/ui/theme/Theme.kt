package com.aread.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AreadColors {
    val Paper = Color(0xFFFAF8F4)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1B1B1F)
    val Muted = Color(0xFF6B6B70)
    val Line = Color(0xFFE6E2DA)
    val Accent = Color(0xFF2F5D50)
    val AccentSoft = Color(0xFFE3ECE8)
    val Warn = Color(0xFFB4532A)
}

private val scheme = lightColorScheme(
    primary = AreadColors.Accent,
    onPrimary = Color.White,
    primaryContainer = AreadColors.AccentSoft,
    onPrimaryContainer = AreadColors.Accent,
    background = AreadColors.Paper,
    onBackground = AreadColors.Ink,
    surface = AreadColors.Surface,
    onSurface = AreadColors.Ink,
    surfaceVariant = AreadColors.Paper,
    onSurfaceVariant = AreadColors.Muted,
    outline = AreadColors.Line,
    error = AreadColors.Warn,
)

private val type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun AreadTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)
}
