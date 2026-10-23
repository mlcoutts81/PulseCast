package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.components.FullScreenPlayer
import com.example.ui.components.PlayerUiState

@Composable
fun FullPlayerScreen(
    playerUiState: PlayerUiState,
    onCollapseClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipBack15Click: () -> Unit,
    onSkipForward30Click: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onCyclePlaybackSpeed: () -> Unit,
    onCycleSleepTimer: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val episode = playerUiState.currentEpisode ?: return
    FullScreenPlayer(
        episode = episode,
        playerState = playerUiState,
        onCollapse = onCollapseClick,
        onPlayPause = onPlayPauseClick,
        onSeekTo = onSeekToMs,
        onSkipForward = onSkipForward30Click,
        onSkipBack = onSkipBack15Click,
        onCycleSpeed = onCyclePlaybackSpeed,
        onCycleSleepTimer = onCycleSleepTimer,
        onOpenQueue = onOpenQueue
    )
}
