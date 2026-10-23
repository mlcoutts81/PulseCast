package com.example.service.player

import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.models.EpisodeEntity
import com.example.data.models.PlaybackState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages playback state and playlists across a Media3 [ExoPlayer] instance and
 * [PodcastMediaLibraryService] session.
 *
 * - Tapping 'Play' on an episode plays it immediately via Media3 ExoPlayer.
 * - Tapping 'Add to Queue' appends the [MediaItem] to the ExoPlayer playlist so episodes play continuously.
 */
@UnstableApi
class PlayerController(
    context: Context,
    private val onProgressPersist: (episodeId: String, positionMs: Long, completed: Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    /**
     * Local Media3 ExoPlayer instance that guarantees immediate, synchronous MediaItem playlist
     * management and playback, and seamlessly hands over or mirrors state with [MediaController].
     */
    val localExoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(15_000L)
        .setSeekForwardIncrementMs(30_000L)
        .build()

    private val activePlayer: Player
        get() = mediaController ?: localExoPlayer

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _playlistMediaIds = MutableStateFlow<List<String>>(emptyList())
    val playlistMediaIds: StateFlow<List<String>> = _playlistMediaIds.asStateFlow()

    private val episodesById = mutableMapOf<String, EpisodeEntity>()
    private var positionTickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var lastPersistedSecond = -1L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncPlayerState()
            if (isPlaying) {
                startPositionTicker()
            } else {
                persistCurrentPosition(completed = false)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                persistCurrentPosition(completed = true)
            }
            syncPlayerState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncPlayerState()
            syncPlaylistMediaIds()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncPlaylistMediaIds()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _playbackState.update { it.copy(playbackSpeed = playbackParameters.speed) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = "Playback notice: ${error.localizedMessage ?: "Audio stream unavailable"}"
                )
            }
        }
    }

    init {
        localExoPlayer.addListener(playerListener)
        connectToService()
    }

    private fun connectToService() {
        if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            return
        }
        try {
            val sessionToken = SessionToken(
                appContext,
                ComponentName(appContext, PodcastMediaLibraryService::class.java)
            )
            val future = MediaController.Builder(appContext, sessionToken).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    try {
                        val controller = future.get()
                        mediaController = controller
                        controller.addListener(playerListener)
                        if (localExoPlayer.mediaItemCount > 0 && controller.mediaItemCount == 0) {
                            val items = (0 until localExoPlayer.mediaItemCount).map {
                                localExoPlayer.getMediaItemAt(it)
                            }
                            controller.setMediaItems(
                                items,
                                localExoPlayer.currentMediaItemIndex,
                                localExoPlayer.currentPosition
                            )
                            controller.prepare()
                            if (localExoPlayer.playWhenReady) {
                                localExoPlayer.pause()
                                controller.play()
                            }
                        }
                        syncPlayerState()
                        syncPlaylistMediaIds()
                    } catch (_: Exception) {
                        // Fallback to in-process localExoPlayer if service binding is unavailable
                    }
                },
                MoreExecutors.directExecutor()
            )
        } catch (_: Exception) {
            // Use localExoPlayer directly
        }
    }

    fun registerKnownEpisodes(episodes: List<EpisodeEntity>) {
        episodes.forEach { episodesById[it.id] = it }
        val current = _playbackState.value.currentEpisode
        if (current != null) {
            episodesById[current.id]?.let { updated ->
                _playbackState.update { it.copy(currentEpisode = updated) }
            }
        }
    }

    /**
     * Plays [episode] immediately via Media3 ExoPlayer and populates the upcoming queue items
     * so episodes play continuously.
     */
    fun playEpisode(episode: EpisodeEntity, upNextQueue: List<EpisodeEntity> = emptyList()) {
        episodesById[episode.id] = episode
        upNextQueue.forEach { episodesById[it.id] = it }

        val targetMediaItem = PodcastMediaLibraryService.episodeToPlayableMediaItem(episode)
        val remainingQueueItems = upNextQueue
            .filter { it.id != episode.id }
            .map { PodcastMediaLibraryService.episodeToPlayableMediaItem(it) }

        val fullPlaylist = listOf(targetMediaItem) + remainingQueueItems
        val startPosMs = if (episode.lastPlayedPositionMs > 0L && !episode.isCompleted) {
            episode.lastPlayedPositionMs
        } else {
            0L
        }

        _playbackState.update {
            it.copy(
                currentEpisode = episode,
                isPlaying = true,
                isBuffering = true,
                currentPositionMs = startPosMs,
                durationMs = (episode.durationSeconds * 1000L).coerceAtLeast(1000L),
                errorMessage = null
            )
        }

        val player = activePlayer
        player.setMediaItems(fullPlaylist, 0, startPosMs)
        player.prepare()
        player.play()

        if (player !== localExoPlayer) {
            localExoPlayer.setMediaItems(fullPlaylist, 0, startPosMs)
        }

        syncPlaylistMediaIds()
        startPositionTicker()
    }

    /**
     * Appends [episode] as a [MediaItem] to the ExoPlayer playlist so episodes play continuously.
     */
    fun appendEpisodeToQueue(episode: EpisodeEntity) {
        episodesById[episode.id] = episode
        val mediaItem = PodcastMediaLibraryService.episodeToPlayableMediaItem(episode)

        val player = activePlayer
        val existingIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (episode.id !in existingIds) {
            player.addMediaItem(mediaItem)
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
        }

        if (player !== localExoPlayer) {
            val localIds = (0 until localExoPlayer.mediaItemCount).map {
                localExoPlayer.getMediaItemAt(it).mediaId
            }
            if (episode.id !in localIds) {
                localExoPlayer.addMediaItem(mediaItem)
            }
        }

        if (_playbackState.value.currentEpisode == null) {
            _playbackState.update {
                it.copy(
                    currentEpisode = episode,
                    durationMs = (episode.durationSeconds * 1000L).coerceAtLeast(1000L)
                )
            }
        }

        syncPlaylistMediaIds()
    }

    fun removeEpisodeFromPlayerQueue(episodeId: String) {
        val player = activePlayer
        for (i in player.mediaItemCount - 1 downTo 0) {
            if (player.getMediaItemAt(i).mediaId == episodeId && i != player.currentMediaItemIndex) {
                player.removeMediaItem(i)
            }
        }
        if (player !== localExoPlayer) {
            for (i in localExoPlayer.mediaItemCount - 1 downTo 0) {
                if (localExoPlayer.getMediaItemAt(i).mediaId == episodeId && i != localExoPlayer.currentMediaItemIndex) {
                    localExoPlayer.removeMediaItem(i)
                }
            }
        }
        syncPlaylistMediaIds()
    }

    fun moveEpisodeInPlayerQueue(fromIndex: Int, toIndex: Int) {
        val player = activePlayer
        if (fromIndex in 0 until player.mediaItemCount && toIndex in 0 until player.mediaItemCount) {
            player.moveMediaItem(fromIndex, toIndex)
        }
        if (player !== localExoPlayer &&
            fromIndex in 0 until localExoPlayer.mediaItemCount &&
            toIndex in 0 until localExoPlayer.mediaItemCount
        ) {
            localExoPlayer.moveMediaItem(fromIndex, toIndex)
        }
        syncPlaylistMediaIds()
    }

    fun togglePlayPause() {
        val player = activePlayer
        if (player.isPlaying) {
            player.pause()
            _playbackState.update { it.copy(isPlaying = false) }
            persistCurrentPosition(completed = false)
        } else {
            if (player.mediaItemCount == 0) {
                val current = _playbackState.value.currentEpisode ?: return
                playEpisode(current)
            } else {
                player.play()
                _playbackState.update { it.copy(isPlaying = true) }
                startPositionTicker()
            }
        }
    }

    fun skipBackward15s() {
        val player = activePlayer
        val target = (player.currentPosition - 15_000L).coerceAtLeast(0L)
        player.seekTo(target)
        _playbackState.update { it.copy(currentPositionMs = target) }
    }

    fun skipForward30s() {
        val player = activePlayer
        val duration = player.duration.takeIf { it > 0 } ?: _playbackState.value.durationMs
        val target = if (duration > 0) {
            (player.currentPosition + 30_000L).coerceAtMost(duration)
        } else {
            player.currentPosition + 30_000L
        }
        player.seekTo(target)
        _playbackState.update { it.copy(currentPositionMs = target) }
    }

    fun seekToFraction(fraction: Float) {
        val player = activePlayer
        val duration = player.duration.takeIf { it > 0 } ?: _playbackState.value.durationMs
        if (duration <= 0L) return
        val targetMs = (duration * fraction.coerceIn(0f, 1f)).toLong()
        player.seekTo(targetMs)
        _playbackState.update { it.copy(currentPositionMs = targetMs) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.0f)
        activePlayer.setPlaybackSpeed(clamped)
        if (activePlayer !== localExoPlayer) {
            localExoPlayer.setPlaybackSpeed(clamped)
        }
        _playbackState.update { it.copy(playbackSpeed = clamped) }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _playbackState.update { it.copy(sleepTimerRemainingMinutes = null) }
            return
        }
        _playbackState.update { it.copy(sleepTimerRemainingMinutes = minutes) }
        sleepTimerJob = scope.launch {
            var remaining = minutes
            while (remaining > 0 && isActive) {
                delay(60_000L)
                remaining--
                _playbackState.update { it.copy(sleepTimerRemainingMinutes = remaining.takeIf { r -> r > 0 }) }
            }
            if (isActive) {
                activePlayer.pause()
                _playbackState.update { it.copy(isPlaying = false, sleepTimerRemainingMinutes = null) }
            }
        }
    }

    fun clearError() {
        _playbackState.update { it.copy(errorMessage = null) }
    }

    private fun syncPlaylistMediaIds() {
        val player = activePlayer
        val ids = (0 until player.mediaItemCount).map { index ->
            player.getMediaItemAt(index).mediaId
        }
        _playlistMediaIds.value = ids
    }

    private fun syncPlayerState() {
        val player = activePlayer
        val currentItem = player.currentMediaItem
        val resolvedEpisode = currentItem?.let { item ->
            episodesById[item.mediaId] ?: EpisodeEntity(
                id = item.mediaId,
                podcastFeedUrl = item.mediaMetadata.extras?.getString("podcastFeedUrl") ?: "",
                podcastTitle = item.mediaMetadata.artist?.toString() ?: "PulseCast",
                podcastAuthor = item.mediaMetadata.artist?.toString() ?: "",
                title = item.mediaMetadata.title?.toString() ?: "Episode",
                description = item.mediaMetadata.description?.toString() ?: "",
                pubDate = item.mediaMetadata.extras?.getString("pubDate") ?: "",
                audioUrl = item.localConfiguration?.uri?.toString() ?: "",
                durationSeconds = item.mediaMetadata.extras?.getLong("durationSeconds") ?: 1800L,
                artworkUrl = item.mediaMetadata.artworkUri?.toString() ?: ""
            )
        } ?: _playbackState.value.currentEpisode

        val reportedDuration = player.duration.takeIf { it > 0L }
            ?: resolvedEpisode?.let { it.durationSeconds * 1000L }
            ?: _playbackState.value.durationMs

        _playbackState.update { state ->
            state.copy(
                currentEpisode = resolvedEpisode,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = reportedDuration.coerceAtLeast(1000L),
                playbackSpeed = player.playbackParameters.speed
            )
        }
    }

    private fun startPositionTicker() {
        if (positionTickerJob?.isActive == true) return
        positionTickerJob = scope.launch {
            while (isActive) {
                val player = activePlayer
                if (player.isPlaying) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.takeIf { it > 0L } ?: _playbackState.value.durationMs
                    _playbackState.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = dur.coerceAtLeast(1000L)
                        )
                    }
                    val currentSec = pos / 1000L
                    if (currentSec > 0 && currentSec % 5L == 0L && currentSec != lastPersistedSecond) {
                        lastPersistedSecond = currentSec
                        persistCurrentPosition(completed = false)
                    }
                }
                delay(500L)
            }
        }
    }

    private fun persistCurrentPosition(completed: Boolean) {
        val currentEp = _playbackState.value.currentEpisode ?: return
        val pos = activePlayer.currentPosition.coerceAtLeast(0L)
        onProgressPersist(currentEp.id, pos, completed)
    }

    fun release() {
        positionTickerJob?.cancel()
        sleepTimerJob?.cancel()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        localExoPlayer.removeListener(playerListener)
        localExoPlayer.release()
    }
}
