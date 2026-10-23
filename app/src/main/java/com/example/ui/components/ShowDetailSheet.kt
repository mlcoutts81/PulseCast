package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.screens.HomeEpisodeUiModel
import com.example.ui.screens.PodcastSubscriptionUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowDetailModalSheet(
    subscription: PodcastSubscriptionUiModel,
    showEpisodes: List<HomeEpisodeUiModel>,
    onDismiss: () -> Unit,
    onUnsubscribe: (PodcastSubscriptionUiModel) -> Unit,
    onRefreshFeed: (String) -> Unit,
    onPlayEpisode: (HomeEpisodeUiModel) -> Unit,
    onToggleQueue: (HomeEpisodeUiModel) -> Unit,
    onTogglePlayed: (HomeEpisodeUiModel) -> Unit = {},
    onDownloadEpisode: (HomeEpisodeUiModel) -> Unit,
    onEpisodeDetailsClick: (HomeEpisodeUiModel) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
        ) {
            // 1. Top Show Header (Squircle Artwork + Title + Episode Count + Close)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = subscription.artworkUrl,
                    contentDescription = subscription.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subscription.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${showEpisodes.size} episodes available",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close show page"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Google Podcasts Show Action Bar (Subscribed / Unsubscribe + Refresh Feed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        onUnsubscribe(subscription)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Subscribed • Tap to remove")
                }

                OutlinedButton(
                    onClick = { onRefreshFeed(subscription.feedUrl) },
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh RSS Feed",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // 3. Filtered Episode List for This Show
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
            ) {
                item {
                    Text(
                        text = "Episodes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }

                items(showEpisodes, key = { it.id }) { episode ->
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
                        onCardClick = { onEpisodeDetailsClick(episode) }
                    )
                }
            }
        }
    }
}
