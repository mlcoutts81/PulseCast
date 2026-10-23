package com.example.ui.theme

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArtworkColorPalette(
    val accentColor: Color,
    val containerColor: Color,
    val onContainerColor: Color,
    val ambientBackgroundBrush: Brush
)

@Composable
fun rememberArtworkColorPalette(
    artworkUrl: String?,
    isDarkTheme: Boolean = isSystemInDarkTheme()
): ArtworkColorPalette {
    val context = LocalContext.current
    val fallbackPrimary = MaterialTheme.colorScheme.primary
    val fallbackContainer = MaterialTheme.colorScheme.primaryContainer
    val fallbackOnContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val baseSurface = MaterialTheme.colorScheme.surfaceContainerHigh

    var rawSeedColor by remember(artworkUrl) { mutableStateOf<Color?>(null) }

    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) {
            rawSeedColor = null
            return@LaunchedEffect
        }
        rawSeedColor = extractVibrantColorFromUrl(context, artworkUrl)
    }

    // Derive accessible M3-style tokens from the extracted artwork seed color
    val targetAccent = remember(rawSeedColor, isDarkTheme, fallbackPrimary) {
        rawSeedColor?.let { adjustHsvForContrast(it, isDark = isDarkTheme, isContainer = false) }
            ?: fallbackPrimary
    }

    val targetContainer = remember(rawSeedColor, isDarkTheme, fallbackContainer) {
        rawSeedColor?.let { adjustHsvForContrast(it, isDark = isDarkTheme, isContainer = true) }
            ?: fallbackContainer
    }

    val targetOnContainer = remember(rawSeedColor, isDarkTheme, fallbackOnContainer) {
        if (rawSeedColor == null) fallbackOnContainer
        else if (isDarkTheme) Color(0xFFF5F5F7) else Color(0xFF111114)
    }

    // Smooth 600ms color transitions when switching episodes
    val animatedAccent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "ArtworkAccent"
    )
    val animatedContainer by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "ArtworkContainer"
    )
    val animatedOnContainer by animateColorAsState(
        targetValue = targetOnContainer,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "ArtworkOnContainer"
    )

    val ambientBrush = remember(animatedAccent, baseSurface) {
        Brush.verticalGradient(
            colors = listOf(
                animatedAccent.copy(alpha = 0.24f),
                animatedAccent.copy(alpha = 0.06f),
                Color.Transparent
            )
        )
    }

    return ArtworkColorPalette(
        accentColor = animatedAccent,
        containerColor = animatedContainer,
        onContainerColor = animatedOnContainer,
        ambientBackgroundBrush = ambientBrush
    )
}

// --- Off-Main-Thread Bitmap Sampler & Vibrant HSV Extractor ---
private suspend fun extractVibrantColorFromUrl(
    context: android.content.Context,
    url: String
): Color? = withContext(Dispatchers.IO) {
    runCatching {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(64, 64) // Tiny downsampled bitmap for instant <5ms color analysis
            .allowHardware(false)
            .build()

        val result = loader.execute(request)
        val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            ?: return@runCatching null

        findMostVibrantColor(bitmap)
    }.getOrNull()
}

private fun findMostVibrantColor(bitmap: Bitmap): Color? {
    val width = bitmap.width
    val height = bitmap.height
    val hsv = FloatArray(3)

    // Quantize hues into 36 buckets (10 degrees each) weighted by saturation & brightness
    val bucketWeights = FloatArray(36)
    val bucketSaturationSum = FloatArray(36)
    val bucketValueSum = FloatArray(36)
    val bucketCounts = IntArray(36)

    for (y in 0 until height step 2) {
        for (x in 0 until width step 2) {
            val pixel = bitmap.getPixel(x, y)
            if (AndroidColor.alpha(pixel) < 128) continue

            AndroidColor.colorToHSV(pixel, hsv)
            val hue = hsv[0]        // 0..360
            val saturation = hsv[1] // 0..1
            val value = hsv[2]      // 0..1

            // Ignore near-black, near-white, and washed-out grey background pixels
            if (saturation < 0.22f || value < 0.18f || value > 0.96f) continue

            val bucket = ((hue / 10f).toInt()).coerceIn(0, 35)
            val vibrancyScore = saturation * saturation * value

            bucketWeights[bucket] += vibrancyScore
            bucketSaturationSum[bucket] += saturation
            bucketValueSum[bucket] += value
            bucketCounts[bucket]++
        }
    }

    var bestBucket = -1
    var maxWeight = 0f
    for (i in 0 until 36) {
        if (bucketWeights[i] > maxWeight && bucketCounts[i] > 0) {
            maxWeight = bucketWeights[i]
            bestBucket = i
        }
    }

    if (bestBucket == -1) return null

    val winningHue = (bestBucket * 10f) + 5f
    val avgSat = (bucketSaturationSum[bestBucket] / bucketCounts[bestBucket]).coerceIn(0.45f, 0.9f)
    val avgVal = (bucketValueSum[bestBucket] / bucketCounts[bestBucket]).coerceIn(0.55f, 0.9f)

    val rgbInt = AndroidColor.HSVToColor(floatArrayOf(winningHue, avgSat, avgVal))
    return Color(rgbInt)
}

// Normalizes the extracted color so it always meets Material 3 contrast guidelines
private fun adjustHsvForContrast(seed: Color, isDark: Boolean, isContainer: Boolean): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(
        AndroidColor.argb(
            255,
            (seed.red * 255).toInt(),
            (seed.green * 255).toInt(),
            (seed.blue * 255).toInt()
        ),
        hsv
    )

    when {
        isDark && !isContainer -> {
            // Wave Scrubber & Accents in Dark/AMOLED mode: bright & pastel-vibrant
            hsv[1] = hsv[1].coerceIn(0.45f, 0.72f)
            hsv[2] = hsv[2].coerceIn(0.82f, 0.96f)
        }
        isDark && isContainer -> {
            // Morphing Play Button Container in Dark mode: rich deep tone
            hsv[1] = hsv[1].coerceIn(0.55f, 0.85f)
            hsv[2] = 0.42f
        }
        !isDark && !isContainer -> {
            // Wave Scrubber & Accents in Light mode: rich & saturated
            hsv[1] = hsv[1].coerceIn(0.65f, 0.90f)
            hsv[2] = hsv[2].coerceIn(0.45f, 0.68f)
        }
        else -> {
            // Morphing Play Button Container in Light mode: soft pastel tint
            hsv[1] = hsv[1].coerceIn(0.25f, 0.45f)
            hsv[2] = 0.94f
        }
    }

    return Color(AndroidColor.HSVToColor(hsv))
}
