package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.PlayerUiState
import com.example.ui.components.PodcastArtwork
import com.example.ui.components.SwipeableEpisodeCard
import com.example.viewmodel.ActivitySegment

/**
 * Classic Google Podcasts Activity Screen upgraded to Material 3 (`SingleChoiceSegmentedButtonRow`)
 * switching between Queue, Downloads, and History, connected to `PodcastViewModel` StateFlows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GooglePodcastsActivityScreen(
    queueEpisodes: List<HomeEpisodeUiModel>,
    downloadedEpisodes: List<HomeEpisodeUiModel>,
    historyEpisodes: List<HomeEpisodeUiModel>,
    playerUiState: PlayerUiState,
    onPlayEpisode: (HomeEpisodeUiModel) -> Unit,
    onToggleQueue: (HomeEpisodeUiModel) -> Unit,
    onTogglePlayed: (HomeEpisodeUiModel) -> Unit = {},
    onRemoveFromQueue: (HomeEpisodeUiModel) -> Unit,
    onMoveQueueItemUp: (Int) -> Unit,
    onMoveQueueItemDown: (Int) -> Unit,
    onDownloadEpisode: (HomeEpisodeUiModel) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSegment by rememberSaveable { mutableStateOf(ActivitySegment.QUEUE) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("activity_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = {
                    Text(
                        text = stringResource(R.string.nav_activity),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (selectedSegment == ActivitySegment.HISTORY && historyEpisodes.isNotEmpty()) {
                        TextButton(
                            onClick = onClearHistory,
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Text("Clear History")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // Segmented Button Bar (SingleChoiceSegmentedButtonRow) switching between Queue, Downloads, and History
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("activity_segmented_button_row")
            ) {
                val segments = listOf(
                    Triple(
                        ActivitySegment.QUEUE,
                        stringResource(R.string.activity_tab_queue),
                        Icons.AutoMirrored.Rounded.QueueMusic
                    ),
                    Triple(
                        ActivitySegment.DOWNLOADS,
                        stringResource(R.string.activity_tab_downloads),
                        Icons.Rounded.DownloadDone
                    ),
                    Triple(
                        ActivitySegment.HISTORY,
                        stringResource(R.string.activity_tab_history),
                        Icons.Rounded.History
                    )
                )

                segments.forEachIndexed { index, (segment, label, icon) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = segments.size),
                        onClick = { selectedSegment = segment },
                        selected = selectedSegment == segment,
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("segment_${segment.name.lowercase()}")
                    ) {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            when (selectedSegment) {
                ActivitySegment.QUEUE -> {
                    if (queueEpisodes.isEmpty()) {
                        EmptyActivityState(
                            title = stringResource(R.string.empty_queue_title),
                            subtitle = stringResource(R.string.empty_queue_subtitle)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("queue_lazy_column"),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            itemsIndexed(
                                items = queueEpisodes,
                                key = { _, ep -> ep.id }
                            ) { index, episode ->
                                QueueEpisodeItemRow(
                                    episode = episode,
                                    position = index + 1,
                                    totalCount = queueEpisodes.size,
                                    isCurrentEpisode = playerUiState.currentEpisode?.id == episode.id,
                                    onPlayClick = { onPlayEpisode(episode) },
                                    onMoveUp = { onMoveQueueItemUp(index) },
                                    onMoveDown = { onMoveQueueItemDown(index) },
                                    onRemove = { onRemoveFromQueue(episode) }
                                )
                            }
                        }
                    }
                }

                ActivitySegment.DOWNLOADS -> {
                    if (downloadedEpisodes.isEmpty()) {
                        EmptyActivityState(
                            title = stringResource(R.string.empty_downloads_title),
                            subtitle = stringResource(R.string.empty_downloads_subtitle)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("downloads_lazy_column"),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(
                                items = downloadedEpisodes,
                                key = { it.id }
                            ) { episode ->
                                SwipeableEpisodeCard(
                                    episodeTitle = episode.title,
                                    podcastName = episode.podcastName,
                                    publishedDate = episode.publishedDate,
                                    description = episode.description,
                                    durationText = episode.durationText,
                                    durationMs = episode.durationMs,
                                    resumePositionMs = episode.resumePositionMs,
                                    artworkUrl = episode.artworkUrl,
                                    isQueued = episode.isQueued,
                                    isPlayed = episode.isPlayed,
                                    onPlayClick = { onPlayEpisode(episode) },
                                    onQueueToggle = { onToggleQueue(episode) },
                                    onTogglePlayed = { onTogglePlayed(episode) },
                                    onDownloadClick = { onDownloadEpisode(episode) },
                                    onCardClick = { onPlayEpisode(episode) }
                                )
                            }
                        }
                    }
                }

                ActivitySegment.HISTORY -> {
                    if (historyEpisodes.isEmpty()) {
                        EmptyActivityState(
                            title = stringResource(R.string.empty_history_title),
                            subtitle = stringResource(R.string.empty_history_subtitle)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("history_lazy_column"),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(
                                items = historyEpisodes,
                                key = { it.id }
                            ) { episode ->
                                SwipeableEpisodeCard(
                                    episodeTitle = episode.title,
                                    podcastName = episode.podcastName,
                                    publishedDate = episode.publishedDate,
                                    description = episode.description,
                                    durationText = episode.durationText,
                                    durationMs = episode.durationMs,
                                    resumePositionMs = episode.resumePositionMs,
                                    artworkUrl = episode.artworkUrl,
                                    isQueued = episode.isQueued,
                                    isPlayed = episode.isPlayed,
                                    onPlayClick = { onPlayEpisode(episode) },
                                    onQueueToggle = { onToggleQueue(episode) },
                                    onTogglePlayed = { onTogglePlayed(episode) },
                                    onDownloadClick = { onDownloadEpisode(episode) },
                                    onCardClick = { onPlayEpisode(episode) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueEpisodeItemRow(
    episode: HomeEpisodeUiModel,
    position: Int,
    totalCount: Int,
    isCurrentEpisode: Boolean,
    onPlayClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("queue_item_${episode.id}"),
        color = MaterialTheme.colorScheme.background,
        onClick = onPlayClick
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$position",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isCurrentEpisode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.width(24.dp)
                )

                PodcastArtwork(
                    artworkUrl = episode.artworkUrl,
                    title = episode.podcastName,
                    cornerRadius = 10.dp,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isCurrentEpisode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${episode.podcastName} • ${episode.durationText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onMoveUp,
                    enabled = position > 1,
                    modifier = Modifier.testTag("queue_move_up_${episode.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = stringResource(R.string.cd_move_up),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = position < totalCount,
                    modifier = Modifier.testTag("queue_move_down_${episode.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDownward,
                        contentDescription = stringResource(R.string.cd_move_down),
                        modifier = Modifier.size(18.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("queue_play_${episode.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag("queue_remove_${episode.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.action_remove_queue),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun EmptyActivityState(
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
