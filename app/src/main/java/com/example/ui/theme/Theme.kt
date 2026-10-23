package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val AmoledDarkColorScheme = darkColorScheme(
    primary = CoralPrimaryDark,
    onPrimary = CoralOnPrimaryDark,
    primaryContainer = CoralPrimaryContainerDark,
    onPrimaryContainer = CoralOnPrimaryContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = AmberOnSecondaryDark,
    secondaryContainer = AmberSecondaryContainerDark,
    onSecondaryContainer = AmberOnSecondaryContainerDark,
    tertiary = VioletTertiaryDark,
    onTertiary = VioletOnTertiaryDark,
    tertiaryContainer = VioletTertiaryContainerDark,
    onTertiaryContainer = VioletOnTertiaryContainerDark,
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceDim = AmoledBlack,
    surfaceContainerLowest = AmoledSurfaceLowest,
    surfaceContainerLow = AmoledSurfaceLow,
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerHigh = AmoledSurfaceContainerHigh,
    surfaceContainerHighest = AmoledSurfaceContainerHighest,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = CoralPrimaryLight,
    onPrimary = CoralOnPrimaryLight,
    primaryContainer = CoralPrimaryContainerLight,
    onPrimaryContainer = CoralOnPrimaryContainerLight,
    secondary = AmberSecondaryLight,
    onSecondary = AmberOnSecondaryLight,
    secondaryContainer = AmberSecondaryContainerLight,
    onSecondaryContainer = AmberOnSecondaryContainerLight,
    tertiary = VioletTertiaryLight,
    onTertiary = VioletOnTertiaryLight,
    tertiaryContainer = VioletTertiaryContainerLight,
    onTertiaryContainer = VioletOnTertiaryContainerLight,
    background = SurfaceLight,
    surface = SurfaceLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight
)

private fun ColorScheme.withPureBlackAmoled(): ColorScheme = copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceDim = AmoledBlack,
    surfaceContainerLowest = AmoledSurfaceLowest,
    surfaceContainerLow = AmoledSurfaceLow,
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerHigh = AmoledSurfaceContainerHigh,
    surfaceContainerHighest = AmoledSurfaceContainerHighest
)

@Composable
fun PulseCastTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    pureBlackAmoled: Boolean = true,
    content: @Composable () -> Unit
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AmoledDarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = if (darkTheme && pureBlackAmoled) {
        baseScheme.withPureBlackAmoled()
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    PulseCastTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        pureBlackAmoled = true,
        content = content
    )
}
