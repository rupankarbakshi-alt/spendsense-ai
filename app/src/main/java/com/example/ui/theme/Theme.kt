package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.preferences.ThemeMode

private val SpendSenseDarkColorScheme = darkColorScheme(
    primary = EmeraldAccent,
    onPrimary = Color(0xFF03261A),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldSoft,
    secondary = CyanFin,
    onSecondary = NavyDeep,
    secondaryContainer = NavyCardElevated,
    onSecondaryContainer = CrispWhite,
    tertiary = AmberAlert,
    onTertiary = NavyDeep,
    background = NavyDeep,
    onBackground = CrispWhite,
    surface = NavyPrimary,
    onSurface = CrispWhite,
    surfaceVariant = NavyCard,
    onSurfaceVariant = SlateMuted,
    surfaceContainerLow = NavyPrimary,
    surfaceContainer = NavyCard,
    surfaceContainerHigh = NavyCardElevated,
    outline = NavyBorder,
    outlineVariant = NavyBorder,
    error = CoralDanger,
    onError = NavyDeep
)

private val SpendSenseLightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = NavyPrimary,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = NavyPrimary,
    tertiary = AmberAlert,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = NavyPrimary,
    surface = LightSurface,
    onSurface = NavyPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMutedText,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = CoralDangerLight,
    onError = Color.White
)

private val SpendSenseShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SpendSenseTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) SpendSenseDarkColorScheme else SpendSenseLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SpendSenseTypography,
        shapes = SpendSenseShapes,
        content = content
    )
}
