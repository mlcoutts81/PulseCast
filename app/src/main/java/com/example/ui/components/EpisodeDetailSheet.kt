package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.screens.HomeEpisodeUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailModalSheet(
    episode: HomeEpisodeUiModel,
    onDismiss: () -> Unit,
    onPlayClick: (HomeEpisodeUiModel) -> Unit,
    onToggleQueue: (HomeEpisodeUiModel) -> Unit,
    onDownloadClick: (HomeEpisodeUiModel) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // 1. Top Header: Artwork + Podcast Name + Published Date + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = episode.artworkUrl,
                    contentDescription = episode.podcastName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.podcastName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${episode.publishedDate} • ${episode.durationText}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close episode details"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Full Episode Title
            Text(
                text = episode.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Pinned Google Podcasts Action Bar (Play Pill + Queue + Download + Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onPlayClick(episode)
                        onDismiss()
                    },
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Play • ${episode.durationText}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                FilledTonalIconButton(onClick = { onToggleQueue(episode) }) {
                    Icon(
                        imageVector = if (episode.isQueued) Icons.Default.Check else Icons.Outlined.PlaylistAdd,
                        contentDescription = if (episode.isQueued) "In Queue" else "Add to Queue"
                    )
                }

                FilledTonalIconButton(onClick = { onDownloadClick(episode) }) {
                    Icon(
                        imageVector = Icons.Outlined.DownloadForOffline,
                        contentDescription = "Download Episode"
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, episode.title)
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Listen to \"${episode.title}\" from ${episode.podcastName}:\n${episode.audioUrl}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Episode"))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share Episode"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // 4. Scrollable Show Notes Section
            Text(
                text = "Show notes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = episode.description.ifBlank { "No show notes provided for this episode." },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
