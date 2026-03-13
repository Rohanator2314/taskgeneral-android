package dev.rohans.taskwarrior.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Dark Theme Colors
private val DarkPrimary = Color(0xFFBB86FC)
private val DarkOnPrimary = Color(0xFF000000)
private val DarkPrimaryContainer = Color(0xFF3700B3)
private val DarkOnPrimaryContainer = Color(0xFFEDE7F6)
private val DarkSecondary = Color(0xFF8BE9FD)
private val DarkOnSecondary = Color(0xFF000000)
private val DarkSecondaryContainer = Color(0xFF004D40)
private val DarkOnSecondaryContainer = Color(0xFFB2DFDB)
private val DarkTertiary = Color(0xFFFFB86C)
private val DarkOnTertiary = Color(0xFF000000)
private val DarkTertiaryContainer = Color(0xFF4E342E)
private val DarkOnTertiaryContainer = Color(0xFFFFCC80)
private val DarkBackground = Color(0xFF0D1117)
private val DarkOnBackground = Color(0xFFC9D1D9)
private val DarkSurface = Color(0xFF161B22)
private val DarkOnSurface = Color(0xFFC9D1D9)
private val DarkSurfaceVariant = Color(0xFF21262D)
private val DarkOnSurfaceVariant = Color(0xFF8B949E)
private val DarkError = Color(0xFFFF5555)
private val DarkOutline = Color(0xFF30363D)

// Light Theme Colors (Technical/Warm Gray)
private val LightPrimary = Color(0xFF6200EE)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFBB86FC)
private val LightOnPrimaryContainer = Color(0xFF3700B3)
private val LightSecondary = Color(0xFF018786)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFB2DFDB)
private val LightOnSecondaryContainer = Color(0xFF004D40)
private val LightTertiary = Color(0xFFBF360C) // Burnt Orange
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFFFCC80)
private val LightOnTertiaryContainer = Color(0xFFBF360C)
private val LightBackground = Color(0xFFF0F0F0) // Light Gray
private val LightOnBackground = Color(0xFF121212)
private val LightSurface = Color(0xFFFFFFFF)
private val LightOnSurface = Color(0xFF121212)
private val LightSurfaceVariant = Color(0xFFE0E0E0)
private val LightOnSurfaceVariant = Color(0xFF424242)
private val LightError = Color(0xFFB00020)
private val LightOutline = Color(0xFF757575)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkError,
    onError = Color.Black,
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = LightError,
    onError = Color.White,
    outline = LightOutline
)

val TaskGeneralShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

val TaskGeneralTypography = Typography(
    // Slightly increase letter spacing for terminal feel
    bodyLarge = TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp
    ),
    labelMedium = TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    ),
    titleMedium = TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.3.sp
    )
)

@Composable
fun TaskGeneralTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = TaskGeneralShapes,
        typography = TaskGeneralTypography,
        content = content
    )
}
