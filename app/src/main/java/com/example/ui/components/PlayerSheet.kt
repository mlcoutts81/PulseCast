package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.rememberArtworkColorPalette
import kotlinx.coroutines.launch

enum class VoiceBoostLevel(val label: String, val gainDb: Int) {
    OFF("Off", 0),
    STANDARD("Boost +6dB", 6),
    MAX("Max +12dB", 12);

    companion object {
        fun fromGainDb(gainDb: Int): VoiceBoostLevel =
            entries.find { it.gainDb == gainDb } ?: OFF
    }
}

data class EpisodeUiModel(
    val id: String,
    val title: String,
    val podcastName: String,
    val artworkUrl: String,
    val durationMs: Long
)

data class PlayerUiState(
    val currentEpisode: EpisodeUiModel? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerLabel: String = "Sleep", // Shows "Sleep", "14:59", or "End of Ep"
    val skipSilenceEnabled: Boolean = false,
    val voiceBoostLevel: VoiceBoostLevel = VoiceBoostLevel.OFF,
    val queue: List<EpisodeUiModel> = emptyList()
) {
    val progressFraction: Float
        get() {
            val dur = currentEpisode?.durationMs ?: 0L
            return if (dur > 0L) {
                (currentPositionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
}

@Composable
fun FullScreenPlayer(
    episode: EpisodeUiModel,
    playerState: PlayerUiState,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBack: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onOpenAudioFx: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onSelectSpeed: (Float) -> Unit = {},
    onToggleSkipSilence: (Boolean) -> Unit = {},
    onSelectVoiceBoost: (VoiceBoostLevel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val durationMs = episode.durationMs.coerceAtLeast(1L)
    val artworkPalette = rememberArtworkColorPalette(episode.artworkUrl)
    var showAudioFxSheet by remember { mutableStateOf(false) }

    val openAudioFxAction = {
        showAudioFxSheet = true
        onOpenAudioFx()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .background(artworkPalette.ambientBackgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("full_player_screen")
    ) {
        val isLandscapeOrWide = maxWidth > maxHeight && maxWidth >= 560.dp

        if (isLandscapeOrWide) {
            // --- LANDSCAPE / FOLDABLE / TABLET FULL PLAYER ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.testTag("full_player_collapse_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_collapse_player),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.player_now_playing).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = artworkPalette.accentColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    IconButton(
                        onClick = openAudioFxAction,
                        modifier = Modifier.testTag("full_player_audio_fx_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Audio Enhancements",
                            tint = if (playerState.skipSilenceEnabled || playerState.voiceBoostLevel != VoiceBoostLevel.OFF) {
                                artworkPalette.accentColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = episode.artworkUrl,
                            contentDescription = episode.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.58f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = episode.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = episode.podcastName,
                            style = MaterialTheme.typography.titleMedium,
                            color = artworkPalette.accentColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        AndroidWaveScrubber(
                            currentPositionMs = playerState.currentPositionMs,
                            durationMs = durationMs,
                            isPlaying = playerState.isPlaying,
                            onSeekTo = onSeekTo,
                            activeColor = artworkPalette.accentColor,
                            thumbColor = artworkPalette.accentColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("full_player_scrub_slider")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = onSkipBack,
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("full_player_skip_back_15")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Replay,
                                    contentDescription = stringResource(R.string.cd_skip_back_15),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            MorphingPlayPauseButton(
                                isPlaying = playerState.isPlaying,
                                onClick = onPlayPause,
                                buttonSize = 72.dp,
                                iconSize = 36.dp,
                                containerColor = artworkPalette.containerColor,
                                contentColor = artworkPalette.onContainerColor,
                                modifier = Modifier.testTag("full_player_play_pause")
                            )

                            FilledTonalIconButton(
                                onClick = onSkipForward,
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("full_player_skip_forward_30")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Forward30,
                                    contentDescription = stringResource(R.string.cd_skip_forward_30),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        AdaptivePlayerUtilityControls(
                            playerState = playerState,
                            onCycleSpeed = onCycleSpeed,
                            onCycleSleepTimer = onCycleSleepTimer,
                            onOpenAudioFx = openAudioFxAction,
                            onOpenQueue = onOpenQueue
                        )
                    }
                }
            }
        } else {
            // --- PORTRAIT PHONE / COMPACT TABLET FULL PLAYER ---
            val artworkMaxSize = (maxHeight * 0.36f).coerceIn(180.dp, 320.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.testTag("full_player_collapse_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_collapse_player),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.player_now_playing).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = artworkPalette.accentColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    IconButton(
                        onClick = openAudioFxAction,
                        modifier = Modifier.testTag("full_player_audio_fx_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Audio Enhancements",
                            tint = if (playerState.skipSilenceEnabled || playerState.voiceBoostLevel != VoiceBoostLevel.OFF) {
                                artworkPalette.accentColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Responsive Album Art (scales down gracefully on small phones)
                PodcastArtwork(
                    artworkUrl = episode.artworkUrl,
                    title = episode.podcastName,
                    cornerRadius = 28.dp,
                    showTitleOverlayOnFallback = true,
                    modifier = Modifier
                        .size(artworkMaxSize)
                        .widthIn(max = 320.dp)
                        .aspectRatio(1f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Episode Title & Podcast Show Name
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = episode.podcastName,
                        style = MaterialTheme.typography.titleMedium,
                        color = artworkPalette.accentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Android "Squiggly" Wave Scrubber Bar
                AndroidWaveScrubber(
                    currentPositionMs = playerState.currentPositionMs,
                    durationMs = durationMs,
                    isPlaying = playerState.isPlaying,
                    onSeekTo = onSeekTo,
                    activeColor = artworkPalette.accentColor,
                    thumbColor = artworkPalette.accentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("full_player_scrub_slider")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onSkipBack,
                        modifier = Modifier
                            .size(60.dp)
                            .testTag("full_player_skip_back_15")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay,
                            contentDescription = stringResource(R.string.cd_skip_back_15),
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    MorphingPlayPauseButton(
                        isPlaying = playerState.isPlaying,
                        onClick = onPlayPause,
                        buttonSize = 84.dp,
                        iconSize = 42.dp,
                        containerColor = artworkPalette.containerColor,
                        contentColor = artworkPalette.onContainerColor,
                        modifier = Modifier.testTag("full_player_play_pause")
                    )

                    FilledTonalIconButton(
                        onClick = onSkipForward,
                        modifier = Modifier
                            .size(60.dp)
                            .testTag("full_player_skip_forward_30")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forward30,
                            contentDescription = stringResource(R.string.cd_skip_forward_30),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Adaptive Utility Controls (Never squashes text into multi-line)
                AdaptivePlayerUtilityControls(
                    playerState = playerState,
                    onCycleSpeed = onCycleSpeed,
                    onCycleSleepTimer = onCycleSleepTimer,
                    onOpenAudioFx = openAudioFxAction,
                    onOpenQueue = onOpenQueue
                )

                Spacer(modifier = Modifier.height(8.dp))
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

@Composable
fun AdaptivePlayerUtilityControls(
    playerState: PlayerUiState,
    onCycleSpeed: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onOpenAudioFx: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val isAudioFxActive = playerState.skipSilenceEnabled ||
        playerState.voiceBoostLevel != VoiceBoostLevel.OFF
    val isTimerActive = playerState.sleepTimerLabel != "Sleep"

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val canFitSingleRow = maxWidth >= 420.dp

        if (canFitSingleRow) {
            // Wide screen: All 4 controls fit comfortably on a single row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onCycleSpeed,
                    label = {
                        Text(
                            text = "${playerState.playbackSpeed}x",
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = "Playback Speed",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                FilterChip(
                    selected = isAudioFxActive,
                    onClick = onOpenAudioFx,
                    label = {
                        Text(
                            text = if (isAudioFxActive) "FX On" else "Boost",
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Audio FX",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                FilterChip(
                    selected = isTimerActive,
                    onClick = onCycleSleepTimer,
                    label = {
                        Text(
                            text = playerState.sleepTimerLabel,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sleep Timer",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                FilledTonalButton(
                    onClick = onOpenQueue,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Up Next (${playerState.queue.size})",
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        } else {
            // Compact phone width: 2 balanced rows so NO button ever wraps or truncates
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Speed, Audio FX / Boost, Sleep Timer (3 equal columns)
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
                            Icon(
                                imageVector = Icons.Outlined.Speed,
                                contentDescription = "Playback Speed",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    FilterChip(
                        selected = isAudioFxActive,
                        onClick = onOpenAudioFx,
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
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Audio FX",
                                modifier = Modifier.size(16.dp)
                            )
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
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = "Sleep Timer",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                // Row 2: Full-width Up Next Queue pill button
                FilledTonalButton(
                    onClick = onOpenQueue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Up Next Queue (${playerState.queue.size})",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEnhancementsSheet(
    playerState: PlayerUiState,
    onSelectSpeed: (Float) -> Unit,
    onToggleSkipSilence: (Boolean) -> Unit,
    onSelectVoiceBoost: (VoiceBoostLevel) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Playback & Audio FX",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // 1. Voice Boost / Volume Gain (Off | +6dB | +12dB)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Voice Boost (fixes quiet podcasts)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    VoiceBoostLevel.entries.forEachIndexed { index, level ->
                        SegmentedButton(
                            selected = playerState.voiceBoostLevel == level,
                            onClick = { onSelectVoiceBoost(level) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = VoiceBoostLevel.entries.size
                            )
                        ) {
                            Text(
                                text = level.label,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // 2. Trim Silence Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onToggleSkipSilence(!playerState.skipSilenceEnabled) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trim silence",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Automatically skips dead air and pauses in speech",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = playerState.skipSilenceEnabled,
                    onCheckedChange = onToggleSkipSilence
                )
            }

            // 3. One-Tap Playback Speed Presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Playback speed (${playerState.playbackSpeed}x)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val speeds = listOf(0.8f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    speeds.forEachIndexed { index, speed ->
                        SegmentedButton(
                            selected = playerState.playbackSpeed == speed,
                            onClick = { onSelectSpeed(speed) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = speeds.size
                            )
                        ) {
                            Text(
                                text = "${speed}x",
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastPlayerScaffold(
    playerState: PlayerUiState,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBack: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleSleepTimer: () -> Unit = {},
    onSelectSpeed: (Float) -> Unit = {},
    onToggleSkipSilence: (Boolean) -> Unit = {},
    onSelectVoiceBoost: (VoiceBoostLevel) -> Unit = {},
    onSelectEpisodeFromQueue: (EpisodeUiModel) -> Unit = {},
    onRemoveFromQueue: (EpisodeUiModel) -> Unit = {},
    onOpenQueue: () -> Unit = {},
    mainAppContent: @Composable (PaddingValues) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )
    val isSheetExpanded = bottomSheetState.currentValue == SheetValue.Expanded

    BackHandler(enabled = isSheetExpanded) {
        coroutineScope.launch {
            bottomSheetState.partialExpand()
        }
    }

    BottomSheetScaffold(
        scaffoldState = bottomSheetScaffoldState,
        sheetPeekHeight = if (playerState.currentEpisode != null) 70.dp else 0.dp,
        sheetDragHandle = null,
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxSize(),
        sheetContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isSheetExpanded) {
                    val currentEpisode = playerState.currentEpisode
                    if (currentEpisode != null) {
                        FullScreenPlayer(
                            episode = currentEpisode,
                            playerState = playerState,
                            onCollapse = {
                                coroutineScope.launch {
                                    bottomSheetState.partialExpand()
                                }
                            },
                            onPlayPause = onPlayPause,
                            onSeekTo = onSeekTo,
                            onSkipForward = onSkipForward,
                            onSkipBack = onSkipBack,
                            onCycleSpeed = onCycleSpeed,
                            onCycleSleepTimer = onCycleSleepTimer,
                            onSelectSpeed = onSelectSpeed,
                            onToggleSkipSilence = onToggleSkipSilence,
                            onSelectVoiceBoost = onSelectVoiceBoost,
                            onOpenQueue = {
                                coroutineScope.launch {
                                    bottomSheetState.partialExpand()
                                }
                                onOpenQueue()
                            }
                        )
                    }
                } else {
                    MiniPlayerBar(
                        playerUiState = playerState,
                        onExpandClick = {
                            coroutineScope.launch {
                                bottomSheetState.expand()
                            }
                        },
                        onPlayPauseClick = onPlayPause,
                        onSkipBackClick = onSkipBack,
                        onSkipForwardClick = onSkipForward
                    )
                }
            }
        }
    ) { innerSheetPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            mainAppContent(innerSheetPadding)
        }
    }
}
