package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val RentFlowDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkTextPrimary,
    secondary = CobaltLight,
    onSecondary = MidnightInk,
    secondaryContainer = DarkCobaltContainer,
    onSecondaryContainer = CobaltContainer,
    tertiary = BrushedSilver,
    onTertiary = MidnightInk,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutline.copy(alpha = 0.72f),
    error = StatusErrorLight,
    errorContainer = StatusErrorBgDark,
    onErrorContainer = StatusErrorBg
)

private val RentFlowLightColorScheme = lightColorScheme(
    primary = MidnightInk,
    onPrimary = PaperWhite,
    primaryContainer = MidnightInkContainer,
    onPrimaryContainer = OnMidnightInkContainer,
    secondary = MutedCobalt,
    onSecondary = PaperWhite,
    secondaryContainer = CobaltContainer,
    onSecondaryContainer = OnCobaltContainer,
    tertiary = MidnightSlate,
    onTertiary = PaperWhite,
    background = MistBlueBg,
    onBackground = TextPrimaryLight,
    surface = IceCardSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SlateBlueContainer,
    onSurfaceVariant = TextSecondaryLight,
    outline = CoolSlateBorder,
    outlineVariant = CoolSlateBorder.copy(alpha = 0.72f),
    error = StatusError,
    errorContainer = StatusErrorBg,
    onErrorContainer = StatusError
)

val RentFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RentFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) RentFlowDarkColorScheme else RentFlowLightColorScheme
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RentFlowTypography,
            shapes = RentFlowShapes,
            content = content
        )
    }
}
