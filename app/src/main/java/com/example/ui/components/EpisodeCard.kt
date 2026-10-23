package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.models.EpisodeEntity
import com.example.data.models.formatDurationMinutesLabel
import com.example.data.models.formatRelativeDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableEpisodeCard(
    episodeTitle: String,
    podcastName: String,
    publishedDate: String,
    description: String,
    durationText: String,
    artworkUrl: String,
    isQueued: Boolean,
    isPlayed: Boolean = false,
    resumePositionMs: Long = 0L,
    durationMs: Long = 0L,
    onPlayClick: () -> Unit,
    onQueueToggle: () -> Unit,
    onTogglePlayed: () -> Unit,
    onDownloadClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    // Keep latest lambdas updated so confirmValueChange always invokes current state
    val currentQueueToggle by rememberUpdatedState(onQueueToggle)
    val currentPlayedToggle by rememberUpdatedState(onTogglePlayed)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { targetValue ->
            when (targetValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swipe Right -> Toggle Up Next Queue
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentQueueToggle()
                    false // Return false so the card springs smoothly back into place
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swipe Left -> Toggle Marked as Played
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentPlayedToggle()
                    false // Spring back into place with updated "Played" styling
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.32f }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection

            val bgColor by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.secondaryContainer
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                label = "SwipeBgColor"
            )

            val iconScale by animateFloatAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.85f else 1.15f,
                label = "SwipeIconScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                }
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    // Right Swipe Content: Add / Remove from Up Next
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isQueued) Icons.Default.PlaylistAddCheck else Icons.Default.PlaylistAdd,
                            contentDescription = "Add to Up Next",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.scale(iconScale)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isQueued) "In Up Next" else "Add to Up Next",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                    // Left Swipe Content: Mark as Played / Unplayed
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isPlayed) "Mark Unplayed" else "Mark Played",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = if (isPlayed) Icons.Outlined.CheckCircleOutline else Icons.Default.CheckCircle,
                            contentDescription = "Mark as Played",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.scale(iconScale)
                        )
                    }
                }
            }
        }
    ) {
        // Dim the card slightly when marked as played so unplayed episodes stand out
        Box(modifier = Modifier.alpha(if (isPlayed) 0.62f else 1f)) {
            GooglePodcastsEpisodeCard(
                episodeTitle = episodeTitle,
                podcastName = podcastName,
                publishedDate = publishedDate,
                description = description,
                durationText = if (isPlayed) "✓ Played • $durationText" else durationText,
                artworkUrl = artworkUrl,
                isQueued = isQueued,
                resumePositionMs = if (isPlayed) 0L else resumePositionMs,
                durationMs = durationMs,
                onPlayClick = onPlayClick,
                onQueueToggle = onQueueToggle,
                onDownloadClick = onDownloadClick,
                onCardClick = onCardClick,
                modifier = Modifier
            )
        }
    }
}

@Composable
fun GooglePodcastsEpisodeCard(
    episodeTitle: String,
    podcastName: String,
    publishedDate: String,
    description: String,
    durationText: String,
    artworkUrl: String,
    isQueued: Boolean = false,
    durationMs: Long = 0L,
    resumePositionMs: Long = 0L,
    onPlayClick: () -> Unit,
    onQueueToggle: () -> Unit,
    onDownloadClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
) {
    val isPartiallyPlayed = resumePositionMs > 0L && durationMs > 0L && resumePositionMs < durationMs
    val progressFraction = if (isPartiallyPlayed) {
        (resumePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val playPillLabel = if (isPartiallyPlayed) {
        val remainingMinutes = ((durationMs - resumePositionMs) / 60_000L).coerceAtLeast(1L)
        "$remainingMinutes min left"
    } else {
        durationText
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Mini Show Icon + Podcast Title + Date
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = podcastName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = " • $publishedDate",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Episode Title
            Text(
                text = episodeTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Episode Snippet
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Signature Google Podcasts Action Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pill-shaped Play + Duration / 'X min left' Button with subtle progress indicator
                Surface(
                    onClick = onPlayClick,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (isPartiallyPlayed) {
                            // Subtle progress fill + bottom bar inside the Play pill
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(50))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progressFraction)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                )
                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .align(Alignment.BottomCenter),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Episode",
                                tint = if (isPartiallyPlayed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = playPillLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isPartiallyPlayed) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }

                // One-tap Add to Queue
                IconButton(onClick = onQueueToggle) {
                    Icon(
                        imageVector = if (isQueued) Icons.Default.Check else Icons.Outlined.PlaylistAdd,
                        contentDescription = "Add to Queue",
                        tint = if (isQueued) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Download Icon
                IconButton(onClick = onDownloadClick) {
                    Icon(
                        imageVector = Icons.Outlined.DownloadForOffline,
                        contentDescription = "Download Episode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(
    episode: EpisodeEntity,
    isCurrentlyPlaying: Boolean,
    isCurrentEpisode: Boolean,
    onPlayClick: () -> Unit,
    onQueueToggleClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false
) {
    val relativeDate = formatRelativeDate(episode.publishedTimestamp, episode.pubDate)
    val durationLabel = if (isCurrentEpisode && isCurrentlyPlaying) {
        "Playing • ${formatDurationMinutesLabel(episode.durationSeconds)}"
    } else {
        formatDurationMinutesLabel(episode.durationSeconds)
    }

    Box(modifier = modifier.testTag("episode_card_${episode.id}")) {
        GooglePodcastsEpisodeCard(
            episodeTitle = episode.title,
            podcastName = episode.podcastTitle,
            publishedDate = relativeDate,
            description = episode.description,
            durationText = durationLabel,
            durationMs = episode.durationSeconds * 1000L,
            resumePositionMs = episode.lastPlayedPositionMs,
            artworkUrl = episode.artworkUrl,
            isQueued = episode.isQueued,
            onPlayClick = onPlayClick,
            onQueueToggle = onQueueToggleClick,
            onDownloadClick = onDownloadClick,
            onCardClick = onDetailsClick
        )
    }
}
