package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val RentFlowDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkTextPrimary,
    secondary = EmeraldLight,
    onSecondary = OnEmeraldContainer,
    secondaryContainer = ColorTokens.DarkEmeraldContainer,
    onSecondaryContainer = EmeraldContainer,
    tertiary = EmeraldLight,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = ColorTokens.DarkOutline,
    error = StatusError,
    errorContainer = ColorTokens.DarkErrorContainer
)

private val RentFlowLightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = CrispWhite,
    primaryContainer = NavyContainer,
    onPrimaryContainer = OnNavyContainer,
    secondary = EmeraldAccent,
    onSecondary = CrispWhite,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = NavySecondary,
    background = SoftNeutralBg,
    onBackground = TextPrimaryLight,
    surface = CrispWhite,
    onSurface = TextPrimaryLight,
    surfaceVariant = SoftSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = SlateBorder,
    error = StatusError,
    errorContainer = StatusErrorBg
)

private object ColorTokens {
    val DarkEmeraldContainer = androidx.compose.ui.graphics.Color(0xFF0F3D39)
    val DarkOutline = androidx.compose.ui.graphics.Color(0xFF283C5A)
    val DarkErrorContainer = androidx.compose.ui.graphics.Color(0xFF45161B)
}

val RentFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RentFlowTypography,
        shapes = RentFlowShapes,
        content = content
    )
}
