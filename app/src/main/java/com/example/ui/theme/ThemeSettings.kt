package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// --- 1. Theme Configuration Models ---
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class ColorSource(val label: String) {
    DYNAMIC_SYSTEM("System Wallpaper"),
    CUSTOM_SEED("Custom Color")
}

enum class CustomAccentColor(
    val label: String,
    val lightPrimary: Color,
    val lightContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color
) {
    GOOGLE_BLUE(
        label = "Podcasts Blue",
        lightPrimary = Color(0xFF1A73E8),
        lightContainer = Color(0xFFD2E3FC),
        darkPrimary = Color(0xFF8AB4F8),
        darkContainer = Color(0xFF174EA6)
    ),
    RACING_RED(
        label = "Apex Red",
        lightPrimary = Color(0xFFC5221F),
        lightContainer = Color(0xFFFCE8E6),
        darkPrimary = Color(0xFFF28B82),
        darkContainer = Color(0xFF8C1D18)
    ),
    EMERALD_GREEN(
        label = "Android Green",
        lightPrimary = Color(0xFF188038),
        lightContainer = Color(0xFFCEEAD6),
        darkPrimary = Color(0xFF81C995),
        darkContainer = Color(0xFF0D652D)
    ),
    CYBER_PURPLE(
        label = "Neon Violet",
        lightPrimary = Color(0xFF7627BB),
        lightContainer = Color(0xFFF3E8FD),
        darkPrimary = Color(0xFFD7AEFB),
        darkContainer = Color(0xFF51158C)
    ),
    SUNSET_GOLD(
        label = "Papaya Orange",
        lightPrimary = Color(0xFFE37400),
        lightContainer = Color(0xFFFFEFC3),
        darkPrimary = Color(0xFFFDD663),
        darkContainer = Color(0xFF7A3E00)
    )
}

data class AppThemeConfig(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.DYNAMIC_SYSTEM,
    val selectedAccent: CustomAccentColor = CustomAccentColor.GOOGLE_BLUE,
    val useAmoledBlack: Boolean = false
)

// --- 2. Dynamic + Custom Material 3 Theme Wrapper ---
@Composable
fun GooglePodcastsDynamicTheme(
    themeConfig: AppThemeConfig,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeConfig.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val baseScheme = when {
        // Use Android 12+ Wallpaper Dynamic Colors when selected
        themeConfig.colorSource == ColorSource.DYNAMIC_SYSTEM &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Otherwise build a custom Material 3 scheme from the user's chosen accent swatch
        darkTheme -> buildCustomDarkScheme(themeConfig.selectedAccent)
        else -> buildCustomLightScheme(themeConfig.selectedAccent)
    }

    // Apply True Black AMOLED surface overrides if Dark Mode + AMOLED toggle are active
    val finalScheme = if (darkTheme && themeConfig.useAmoledBlack) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0D0D0D),
            surfaceContainer = Color(0xFF141414),
            surfaceContainerHigh = Color(0xFF1F1F1F)
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = finalScheme,
        typography = Typography,
        content = content
    )
}

private fun buildCustomLightScheme(accent: CustomAccentColor): ColorScheme {
    return lightColorScheme(
        primary = accent.lightPrimary,
        onPrimary = Color.White,
        primaryContainer = accent.lightContainer,
        onPrimaryContainer = Color(0xFF041E49),
        secondary = accent.lightPrimary,
        secondaryContainer = accent.lightContainer,
        onSecondaryContainer = Color(0xFF041E49)
    )
}

private fun buildCustomDarkScheme(accent: CustomAccentColor): ColorScheme {
    return darkColorScheme(
        primary = accent.darkPrimary,
        onPrimary = Color(0xFF041E49),
        primaryContainer = accent.darkContainer,
        onPrimaryContainer = Color(0xFFD2E3FC),
        secondary = accent.darkPrimary,
        secondaryContainer = accent.darkContainer,
        onSecondaryContainer = Color(0xFFD2E3FC)
    )
}

// --- 3. Interactive Theme Settings Dialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsDialog(
    currentConfig: AppThemeConfig,
    onConfigChange: (AppThemeConfig) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Appearance & Theme",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Section 1: Light / Dark / System Mode
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Theme mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = currentConfig.themeMode == mode,
                                onClick = { onConfigChange(currentConfig.copy(themeMode = mode)) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size
                                )
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }

                // Section 2: System Dynamic Colors vs Custom Color
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Color source",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ColorSource.entries.forEachIndexed { index, source ->
                            SegmentedButton(
                                selected = currentConfig.colorSource == source,
                                onClick = { onConfigChange(currentConfig.copy(colorSource = source)) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ColorSource.entries.size
                                )
                            ) {
                                Text(source.label)
                            }
                        }
                    }
                }

                // Section 3: User-Defined Accent Swatches (Visible when Custom Color is active)
                if (currentConfig.colorSource == ColorSource.CUSTOM_SEED) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Accent color (${currentConfig.selectedAccent.label})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(CustomAccentColor.entries) { swatch ->
                                val isSelected = currentConfig.selectedAccent == swatch
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(swatch.lightPrimary)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onConfigChange(currentConfig.copy(selectedAccent = swatch))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = swatch.label,
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Section 4: Pure Black AMOLED Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onConfigChange(currentConfig.copy(useAmoledBlack = !currentConfig.useAmoledBlack))
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pure black dark mode",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Uses true #000000 backgrounds in dark theme",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = currentConfig.useAmoledBlack,
                        onCheckedChange = {
                            onConfigChange(currentConfig.copy(useAmoledBlack = it))
                        }
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
