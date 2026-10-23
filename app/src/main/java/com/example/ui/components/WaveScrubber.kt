package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.PI
import kotlin.math.sin

// --- 1. Android "Squiggly" Wave Scrubber Bar ---
@Composable
fun AndroidWaveScrubber(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    waveAmplitude: Dp = 4.dp,
    waveLength: Dp = 28.dp,
    strokeWidth: Dp = 4.dp
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isScreenVisible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    val progressFraction = if (isDragging) {
        dragFraction
    } else {
        (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    // Only run rememberInfiniteTransition when playing AND screen is visible
    val phaseShift = if (isPlaying && !isDragging && isScreenVisible) {
        val infiniteTransition = rememberInfiniteTransition(label = "WavePhaseTransition")
        val animatedPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "WavePhase"
        )
        animatedPhase
    } else {
        0f
    }

    // Smoothly flatten the wave to 0 amplitude when paused or scrubbing
    val amplitudeMultiplier by animateFloatAsState(
        targetValue = if (isPlaying && !isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "WaveAmplitude"
    )

    // Expand thumb slightly when touched
    val thumbHeightDp by animateDpAsState(
        targetValue = if (isDragging) 28.dp else 22.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ThumbHeight"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(safeDuration) {
                    detectTapGestures { offset ->
                        val tappedFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekTo((tappedFraction * safeDuration).toLong())
                    }
                }
                .pointerInput(safeDuration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeekTo((dragFraction * safeDuration).toLong())
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }
        ) {
            val width = size.width
            val centerY = size.height / 2f
            val thumbX = width * progressFraction
            val strokePx = strokeWidth.toPx()
            val maxAmpPx = waveAmplitude.toPx() * amplitudeMultiplier
            val waveLengthPx = waveLength.toPx().coerceAtLeast(1f)

            // A. Draw Played Wave Path (Left of Thumb)
            if (thumbX > 0f) {
                val wavePath = Path()
                val stepPx = 3f
                var x = 0f
                var firstPoint = true

                while (x <= thumbX) {
                    val angle = ((x / waveLengthPx) * 2f * PI.toFloat()) - phaseShift
                    val y = centerY + sin(angle) * maxAmpPx
                    if (firstPoint) {
                        wavePath.moveTo(x, y)
                        firstPoint = false
                    } else {
                        wavePath.lineTo(x, y)
                    }
                    x += stepPx
                }

                drawPath(
                    path = wavePath,
                    color = activeColor,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            // B. Draw Unplayed Flat Track (Right of Thumb)
            if (thumbX < width) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(thumbX, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }

            // C. Draw Android Media Vertical Pill Scrubber Thumb
            val thumbWidthPx = 6.dp.toPx()
            val thumbHeightPx = thumbHeightDp.toPx()
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(
                    x = (thumbX - thumbWidthPx / 2f).coerceIn(0f, width - thumbWidthPx),
                    y = centerY - thumbHeightPx / 2f
                ),
                size = Size(thumbWidthPx, thumbHeightPx),
                cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
            )
        }

        // Timestamps Row
        val displayedPositionMs = (progressFraction * safeDuration).toLong()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatWaveDuration(displayedPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatWaveDuration(safeDuration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- 2. Morphing Material 3 Play/Pause Squircle Button ---
@Composable
fun MorphingPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 80.dp,
    iconSize: Dp = 40.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    // Morphs between a Circle (50% radius) when paused and a Squircle (28% radius) when playing
    val cornerPercent by animateIntAsState(
        targetValue = if (isPlaying) 28 else 50,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "PlayButtonCornerMorph"
    )

    FilledIconButton(
        onClick = onClick,
        shape = RoundedCornerShape(percent = cornerPercent),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = modifier.size(buttonSize)
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            modifier = Modifier.size(iconSize)
        )
    }
}

// --- 3. Mini-Player Animated Audio Equalizer Bars ---
@Composable
fun MiniWaveEqualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    thumbColor: Color = activeColor,
    barColor: Color = activeColor
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isScreenVisible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    val heights = if (isPlaying && isScreenVisible) {
        val transition = rememberInfiniteTransition(label = "MiniEq")
        val bar1 by transition.animateFloat(
            initialValue = 0.3f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "b1"
        )
        val bar2 by transition.animateFloat(
            initialValue = 0.8f, targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse), label = "b2"
        )
        val bar3 by transition.animateFloat(
            initialValue = 0.4f, targetValue = 0.95f,
            animationSpec = infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "b3"
        )
        listOf(bar1, bar2, bar3)
    } else {
        listOf(0.25f, 0.25f, 0.25f)
    }

    Canvas(modifier = modifier.size(20.dp)) {
        val barWidth = size.width / 5f
        val maxBarHeight = size.height
        heights.forEachIndexed { index, fraction ->
            val h = maxBarHeight * fraction
            val x = index * (barWidth * 1.8f)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, (maxBarHeight - h) / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

private fun formatWaveDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
