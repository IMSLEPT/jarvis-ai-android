package it.jarvis.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object JColors {
    val Navy = Color(0xFF031030)
    val NavySurface = Color(0xFF091D42)
    val NavyCard = Color(0xFF102A50)
    val Azure = Color(0xFF45E6FA)
    val Indigo = Color(0xFF80B9FF)
    val Violet = Color(0xFFB8D9FF)
    val Ink = Color(0xFFF1F7FF)
    val Danger = Color(0xFFFF4D6A)
    val Ok = Color(0xFF38E8A0)
    val Warn = Color(0xFFFFC857)
}

private val Dark = darkColorScheme(
    primary = JColors.Azure, onPrimary = Color(0xFF002D43),
    secondary = JColors.Indigo, tertiary = JColors.Violet,
    primaryContainer = Color(0xFF104C70), onPrimaryContainer = Color(0xFFE1FBFF),
    background = JColors.Navy, onBackground = JColors.Ink,
    surface = JColors.NavySurface, onSurface = JColors.Ink,
    surfaceContainer = JColors.NavySurface, surfaceContainerHigh = JColors.NavyCard,
    inverseSurface = Color(0xFFE7F1FA), inverseOnSurface = JColors.Navy,
    surfaceVariant = JColors.NavyCard, onSurfaceVariant = Color(0xFFADC2DD),
    error = JColors.Danger, outline = Color(0xFF385575),
)
private val Light = lightColorScheme(
    primary = Color(0xFF006788), onPrimary = Color.White,
    secondary = Color(0xFF145FC4), tertiary = Color(0xFF367DA7),
    background = Color(0xFFF2F7FF), onBackground = Color(0xFF0A1530),
    surface = Color.White, onSurface = Color(0xFF0A1530),
    surfaceVariant = Color(0xFFE7F1FA), onSurfaceVariant = Color(0xFF385575),
    error = Color(0xFFD22B4B), outline = Color(0xFF8DA2CF),
)

@Composable
fun JarvisTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp),
    )) {
        androidx.compose.material3.Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, content = content)
    }
}

@Composable
fun screenBrush(): Brush {
    val dark = MaterialTheme.colorScheme.background == JColors.Navy
    return if (dark) Brush.verticalGradient(listOf(Color(0xFF0B2D59), JColors.Navy, Color(0xFF020A20)))
    else Brush.verticalGradient(listOf(Color(0xFFDCEFFC), Color(0xFFF2F7FF)))
}
