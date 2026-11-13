package com.aread.app.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aread.app.data.Prefs

@Immutable
data class Palette(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentSoft: Color,
    val warn: Color,
    val amber: Color,
    val dark: Boolean,
)

val AccentChoices = listOf(
    Color(0xFF2F6B58), Color(0xFF3F51B5), Color(0xFFC4572C), Color(0xFF7B3F8C), Color(0xFF1F6F9F),
)
val AccentNames = listOf("Forest", "Indigo", "Clay", "Plum", "Ocean")

fun buildPalette(dark: Boolean, accentIndex: Int): Palette {
    val base = AccentChoices[accentIndex.coerceIn(0, AccentChoices.lastIndex)]
    return if (dark) {
        val paper = Color(0xFF121413)
        val accent = lerp(base, Color.White, 0.3f)
        Palette(
            paper, Color(0xFF1B1E1D), Color(0xFFF2F0EB), Color(0xFF9A9CA0), Color(0xFF2B2F2D),
            accent, lerp(paper, accent, 0.2f), Color(0xFFE0825A), Color(0xFFF2A93B), true,
        )
    } else {
        val paper = Color(0xFFFAF8F4)
        Palette(
            paper, Color.White, Color(0xFF1B1B1F), Color(0xFF6B6B70), Color(0xFFE6E2DA),
            base, lerp(paper, base, 0.13f), Color(0xFFB4532A), Color(0xFFE69A1F), false,
        )
    }
}

val LocalPalette = staticCompositionLocalOf { buildPalette(false, 0) }

object AreadColors {
    val Paper: Color @Composable get() = LocalPalette.current.paper
    val Surface: Color @Composable get() = LocalPalette.current.surface
    val Ink: Color @Composable get() = LocalPalette.current.ink
    val Muted: Color @Composable get() = LocalPalette.current.muted
    val Line: Color @Composable get() = LocalPalette.current.line
    val Accent: Color @Composable get() = LocalPalette.current.accent
    val AccentSoft: Color @Composable get() = LocalPalette.current.accentSoft
    val Warn: Color @Composable get() = LocalPalette.current.warn
    val Amber: Color @Composable get() = LocalPalette.current.amber
}

object ThemeState {
    var mode by mutableIntStateOf(0)
    var accent by mutableIntStateOf(0)
    var bgPath by mutableStateOf<String?>(null)
    var avatarPath by mutableStateOf<String?>(null)

    fun load(c: Context) {
        mode = Prefs.themeMode(c)
        accent = Prefs.accent(c)
        bgPath = Prefs.bgPath(c)
        avatarPath = Prefs.avatarPath(c)
    }
}

private val type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun AreadTheme(content: @Composable () -> Unit) {
    val dark = when (ThemeState.mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val palette = remember(dark, ThemeState.accent) { buildPalette(dark, ThemeState.accent) }
    val onAccent = if (dark) Color(0xFF0E1110) else Color.White
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent, onPrimary = onAccent, primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.accent, background = palette.paper, onBackground = palette.ink,
            surface = palette.surface, onSurface = palette.ink, surfaceVariant = palette.paper,
            onSurfaceVariant = palette.muted, outline = palette.line, error = palette.warn,
            surfaceContainer = palette.surface,
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = onAccent, primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.accent, background = palette.paper, onBackground = palette.ink,
            surface = palette.surface, onSurface = palette.ink, surfaceVariant = palette.paper,
            onSurfaceVariant = palette.muted, outline = palette.line, error = palette.warn,
            surfaceContainer = palette.surface,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)
    }
}
