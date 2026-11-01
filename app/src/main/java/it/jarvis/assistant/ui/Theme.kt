package it.jarvis.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object JColors {
    val Navy = Color(0xFF0A1015)
    val NavySurface = Color(0xFF101B23)
    val NavyCard = Color(0xFF172630)
    val Azure = Color(0xFF69E1D9)
    val Indigo = Color(0xFF8BBAFF)
    val Violet = Color(0xFFFFB6A0)
    val Ink = Color(0xFFF0F6F8)
    val Danger = Color(0xFFFF4D6A)
    val Ok = Color(0xFF38E8A0)
    val Warn = Color(0xFFFFC857)
}

private val Dark = darkColorScheme(
    primary = JColors.Azure, onPrimary = Color(0xFF00212E),
    secondary = JColors.Indigo, tertiary = JColors.Violet,
    primaryContainer = Color(0xFF16413F), onPrimaryContainer = Color(0xFFD6FFFA),
    background = JColors.Navy, onBackground = JColors.Ink,
    surface = JColors.NavySurface, onSurface = JColors.Ink,
    surfaceVariant = JColors.NavyCard, onSurfaceVariant = Color(0xFF9AB0BB),
    error = JColors.Danger, outline = Color(0xFF3D515C),
)
private val Light = lightColorScheme(
    primary = Color(0xFF0A6CFF), onPrimary = Color.White,
    secondary = Color(0xFF4A63D8), tertiary = Color(0xFF6B4BDB),
    background = Color(0xFFF2F7FF), onBackground = Color(0xFF0A1530),
    surface = Color.White, onSurface = Color(0xFF0A1530),
    surfaceVariant = Color(0xFFE3ECFB), onSurfaceVariant = Color(0xFF3D515C),
    error = Color(0xFFD22B4B), outline = Color(0xFF8DA2CF),
)

@Composable
fun JarvisTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    MaterialTheme(colorScheme = if (dark) Dark else Light) {
        androidx.compose.material3.Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, content = content)
    }
}

@Composable
fun screenBrush(): Brush {
    val dark = MaterialTheme.colorScheme.background == JColors.Navy
    return if (dark) Brush.verticalGradient(listOf(Color(0xFF11212A), JColors.Navy, Color(0xFF080E13)))
    else Brush.verticalGradient(listOf(Color(0xFFDDEBFF), Color(0xFFF2F7FF)))
}
