package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.rememberArtworkColorPalette

enum class MainNavTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    EXPLORE("Explore", Icons.Outlined.Explore),
    ACTIVITY("Activity", Icons.Outlined.QueueMusic)
}

@Composable
fun AdaptivePodcastAppShell(
    selectedTab: MainNavTab,
    onSelectTab: (MainNavTab) -> Unit,
    playerState: PlayerUiState,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBack: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onSelectSpeed: (Float) -> Unit = {},
    onToggleSkipSilence: (Boolean) -> Unit = {},
    onSelectVoiceBoost: (VoiceBoostLevel) -> Unit = {},
    onSelectEpisodeFromQueue: (EpisodeUiModel) -> Unit,
    onRemoveFromQueue: (EpisodeUiModel) -> Unit,
    screenContent: @Composable (PaddingValues) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletLandscape = maxWidth >= 840.dp

        if (isTabletLandscape) {
            // --- PIXEL TABLET SPLIT-SCREEN DOCK LAYOUT ---
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // 1. Left Ergonomic Navigation Rail
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    header = {
                        Icon(
                            imageVector = Icons.Outlined.RssFeed,
                            contentDescription = "Podcasts",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(vertical = 16.dp)
                                .size(32.dp)
                        )
                    }
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    MainNavTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = selectedTab == tab,
                            onClick = { onSelectTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                // 2. Center Main Content Pane (Home Grid / Explore / Activity)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    screenContent(PaddingValues(0.dp))
                }

                // 3. Right Persistent Docked Player & Queue Side-Panel (380.dp)
                TabletDockedPlayerPanel(
                    playerState = playerState,
                    onPlayPause = onPlayPause,
                    onSeekTo = onSeekTo,
                    onSkipForward = onSkipForward,
                    onSkipBack = onSkipBack,
                    onCycleSpeed = onCycleSpeed,
                    onCycleSleepTimer = onCycleSleepTimer,
                    onSelectSpeed = onSelectSpeed,
                    onToggleSkipSilence = onToggleSkipSilence,
                    onSelectVoiceBoost = onSelectVoiceBoost,
                    onSelectEpisodeFromQueue = onSelectEpisodeFromQueue,
                    onRemoveFromQueue = onRemoveFromQueue
                )
            }
        } else {
            // --- STANDARD PHONE LAYOUT (Bottom Nav + Expandable Mini-Player) ---
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0), // Prevents double top padding
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.testTag("bottom_navigation_bar")
                    ) {
                        MainNavTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { onSelectTab(tab) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            ) { outerPadding ->
                // Only apply bottom padding from the outer Scaffold so the top stays flush
                Box(modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding())) {
                    PodcastPlayerScaffold(
                        playerState = playerState,
                        onPlayPause = onPlayPause,
                        onSeekTo = onSeekTo,
                        onSkipForward = onSkipForward,
                        onSkipBack = onSkipBack,
                        onCycleSpeed = onCycleSpeed,
                        onCycleSleepTimer = onCycleSleepTimer,
                        onSelectSpeed = onSelectSpeed,
                        onToggleSkipSilence = onToggleSkipSilence,
                        onSelectVoiceBoost = onSelectVoiceBoost,
                        onSelectEpisodeFromQueue = onSelectEpisodeFromQueue,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onOpenQueue = { onSelectTab(MainNavTab.ACTIVITY) },
                        mainAppContent = screenContent
                    )
                }
            }
        }
    }
}

// --- Persistent Right-Hand Player & Queue Panel for Pixel Tablet ---
@Composable
private fun TabletDockedPlayerPanel(
    playerState: PlayerUiState,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBack: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onSelectSpeed: (Float) -> Unit = {},
    onToggleSkipSilence: (Boolean) -> Unit = {},
    onSelectVoiceBoost: (VoiceBoostLevel) -> Unit = {},
    onSelectEpisodeFromQueue: (EpisodeUiModel) -> Unit,
    onRemoveFromQueue: (EpisodeUiModel) -> Unit
) {
    val episode = playerState.currentEpisode
    val artworkPalette = rememberArtworkColorPalette(episode?.artworkUrl)
    var showAudioFxSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .width(380.dp)
            .fillMaxHeight()
            .padding(12.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(artworkPalette.ambientBackgroundBrush)
        ) {
            if (episode == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select an episode to start listening",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Compact Header Artwork + Title for Hub Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = episode.artworkUrl,
                            contentDescription = episode.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = episode.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = episode.podcastName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = artworkPalette.accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Android "Squiggly" Wave Scrubber Bar
                    AndroidWaveScrubber(
                        currentPositionMs = playerState.currentPositionMs,
                        durationMs = episode.durationMs,
                        isPlaying = playerState.isPlaying,
                        onSeekTo = onSeekTo,
                        activeColor = artworkPalette.accentColor,
                        thumbColor = artworkPalette.accentColor,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Large Easy-Tap Transport Controls for Docked Viewing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = onSkipBack,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 15s")
                        }

                        MorphingPlayPauseButton(
                            isPlaying = playerState.isPlaying,
                            onClick = onPlayPause,
                            buttonSize = 68.dp,
                            iconSize = 34.dp,
                            containerColor = artworkPalette.containerColor,
                            contentColor = artworkPalette.onContainerColor
                        )

                        FilledTonalIconButton(
                            onClick = onSkipForward,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.Default.Forward30, contentDescription = "Forward 30s")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Speed, Audio FX, & Sleep Timer Chips (Never wraps or squashes)
                    val isAudioFxActive = playerState.skipSilenceEnabled ||
                        playerState.voiceBoostLevel != VoiceBoostLevel.OFF
                    val isTimerActive = playerState.sleepTimerLabel != "Sleep"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = onCycleSpeed,
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = "${playerState.playbackSpeed}x",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        FilterChip(
                            selected = isAudioFxActive,
                            onClick = { showAudioFxSheet = true },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = if (isAudioFxActive) "FX On" else "Boost",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.GraphicEq, contentDescription = "Audio FX", modifier = Modifier.size(16.dp))
                            }
                        )
                        FilterChip(
                            selected = isTimerActive,
                            onClick = onCycleSleepTimer,
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = playerState.sleepTimerLabel,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Always-Visible Up Next Queue List inside Docked Panel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Up Next (${playerState.queue.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (playerState.queue.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Queue is empty",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(playerState.queue, key = { _, ep -> ep.id }) { _, queuedEp ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectEpisodeFromQueue(queuedEp) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = queuedEp.artworkUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = queuedEp.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = queuedEp.podcastName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { onRemoveFromQueue(queuedEp) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAudioFxSheet) {
        AudioEnhancementsSheet(
            playerState = playerState,
            onSelectSpeed = onSelectSpeed,
            onToggleSkipSilence = onToggleSkipSilence,
            onSelectVoiceBoost = onSelectVoiceBoost,
            onDismiss = { showAudioFxSheet = false }
        )
    }
}

private fun formatTabletDuration(durationMs: Long): String {
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
