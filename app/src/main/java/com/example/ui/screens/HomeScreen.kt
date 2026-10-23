package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.components.EpisodeDetailModalSheet
import com.example.ui.components.ShowDetailModalSheet
import com.example.ui.components.SwipeableEpisodeCard
import com.example.ui.theme.AppThemeConfig
import com.example.ui.theme.ThemeSettingsDialog

// --- Home Screen UI Models ---
data class PodcastSubscriptionUiModel(
    val id: String,
    val title: String,
    val artworkUrl: String,
    val feedUrl: String,
    val hasNewEpisodes: Boolean = false
)

data class HomeEpisodeUiModel(
    val id: String,
    val title: String,
    val podcastName: String,
    val publishedDate: String,
    val description: String,
    val durationText: String,
    val durationMs: Long,
    val audioUrl: String,
    val artworkUrl: String,
    val isQueued: Boolean = false,
    val isPlayed: Boolean = false,
    val resumePositionMs: Long = 0L
) {
    val durationFormatted: String
        get() = durationText
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GooglePodcastsHomeScreen(
    subscriptions: List<PodcastSubscriptionUiModel>,
    feedEpisodes: List<HomeEpisodeUiModel>,
    hidePlayedEpisodes: Boolean = false,
    onToggleHidePlayed: () -> Unit = {},
    themeConfig: AppThemeConfig = AppThemeConfig(),
    onThemeConfigChange: (AppThemeConfig) -> Unit = {},
    onSubscriptionClick: (PodcastSubscriptionUiModel) -> Unit = {},
    onUnsubscribePodcast: (PodcastSubscriptionUiModel) -> Unit = {},
    onPlayEpisode: (HomeEpisodeUiModel) -> Unit,
    onToggleQueue: (HomeEpisodeUiModel) -> Unit,
    onTogglePlayed: (HomeEpisodeUiModel) -> Unit = {},
    onDownloadEpisode: (HomeEpisodeUiModel) -> Unit,
    onEpisodeDetailsClick: (HomeEpisodeUiModel) -> Unit = {},
    onAddRssUrl: (String) -> Unit,
    onImportOpmlUri: (Uri) -> Unit,
    onExportOpmlUri: (Uri) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showAddDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var selectedSubscription by remember { mutableStateOf<PodcastSubscriptionUiModel?>(null) }
    var selectedEpisodeForDetails by remember { mutableStateOf<HomeEpisodeUiModel?>(null) }

    val playedCount = remember(feedEpisodes) { feedEpisodes.count { it.isPlayed } }
    val visibleEpisodes = remember(feedEpisodes, hidePlayedEpisodes) {
        if (hidePlayedEpisodes) feedEpisodes.filterNot { it.isPlayed } else feedEpisodes
    }

    // Keep the detail sheet's episode state (e.g. isQueued) synced with feedEpisodes
    val activeDetailEpisode = remember(selectedEpisodeForDetails, feedEpisodes) {
        selectedEpisodeForDetails?.let { selected ->
            feedEpisodes.find { it.id == selected.id } ?: selected
        }
    }

    // System File Picker for OPML Import
    val opmlPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImportOpmlUri(it) }
    }

    // System File Creator for OPML Export
    val opmlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-opml")
    ) { uri: Uri? ->
        uri?.let { onExportOpmlUri(it) }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.RssFeed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Podcasts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showThemeDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = "Appearance & Theme"
                        )
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add RSS or OPML"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (maxWidth >= 700.dp) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    // 1. Top Subscriptions Squircle Carousel
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SubscriptionsCarouselSection(
                            subscriptions = subscriptions,
                            onSubscriptionClick = { sub ->
                                selectedSubscription = sub
                                onSubscriptionClick(sub)
                            },
                            onAddClick = { showAddDialog = true }
                        )
                    }

                    // 2. "For You" Section Header + Hide Played FilterChip
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 20.dp,
                                    end = 16.dp,
                                    top = 12.dp,
                                    bottom = 4.dp
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "For you",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (playedCount > 0 || hidePlayedEpisodes) {
                                FilterChip(
                                    selected = hidePlayedEpisodes,
                                    onClick = onToggleHidePlayed,
                                    label = {
                                        Text(
                                            text = if (hidePlayedEpisodes && playedCount > 0) {
                                                "Hide played ($playedCount)"
                                            } else {
                                                "Hide played"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (hidePlayedEpisodes) {
                                                Icons.Default.Check
                                            } else {
                                                Icons.Outlined.VisibilityOff
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(50)
                                )
                            }
                        }
                    }

                    // 3. 2-Column Chronological Episode Feed
                    if (visibleEpisodes.isEmpty() && feedEpisodes.isNotEmpty() && hidePlayedEpisodes) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 24.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "You're all caught up!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "All $playedCount episodes in your feed have been marked as played.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    FilledTonalButton(onClick = onToggleHidePlayed) {
                                        Text("Show played episodes")
                                    }
                                }
                            }
                        }
                    } else if (visibleEpisodes.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyFeedPlaceholder(onAddClick = { showAddDialog = true })
                        }
                    } else {
                        items(visibleEpisodes, key = { it.id }) { episode ->
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
                                onCardClick = {
                                    selectedEpisodeForDetails = episode
                                    onEpisodeDetailsClick(episode)
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp) // Space for MiniPlayer
                ) {
                    // 1. Top Subscriptions Squircle Carousel
                    item {
                        SubscriptionsCarouselSection(
                            subscriptions = subscriptions,
                            onSubscriptionClick = { sub ->
                                selectedSubscription = sub
                                onSubscriptionClick(sub)
                            },
                            onAddClick = { showAddDialog = true }
                        )
                    }

                    // 2. "For You" Section Header + Hide Played FilterChip
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 20.dp,
                                    end = 16.dp,
                                    top = 12.dp,
                                    bottom = 4.dp
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "For you",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (playedCount > 0 || hidePlayedEpisodes) {
                                FilterChip(
                                    selected = hidePlayedEpisodes,
                                    onClick = onToggleHidePlayed,
                                    label = {
                                        Text(
                                            text = if (hidePlayedEpisodes && playedCount > 0) {
                                                "Hide played ($playedCount)"
                                            } else {
                                                "Hide played"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (hidePlayedEpisodes) {
                                                Icons.Default.Check
                                            } else {
                                                Icons.Outlined.VisibilityOff
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(50)
                                )
                            }
                        }
                    }

                    // 3. Chronological Episode Feed
                    if (visibleEpisodes.isEmpty() && feedEpisodes.isNotEmpty() && hidePlayedEpisodes) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 24.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "You're all caught up!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "All $playedCount episodes in your feed have been marked as played.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    FilledTonalButton(onClick = onToggleHidePlayed) {
                                        Text("Show played episodes")
                                    }
                                }
                            }
                        }
                    } else if (visibleEpisodes.isEmpty()) {
                        item {
                            EmptyFeedPlaceholder(onAddClick = { showAddDialog = true })
                        }
                    } else {
                        items(visibleEpisodes, key = { it.id }) { episode ->
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
                                onCardClick = {
                                    selectedEpisodeForDetails = episode
                                    onEpisodeDetailsClick(episode)
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog for Appearance & Theme Settings
    if (showThemeDialog) {
        ThemeSettingsDialog(
            currentConfig = themeConfig,
            onConfigChange = onThemeConfigChange,
            onDismiss = { showThemeDialog = false }
        )
    }

    // Modal Dialog for Custom RSS URL or OPML File Import/Export
    if (showAddDialog) {
        AddPodcastOrOpmlDialog(
            hasSubscriptions = subscriptions.isNotEmpty(),
            onDismiss = { showAddDialog = false },
            onSubmitRss = { url ->
                onAddRssUrl(url)
                showAddDialog = false
            },
            onLaunchOpmlPicker = {
                showAddDialog = false
                // Accepts XML, OPML, or fallback octets
                opmlPickerLauncher.launch("*/*")
            },
            onLaunchOpmlExport = {
                showAddDialog = false
                opmlExportLauncher.launch("material_podcasts_backup.opml")
            }
        )
    }

    // Show Detail Modal Sheet when tapping any squircle in the top carousel
    selectedSubscription?.let { sub ->
        val showEpisodes = remember(sub, feedEpisodes) {
            feedEpisodes.filter { it.podcastName.equals(sub.title, ignoreCase = true) }
        }
        ShowDetailModalSheet(
            subscription = sub,
            showEpisodes = showEpisodes,
            onDismiss = { selectedSubscription = null },
            onUnsubscribe = { targetSub ->
                onUnsubscribePodcast(targetSub)
                selectedSubscription = null
            },
            onRefreshFeed = { feedUrl -> onAddRssUrl(feedUrl) },
            onPlayEpisode = { ep -> onPlayEpisode(ep) },
            onToggleQueue = { ep -> onToggleQueue(ep) },
            onTogglePlayed = { ep -> onTogglePlayed(ep) },
            onDownloadEpisode = { ep -> onDownloadEpisode(ep) },
            onEpisodeDetailsClick = { ep ->
                selectedEpisodeForDetails = ep
                onEpisodeDetailsClick(ep)
            }
        )
    }

    // Episode Details Modal Bottom Sheet
    activeDetailEpisode?.let { detailEpisode ->
        EpisodeDetailModalSheet(
            episode = detailEpisode,
            onDismiss = { selectedEpisodeForDetails = null },
            onPlayClick = { ep -> onPlayEpisode(ep) },
            onToggleQueue = { ep -> onToggleQueue(ep) },
            onDownloadClick = { ep -> onDownloadEpisode(ep) }
        )
    }
}

// --- Top Horizontal Squircle Row ---
@Composable
private fun SubscriptionsCarouselSection(
    subscriptions: List<PodcastSubscriptionUiModel>,
    onSubscriptionClick: (PodcastSubscriptionUiModel) -> Unit,
    onAddClick: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        items(subscriptions, key = { it.id }) { sub ->
            SubscriptionSquircleItem(
                subscription = sub,
                onClick = { onSubscriptionClick(sub) }
            )
        }

        // "Add RSS / OPML" Squircle Button at the end of the row
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(80.dp)
                    .clickable(onClick = onAddClick)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Podcast or OPML",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Add RSS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SubscriptionSquircleItem(
    subscription: PodcastSubscriptionUiModel,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.size(80.dp)) {
            AsyncImage(
                model = subscription.artworkUrl,
                contentDescription = subscription.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )

            // Unread New Episode Badge Dot
            if (subscription.hasNewEpisodes) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subscription.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// --- Custom RSS & OPML Import / Export Dialog ---
@Composable
private fun AddPodcastOrOpmlDialog(
    hasSubscriptions: Boolean,
    onDismiss: () -> Unit,
    onSubmitRss: (String) -> Unit,
    onLaunchOpmlPicker: () -> Unit,
    onLaunchOpmlExport: () -> Unit
) {
    var rssUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add or Backup Podcasts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Subscribe via an RSS feed link, or import/export an OPML backup file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = rssUrl,
                    onValueChange = { rssUrl = it },
                    label = { Text("RSS Feed URL") },
                    placeholder = { Text("https://example.com/feed.xml") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Import OPML Button
                FilledTonalButton(
                    onClick = onLaunchOpmlPicker,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import OPML File (.opml / .xml)")
                }

                // One-Tap Export OPML Button
                OutlinedButton(
                    onClick = onLaunchOpmlExport,
                    enabled = hasSubscriptions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export OPML Backup (.opml)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitRss(rssUrl.trim()) },
                enabled = rssUrl.isNotBlank()
            ) {
                Text("Subscribe")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// --- Empty Feed State ---
@Composable
private fun EmptyFeedPlaceholder(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No episodes yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Search for podcasts in Explore, paste an RSS link, or import an OPML file to populate your feed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        FilledTonalButton(onClick = onAddClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add RSS or OPML")
        }
    }
}
