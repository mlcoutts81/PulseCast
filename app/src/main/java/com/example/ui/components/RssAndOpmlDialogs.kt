package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.models.DownloadStatus
import com.example.data.models.EpisodeEntity
import com.example.data.models.formatDurationMinutesLabel
import com.example.data.models.formatRelativeDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RssAndOpmlDialog(
    onDismiss: () -> Unit,
    onSubscribeRss: (String) -> Unit,
    onPickOpmlFile: () -> Unit,
    onImportSampleOpml: () -> Unit
) {
    var rssUrl by rememberSaveable { mutableStateOf("") }

    val quickFeeds = listOf(
        "99% Invisible" to "https://feeds.99percentinvisible.org/99percentinvisible",
        "Planet Money" to "https://feeds.npr.org/510289/podcast.xml",
        "Hard Fork" to "https://feeds.simplecast.com/l2i9YnTd"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.RssFeed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.dialog_rss_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Paste a direct podcast RSS/Atom feed URL or import an OPML XML file to add subscriptions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = rssUrl,
                    onValueChange = { rssUrl = it },
                    label = { Text(stringResource(R.string.dialog_rss_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rss_url_input")
                )

                Text(
                    text = "Quick Fill Verified Feeds:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickFeeds.forEach { (name, url) ->
                        AssistChip(
                            onClick = { rssUrl = url },
                            label = { Text(name) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "OPML Subscription Import (.opml / .xml)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPickOpmlFile,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_opml_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose .OPML")
                    }

                    OutlinedButton(
                        onClick = onImportSampleOpml,
                        modifier = Modifier.testTag("sample_opml_button")
                    ) {
                        Text("Starter Pack")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubscribeRss(rssUrl) },
                enabled = rssUrl.isNotBlank(),
                modifier = Modifier.testTag("rss_subscribe_confirm_button")
            ) {
                Text(stringResource(R.string.dialog_rss_add))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("rss_dialog_cancel_button")
            ) {
                Text(stringResource(R.string.dialog_rss_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailsBottomSheet(
    episode: EpisodeEntity,
    onDismiss: () -> Unit,
    onPlayClick: () -> Unit,
    onQueueToggleClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val relativeDate = formatRelativeDate(episode.publishedTimestamp, episode.pubDate)
    val durationLabel = formatDurationMinutesLabel(episode.durationSeconds)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag("episode_details_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                PodcastArtwork(
                    artworkUrl = episode.artworkUrl,
                    title = episode.podcastTitle,
                    cornerRadius = 14.dp,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.podcastTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${episode.podcastAuthor} • $relativeDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = episode.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        onPlayClick()
                        onDismiss()
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("sheet_play_episode_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(durationLabel)
                }

                FilledTonalButton(
                    onClick = onQueueToggleClick,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (episode.isQueued) {
                            Icons.AutoMirrored.Rounded.PlaylistAddCheck
                        } else {
                            Icons.AutoMirrored.Rounded.PlaylistAdd
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (episode.isQueued) "In Queue" else "Queue")
                }

                OutlinedButton(
                    onClick = onDownloadClick,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (episode.downloadStatus == DownloadStatus.DOWNLOADED) {
                            Icons.Rounded.DownloadDone
                        } else {
                            Icons.Outlined.DownloadForOffline
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        when (episode.downloadStatus) {
                            DownloadStatus.DOWNLOADED -> "Saved"
                            DownloadStatus.DOWNLOADING -> "${episode.downloadProgress}%"
                            else -> "Download"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Show Notes & Episode Details",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = episode.description.ifBlank { "No episode notes provided by the publisher." },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
