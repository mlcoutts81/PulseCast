package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.rememberArtworkColorPalette
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun MiniPlayerBar(
    playerUiState: PlayerUiState,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipBackClick: () -> Unit,
    onSkipForwardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val episode = playerUiState.currentEpisode ?: return
    val artworkPalette = rememberArtworkColorPalette(episode.artworkUrl)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onExpandClick)
            .testTag("mini_player_bar"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thin 4.dp animated wave progress bar at the top edge
            MiniWaveProgressIndicator(
                progressFraction = playerUiState.progressFraction,
                isPlaying = playerUiState.isPlaying,
                activeColor = artworkPalette.accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PodcastArtwork(
                    artworkUrl = episode.artworkUrl,
                    title = episode.podcastName,
                    cornerRadius = 10.dp,
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = episode.podcastName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Animated Audio Equalizer Bars next to transport controls
                MiniWaveEqualizer(
                    isPlaying = playerUiState.isPlaying,
                    activeColor = artworkPalette.accentColor,
                    thumbColor = artworkPalette.accentColor,
                    barColor = artworkPalette.accentColor,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                IconButton(
                    onClick = onSkipBackClick,
                    modifier = Modifier.testTag("mini_player_skip_back")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Replay,
                        contentDescription = stringResource(R.string.cd_skip_back_15),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                MorphingPlayPauseButton(
                    isPlaying = playerUiState.isPlaying,
                    onClick = onPlayPauseClick,
                    buttonSize = 40.dp,
                    iconSize = 22.dp,
                    containerColor = artworkPalette.containerColor,
                    contentColor = artworkPalette.onContainerColor,
                    modifier = Modifier.testTag("mini_player_play_pause")
                )

                IconButton(
                    onClick = onSkipForwardClick,
                    modifier = Modifier.testTag("mini_player_skip_forward")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Forward30,
                        contentDescription = stringResource(R.string.cd_skip_forward_30),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.cd_expand_player),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniWaveProgressIndicator(
    progressFraction: Float,
    isPlaying: Boolean,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val clampedProgress = progressFraction.coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "MiniWavePhaseTransition")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MiniWavePhase"
    )

    val amplitudeMultiplier by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "MiniWaveAmplitude"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val centerY = size.height / 2f
        val thumbX = width * clampedProgress
        val strokePx = 2.5.dp.toPx()
        val maxAmpPx = 1.2.dp.toPx() * amplitudeMultiplier
        val waveLengthPx = 20.dp.toPx().coerceAtLeast(1f)

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

        if (thumbX < width) {
            drawLine(
                color = inactiveColor,
                start = Offset(thumbX, centerY),
                end = Offset(width, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }
    }
}
