package com.unicornwhodev.visiondatasetstudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightStudio = lightColorScheme(
    primary = Color(0xFF186858), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F2E9), onPrimaryContainer = Color(0xFF083B30),
    secondary = Color(0xFF53645D), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EBE6), onSecondaryContainer = Color(0xFF24362D),
    tertiary = Color(0xFF765B1D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDC3), onTertiaryContainer = Color(0xFF4B390C),
    background = Color(0xFFF6F8F5), onBackground = Color(0xFF1B2420),
    surface = Color(0xFFFCFDFB), onSurface = Color(0xFF1B2420),
    surfaceVariant = Color(0xFFEBEFEB), onSurfaceVariant = Color(0xFF505D55),
    outline = Color(0xFF76847B), outlineVariant = Color(0xFFD8DFD8)
)
private val DarkStudio = darkColorScheme(
    primary = Color(0xFF9FDAC5), onPrimary = Color(0xFF073C2F),
    primaryContainer = Color(0xFF234E40), onPrimaryContainer = Color(0xFFD3F5E6),
    secondary = Color(0xFFBDCCC1), onSecondary = Color(0xFF28382E),
    secondaryContainer = Color(0xFF34483C), onSecondaryContainer = Color(0xFFDAE8DD),
    tertiary = Color(0xFFE5C676), onTertiary = Color(0xFF403207),
    tertiaryContainer = Color(0xFF584519), onTertiaryContainer = Color(0xFFFFEABD),
    background = Color(0xFF111815), onBackground = Color(0xFFE4EBE5),
    surface = Color(0xFF17201B), onSurface = Color(0xFFE4EBE5),
    surfaceVariant = Color(0xFF25332B), onSurfaceVariant = Color(0xFFC2CEC5),
    outline = Color(0xFF89988E), outlineVariant = Color(0xFF3C4B41)
)

@Composable
fun VisionDatasetStudioTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkStudio else LightStudio,
        typography = Typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content
    )
}
