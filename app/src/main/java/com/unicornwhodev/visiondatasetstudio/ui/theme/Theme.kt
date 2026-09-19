package com.unicornwhodev.visiondatasetstudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val StudioCyan = Color(0xFF6ED6DA)
val StudioViolet = Color(0xFFAB9DEE)
private val DarkStudio = darkColorScheme(
    primary = StudioCyan, onPrimary = Color(0xFF06282D),
    primaryContainer = Color(0xFF12383F), onPrimaryContainer = Color(0xFFACF4F3),
    secondary = StudioViolet, onSecondary = Color(0xFF241B4D),
    secondaryContainer = Color(0xFF2C2648), onSecondaryContainer = Color(0xFFE4DDFF),
    tertiary = Color(0xFFFFC782), onTertiary = Color(0xFF432807),
    tertiaryContainer = Color(0xFF43321D), onTertiaryContainer = Color(0xFFFFDCA6),
    background = Color(0xFF101217), onBackground = Color(0xFFF1F3FA),
    surface = Color(0xFF171A21), onSurface = Color(0xFFF1F3FA),
    surfaceVariant = Color(0xFF242932), onSurfaceVariant = Color(0xFFA3ADC2),
    surfaceContainerLowest = Color(0xFF0C0E12), surfaceContainerLow = Color(0xFF171A21),
    surfaceContainer = Color(0xFF1C2028), surfaceContainerHigh = Color(0xFF242932), surfaceContainerHighest = Color(0xFF2C323E),
    outline = Color(0xFF74819A), outlineVariant = Color(0xFF303641),
    error = Color(0xFFFF9AAB), errorContainer = Color(0xFF46202E), onErrorContainer = Color(0xFFFFD9E1)
)
private val LightStudio = lightColorScheme(
    primary = Color(0xFF086973), onPrimary = Color.White,
    primaryContainer = Color(0xFFC4F3F3), onPrimaryContainer = Color(0xFF083940),
    secondary = Color(0xFF6550AB), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E1FF), onSecondaryContainer = Color(0xFF342267),
    background = Color(0xFFF5F6FB), onBackground = Color(0xFF172033),
    surface = Color(0xFFFCFCFF), onSurface = Color(0xFF172033),
    surfaceVariant = Color(0xFFE9EDF5), onSurfaceVariant = Color(0xFF546179),
    surfaceContainer = Color(0xFFEEF1F8), surfaceContainerHigh = Color(0xFFE8ECF5),
    outline = Color(0xFF74819A), outlineVariant = Color(0xFFD9DFEC)
)

@Composable
fun VisionDatasetStudioTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkStudio else LightStudio, typography = Typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(20.dp)),
        content = content)
}
