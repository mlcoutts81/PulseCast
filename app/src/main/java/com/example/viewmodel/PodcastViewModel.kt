package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.data.local.PodcastDownloadHelper
import com.example.data.local.PodcastLocalStorage
import com.example.data.models.ItunesPodcastItem
import com.example.data.models.PodcastEntity
import com.example.data.rss.ItunesSearchApi
import com.example.data.rss.PodcastXmlParser
import com.example.service.player.PodcastMediaService
import com.example.ui.components.EpisodeUiModel
import com.example.ui.components.PlayerUiState
import com.example.ui.components.VoiceBoostLevel
import com.example.ui.screens.DefaultDummyEpisodes
import com.example.ui.screens.DefaultDummySubscriptions
import com.example.ui.screens.HomeEpisodeUiModel
import com.example.ui.screens.PodcastSubscriptionUiModel
import com.example.ui.theme.AppThemeConfig
import com.example.ui.widget.PodcastGlanceWidget
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class BottomNavTab {
    HOME,
    EXPLORE,
    ACTIVITY
}

enum class ActivitySegment {
    QUEUE,
    DOWNLOADS,
    HISTORY
}

private enum class SleepTimerMode {
    OFF,
    MIN_15,
    MIN_30,
    END_OF_EPISODE
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: List<ItunesPodcastItem>, val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@UnstableApi
class PodcastViewModel(application: Application) : AndroidViewModel(application) {

    private val localStorage = PodcastLocalStorage(application)
    private var mediaController: MediaController? = null
    private var fallbackLoudnessEnhancer: LoudnessEnhancer? = null

    // --- Theme Configuration State ---
    private val _themeConfig = MutableStateFlow(localStorage.loadThemeConfig())
    val themeConfig: StateFlow<AppThemeConfig> = _themeConfig.asStateFlow()

    fun updateThemeConfig(config: AppThemeConfig) {
        _themeConfig.value = config
        localStorage.saveThemeConfig(config)
    }

    // Local ExoPlayer fallback so real audio streaming works immediately even before MediaController binds
    private val fallbackExoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(getApplication())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    private val activePlayer: Player
        get() = mediaController ?: fallbackExoPlayer

    private val itunesApi by lazy { ItunesSearchApi.create() }

    // --- Home & Activity State ---
    private val subscribedFeedUrls = mutableSetOf<String>()

    private val _subscriptions = MutableStateFlow<List<PodcastSubscriptionUiModel>>(emptyList())
    val subscriptions: StateFlow<List<PodcastSubscriptionUiModel>> = _subscriptions.asStateFlow()

    private val _feedEpisodes = MutableStateFlow<List<HomeEpisodeUiModel>>(emptyList())
    val feedEpisodes: StateFlow<List<HomeEpisodeUiModel>> = _feedEpisodes.asStateFlow()

    private val _queueEpisodes = MutableStateFlow<List<HomeEpisodeUiModel>>(emptyList())
    val queueEpisodes: StateFlow<List<HomeEpisodeUiModel>> = _queueEpisodes.asStateFlow()

    private val _downloadedEpisodes = MutableStateFlow<List<HomeEpisodeUiModel>>(emptyList())
    val downloadedEpisodes: StateFlow<List<HomeEpisodeUiModel>> = _downloadedEpisodes.asStateFlow()

    private val _historyEpisodes = MutableStateFlow<List<HomeEpisodeUiModel>>(emptyList())
    val historyEpisodes: StateFlow<List<HomeEpisodeUiModel>> = _historyEpisodes.asStateFlow()

    private val _hidePlayedEpisodes = MutableStateFlow(localStorage.loadHidePlayed())
    val hidePlayedEpisodes: StateFlow<Boolean> = _hidePlayedEpisodes.asStateFlow()

    fun toggleHidePlayed() {
        val next = !_hidePlayedEpisodes.value
        _hidePlayedEpisodes.value = next
        localStorage.saveHidePlayed(next)
    }

    // --- Expandable Player UI State ---
    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()

    // --- Sleep Timer State (Off -> 15m -> 30m -> End of Ep) ---
    private var sleepTimerMode = SleepTimerMode.OFF
    private var sleepTimerJob: Job? = null

    // --- Explore Search State ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    private var searchDebounceJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            setupFallbackLoudnessEnhancer(audioSessionId, _playerUiState.value.voiceBoostLevel.gainDb)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            PodcastMediaService.isCurrentlyPlaying = isPlaying
            _playerUiState.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) {
                saveCurrentPlaybackPosition()
            }
            viewModelScope.launch {
                PodcastGlanceWidget.updateAllWidgets(getApplication())
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _playerUiState.value.currentEpisode?.let { endedEp ->
                    updateEpisodeResumePosition(endedEp.id, 0L, endedEp.durationMs)
                }
                if (sleepTimerMode == SleepTimerMode.END_OF_EPISODE) {
                    activePlayer.pause()
                    sleepTimerMode = SleepTimerMode.OFF
                    _playerUiState.update { it.copy(isPlaying = false, sleepTimerLabel = "Sleep") }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Save timestamp of the outgoing track immediately before transitioning
            saveCurrentPlaybackPosition()

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                sleepTimerMode == SleepTimerMode.END_OF_EPISODE
            ) {
                activePlayer.pause()
                sleepTimerMode = SleepTimerMode.OFF
                _playerUiState.update { it.copy(isPlaying = false, sleepTimerLabel = "Sleep") }
            }
            mediaItem?.let { item ->
                val matched = (_feedEpisodes.value + _queueEpisodes.value + _historyEpisodes.value + _downloadedEpisodes.value)
                    .find { it.id == item.mediaId }
                if (matched != null) {
                    val savedPos = localStorage.getEpisodePosition(matched.id)
                        .takeIf { it > 0L }
                        ?: matched.resumePositionMs.coerceAtLeast(0L)
                    updateNowPlayingUi(matched, initialPositionMs = savedPos)
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && savedPos > 0L) {
                        activePlayer.seekTo(savedPos)
                    }
                }
            }
        }
    }

    init {
        fallbackExoPlayer.addListener(playerListener)
        restorePersistedState()
        restoreLastPlayedToMiniPlayer()
        initializeMediaController()
        startProgressPoller()
    }

    private fun restorePersistedState() {
        val isFirstLaunch = localStorage.isFirstLaunch()
        val savedPositions = localStorage.loadAllPositions()
        val playedIds = localStorage.loadPlayedEpisodeIds()
        val savedSkipSilence = localStorage.loadSkipSilence()
        val savedVoiceBoost = VoiceBoostLevel.fromGainDb(localStorage.loadVoiceBoostDb())

        _playerUiState.update {
            it.copy(
                skipSilenceEnabled = savedSkipSilence,
                voiceBoostLevel = savedVoiceBoost
            )
        }
        fallbackExoPlayer.skipSilenceEnabled = savedSkipSilence

        val savedHistory = localStorage.loadHistory()
        if (savedHistory.isNotEmpty()) {
            _historyEpisodes.value = savedHistory.map { ep ->
                ep.copy(
                    isPlayed = ep.id in playedIds || ep.isPlayed,
                    resumePositionMs = savedPositions[ep.id] ?: ep.resumePositionMs
                )
            }
        }

        val savedDownloads = localStorage.loadDownloads()
        if (savedDownloads.isNotEmpty()) {
            _downloadedEpisodes.value = savedDownloads.map { ep ->
                ep.copy(
                    isPlayed = ep.id in playedIds || ep.isPlayed,
                    resumePositionMs = savedPositions[ep.id] ?: ep.resumePositionMs
                )
            }
        }

        val savedQueue = localStorage.loadQueue()
        if (savedQueue.isNotEmpty()) {
            _queueEpisodes.value = savedQueue.map { ep ->
                ep.copy(
                    isPlayed = ep.id in playedIds || ep.isPlayed,
                    resumePositionMs = savedPositions[ep.id] ?: ep.resumePositionMs
                )
            }
        } else if (isFirstLaunch) {
            val defaultQueue = DefaultDummyEpisodes.filter { it.isQueued }.map { ep ->
                ep.copy(
                    isPlayed = ep.id in playedIds || ep.isPlayed,
                    resumePositionMs = savedPositions[ep.id] ?: ep.resumePositionMs
                )
            }
            _queueEpisodes.value = defaultQueue
            localStorage.saveQueue(defaultQueue)
        }

        if (isFirstLaunch) {
            // Seed starter subscriptions only once on the very first install
            val starterUrls = DefaultDummySubscriptions.map { it.feedUrl.trim() }
            subscribedFeedUrls.addAll(starterUrls)
            _subscriptions.value = DefaultDummySubscriptions
            _feedEpisodes.value = DefaultDummyEpisodes.map { ep ->
                ep.copy(
                    isPlayed = ep.id in playedIds || ep.isPlayed,
                    resumePositionMs = savedPositions[ep.id] ?: ep.resumePositionMs
                )
            }
            localStorage.saveSubscribedUrls(subscribedFeedUrls)
            localStorage.markFirstLaunchComplete()
            loadStarterPodcast()
        } else {
            val savedUrls = localStorage.loadSubscribedUrls()
            subscribedFeedUrls.clear()
            subscribedFeedUrls.addAll(savedUrls.map { it.trim() })

            // Retain only starter/dummy items whose feedUrl is still subscribed
            val retainedDummySubs = DefaultDummySubscriptions.filter { sub ->
                subscribedFeedUrls.any { it.equals(sub.feedUrl.trim(), ignoreCase = true) }
            }
            val retainedTitles = retainedDummySubs.map { it.title.lowercase() }.toSet()
            _subscriptions.value = retainedDummySubs
            _feedEpisodes.value = DefaultDummyEpisodes
                .filter { ep -> ep.podcastName.lowercase() in retainedTitles }
                .map { ep ->
                    ep.copy(
                        isPlayed = ep.id in playedIds || ep.isPlayed,
                        resumePositionMs = savedPositions[ep.id] ?: ep.resumePositionMs
                    )
                }

            savedUrls.forEach { url ->
                addRssFeedUrl(url)
            }
        }

        syncQueueStateToUi()
        PodcastMediaService.cachedFeedItems = _feedEpisodes.value.map { it.toMediaItem() }
    }

    private fun restoreLastPlayedToMiniPlayer() {
        val lastPlayed = _historyEpisodes.value.firstOrNull() ?: _queueEpisodes.value.firstOrNull() ?: return
        val savedPositionMs = localStorage.getEpisodePosition(lastPlayed.id)
            .takeIf { it > 0L }
            ?: lastPlayed.resumePositionMs.coerceAtLeast(0L)

        // Immediately populate the Mini-Player and Tablet Docked Player UI (paused)
        updateNowPlayingUi(lastPlayed, initialPositionMs = savedPositionMs)

        val playlistItems = buildList {
            add(lastPlayed.toMediaItem())
            _queueEpisodes.value
                .filterNot { it.id == lastPlayed.id }
                .forEach { add(it.toMediaItem()) }
        }
        if (playlistItems.isNotEmpty()) {
            fallbackExoPlayer.setMediaItems(
                playlistItems,
                /* startIndex = */ 0,
                /* startPositionMs = */ savedPositionMs
            )
            fallbackExoPlayer.prepare()
            fallbackExoPlayer.playWhenReady = false
        }
    }

    private fun initializeMediaController() {
        if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            return
        }
        try {
            val context = getApplication<Application>()
            val sessionToken = SessionToken(context, ComponentName(context, PodcastMediaService::class.java))
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

            controllerFuture.addListener({
                try {
                    val controller = controllerFuture.get()
                    mediaController = controller

                    // Sync persisted Audio FX (Skip Silence & Voice Boost) to MediaLibraryService
                    setSkipSilence(_playerUiState.value.skipSilenceEnabled)
                    setVoiceBoost(_playerUiState.value.voiceBoostLevel)

                    // Sync or cue the restored Mini-Player episode in ExoPlayer
                    val currentMediaItem = controller.currentMediaItem
                    val restoredEpisode = _historyEpisodes.value.firstOrNull() ?: _queueEpisodes.value.firstOrNull()

                    if (currentMediaItem != null && (controller.isPlaying || currentMediaItem.mediaId == restoredEpisode?.id)) {
                        // Service is already active (e.g. playing in background or Android Auto) -> sync UI to it
                        val matched = (_feedEpisodes.value + _historyEpisodes.value + _queueEpisodes.value + _downloadedEpisodes.value)
                            .find { it.id == currentMediaItem.mediaId }
                        if (matched != null) {
                            val savedMs = localStorage.getEpisodePosition(matched.id)
                                .takeIf { it > 0L }
                                ?: matched.resumePositionMs.coerceAtLeast(0L)
                            val effectivePos = controller.currentPosition.takeIf { it > 0L } ?: savedMs
                            updateNowPlayingUi(matched, initialPositionMs = effectivePos)
                            _playerUiState.update { it.copy(isPlaying = controller.isPlaying) }
                            if (!controller.isPlaying && controller.currentPosition <= 0L && savedMs > 0L) {
                                controller.seekTo(0, savedMs)
                            }
                        }
                    } else if (restoredEpisode != null) {
                        // Cold start -> Cue the last-played episode and queue at its saved timestamp (Paused)
                        val resumeMs = localStorage.getEpisodePosition(restoredEpisode.id)
                            .takeIf { it > 0L }
                            ?: restoredEpisode.resumePositionMs.coerceAtLeast(0L)
                        val playlistItems = buildList {
                            add(restoredEpisode.toMediaItem())
                            _queueEpisodes.value
                                .filterNot { it.id == restoredEpisode.id }
                                .forEach { add(it.toMediaItem()) }
                        }
                        controller.setMediaItems(playlistItems, /* startIndex = */ 0, /* startPositionMs = */ resumeMs)
                        controller.prepare()
                        controller.playWhenReady = false // Stay paused until the user taps Play
                    }

                    controller.addListener(playerListener)
                } catch (_: Exception) {
                    // Use fallbackExoPlayer when service binding is unavailable
                }
            }, MoreExecutors.directExecutor())
        } catch (_: Exception) {
            // Use fallbackExoPlayer
        }
    }

    private fun startProgressPoller() {
        viewModelScope.launch {
            var secondsCounter = 0
            while (isActive) {
                val controller = mediaController ?: fallbackExoPlayer
                val currentEp = _playerUiState.value.currentEpisode
                if (controller.isPlaying && currentEp != null) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val duration = controller.duration.takeIf { it > 0 } ?: currentEp.durationMs

                    // Only trigger UI state emission if the displayed second actually changed
                    if (kotlin.math.abs(pos - _playerUiState.value.currentPositionMs) >= 950L) {
                        _playerUiState.update { state ->
                            state.copy(
                                currentPositionMs = pos,
                                currentEpisode = if (state.currentEpisode?.durationMs != duration) {
                                    state.currentEpisode?.copy(durationMs = duration)
                                } else {
                                    state.currentEpisode
                                }
                            )
                        }
                    }

                    secondsCounter++
                    // Write to SharedPreferences ONLY every 15 seconds (not every 2s!) without rebuilding card lists
                    if (secondsCounter >= 15) {
                        secondsCounter = 0
                        saveEpisodeProgress(currentEp.id, pos, duration, updateCardLists = false)
                    }
                }
                delay(1000L) // 1s interval
            }
        }
    }

    private fun saveCurrentPlaybackPosition() {
        val currentEp = _playerUiState.value.currentEpisode ?: return
        val controller = activePlayer
        val posMs = controller.currentPosition.takeIf { it > 0L }
            ?: _playerUiState.value.currentPositionMs
        val durationMs = controller.duration.takeIf { it > 0L } ?: currentEp.durationMs
        saveEpisodeProgress(currentEp.id, posMs, durationMs, updateCardLists = true)
    }

    private fun updateEpisodeResumePosition(episodeId: String, positionMs: Long, durationMs: Long) {
        saveEpisodeProgress(episodeId, positionMs, durationMs, updateCardLists = true)
    }

    private fun saveEpisodeProgress(
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        updateCardLists: Boolean = true
    ) {
        localStorage.saveEpisodePosition(episodeId, positionMs, durationMs)
        if (!updateCardLists) return

        val savedPos = localStorage.getEpisodePosition(episodeId)
        _feedEpisodes.update { list ->
            list.map { if (it.id == episodeId) it.copy(resumePositionMs = savedPos) else it }
        }
        _queueEpisodes.update { list ->
            list.map { if (it.id == episodeId) it.copy(resumePositionMs = savedPos) else it }
        }
        _downloadedEpisodes.update { list ->
            list.map { if (it.id == episodeId) it.copy(resumePositionMs = savedPos) else it }
        }
        _historyEpisodes.update { list ->
            list.map { if (it.id == episodeId) it.copy(resumePositionMs = savedPos) else it }
        }
    }

    // --- Play Episode Immediately (checks offline download URI + seeks to saved resume position) ---
    fun playEpisode(episode: HomeEpisodeUiModel) {
        // Save outgoing track's position immediately before switching
        if (_playerUiState.value.currentEpisode != null &&
            _playerUiState.value.currentEpisode?.id != episode.id
        ) {
            saveCurrentPlaybackPosition()
        }

        val context = getApplication<Application>()
        val resolvedPlaybackUri = PodcastDownloadHelper.getOfflinePlaybackUri(context, episode)
        val savedPositionMs = localStorage.getEpisodePosition(episode.id)
            .takeIf { it > 0L }
            ?: episode.resumePositionMs.coerceAtLeast(0L)
        val playableEpisode = episode.copy(
            audioUrl = resolvedPlaybackUri,
            resumePositionMs = savedPositionMs
        )

        updateNowPlayingUi(playableEpisode, initialPositionMs = savedPositionMs)
        addToHistory(playableEpisode)

        val controller = activePlayer
        val existingIndex = (0 until controller.mediaItemCount).indexOfFirst {
            controller.getMediaItemAt(it).mediaId == playableEpisode.id
        }

        val targetIndex = if (existingIndex != -1) {
            controller.removeMediaItem(existingIndex)
            controller.addMediaItem(existingIndex, playableEpisode.toMediaItem(resolvedPlaybackUri))
            existingIndex
        } else {
            controller.addMediaItem(0, playableEpisode.toMediaItem(resolvedPlaybackUri))
            0
        }
        controller.seekTo(targetIndex, savedPositionMs)
        controller.prepare()
        controller.play()
        _playerUiState.update {
            it.copy(
                isPlaying = true,
                currentPositionMs = savedPositionMs
            )
        }
    }

    private fun updateNowPlayingUi(
        episode: HomeEpisodeUiModel,
        initialPositionMs: Long = _playerUiState.value.currentPositionMs
    ) {
        _playerUiState.update {
            it.copy(
                currentPositionMs = initialPositionMs,
                currentEpisode = EpisodeUiModel(
                    id = episode.id,
                    title = episode.title,
                    podcastName = episode.podcastName,
                    artworkUrl = episode.artworkUrl,
                    durationMs = episode.durationMs
                )
            )
        }
    }

    // --- Queue Management (Syncs UI + ExoPlayer Playlist + LocalStorage + Android Auto) ---
    fun toggleEpisodePlayed(episode: HomeEpisodeUiModel) {
        val newPlayedState = !episode.isPlayed
        localStorage.setEpisodePlayedStatus(episode.id, newPlayedState)

        // If marked as played, also remove it from the active Up Next Queue so finished shows don't clutter the queue
        if (newPlayedState && episode.isQueued) {
            toggleQueue(episode)
        }

        val updateItem: (HomeEpisodeUiModel) -> HomeEpisodeUiModel = { item ->
            if (item.id == episode.id) {
                item.copy(
                    isPlayed = newPlayedState,
                    resumePositionMs = if (newPlayedState) 0L else item.resumePositionMs,
                    isQueued = if (newPlayedState) false else item.isQueued
                )
            } else {
                item
            }
        }

        _feedEpisodes.update { list -> list.map(updateItem) }
        _queueEpisodes.update { list -> list.map(updateItem) }
        _downloadedEpisodes.update { list -> list.map(updateItem) }
        _historyEpisodes.update { list -> list.map(updateItem) }
    }

    fun toggleQueue(episode: HomeEpisodeUiModel) {
        val currentQueue = _queueEpisodes.value.toMutableList()
        val alreadyQueued = currentQueue.any { it.id == episode.id }

        if (alreadyQueued) {
            removeFromQueue(episode)
        } else {
            currentQueue.add(episode.copy(isQueued = true))
            _queueEpisodes.value = currentQueue
            localStorage.saveQueue(_queueEpisodes.value)
            activePlayer.addMediaItem(episode.toMediaItem())
            syncQueueStateToUi()
        }
        viewModelScope.launch {
            PodcastGlanceWidget.updateAllWidgets(getApplication())
        }
    }

    fun removeFromQueue(episode: HomeEpisodeUiModel) {
        _queueEpisodes.update { list -> list.filterNot { it.id == episode.id } }
        localStorage.saveQueue(_queueEpisodes.value)
        val controller = activePlayer
        for (i in 0 until controller.mediaItemCount) {
            if (controller.getMediaItemAt(i).mediaId == episode.id) {
                controller.removeMediaItem(i)
                break
            }
        }
        syncQueueStateToUi()
    }

    fun playEpisodeFromQueue(episode: EpisodeUiModel) {
        val target = _queueEpisodes.value.find { it.id == episode.id }
            ?: _feedEpisodes.value.find { it.id == episode.id }
            ?: _downloadedEpisodes.value.find { it.id == episode.id }
            ?: _historyEpisodes.value.find { it.id == episode.id }
        if (target != null) {
            playEpisode(target)
        }
    }

    fun removeFromQueue(episode: EpisodeUiModel) {
        _queueEpisodes.value.find { it.id == episode.id }?.let { removeFromQueue(it) }
    }

    fun moveQueueItemUp(index: Int) {
        val list = _queueEpisodes.value.toMutableList()
        if (index > 0 && index < list.size) {
            val item = list.removeAt(index)
            list.add(index - 1, item)
            _queueEpisodes.value = list
            localStorage.saveQueue(_queueEpisodes.value)
            if (index < activePlayer.mediaItemCount) {
                activePlayer.moveMediaItem(index, index - 1)
            }
            syncQueueStateToUi()
        }
    }

    fun moveQueueItemDown(index: Int) {
        val list = _queueEpisodes.value.toMutableList()
        if (index >= 0 && index < list.lastIndex) {
            val item = list.removeAt(index)
            list.add(index + 1, item)
            _queueEpisodes.value = list
            localStorage.saveQueue(_queueEpisodes.value)
            if (index + 1 < activePlayer.mediaItemCount) {
                activePlayer.moveMediaItem(index, index + 1)
            }
            syncQueueStateToUi()
        }
    }

    private fun syncQueueStateToUi() {
        val queuedIds = _queueEpisodes.value.map { it.id }.toSet()
        _feedEpisodes.update { feed ->
            feed.map { ep -> ep.copy(isQueued = ep.id in queuedIds) }
        }
        _playerUiState.update { state ->
            state.copy(
                queue = _queueEpisodes.value.map { ep ->
                    EpisodeUiModel(ep.id, ep.title, ep.podcastName, ep.artworkUrl, ep.durationMs)
                }
            )
        }
        viewModelScope.launch {
            PodcastGlanceWidget.updateAllWidgets(getApplication())
        }
    }

    // --- Transport Controls ---
    fun togglePlayPause() {
        val controller = activePlayer
        if (controller.isPlaying) {
            saveCurrentPlaybackPosition()
            controller.pause()
            PodcastMediaService.isCurrentlyPlaying = false
            _playerUiState.update { it.copy(isPlaying = false) }
        } else {
            controller.play()
            PodcastMediaService.isCurrentlyPlaying = true
            _playerUiState.update { it.copy(isPlaying = true) }
        }
        viewModelScope.launch {
            PodcastGlanceWidget.updateAllWidgets(getApplication())
        }
    }

    fun seekTo(positionMs: Long) {
        activePlayer.seekTo(positionMs)
        _playerUiState.update { it.copy(currentPositionMs = positionMs) }
        _playerUiState.value.currentEpisode?.let { currentEp ->
            updateEpisodeResumePosition(currentEp.id, positionMs, currentEp.durationMs)
        }
    }

    fun skipForward() {
        val controller = activePlayer
        val maxDuration = controller.duration.takeIf { it > 0 }
            ?: _playerUiState.value.currentEpisode?.durationMs
            ?: Long.MAX_VALUE
        val target = (controller.currentPosition + 30_000L).coerceAtMost(maxDuration)
        controller.seekTo(target)
        _playerUiState.update { it.copy(currentPositionMs = target) }
        _playerUiState.value.currentEpisode?.let { currentEp ->
            updateEpisodeResumePosition(currentEp.id, target, currentEp.durationMs)
        }
    }

    fun skipBack() {
        val controller = activePlayer
        val target = (controller.currentPosition - 15_000L).coerceAtLeast(0L)
        controller.seekTo(target)
        _playerUiState.update { it.copy(currentPositionMs = target) }
        _playerUiState.value.currentEpisode?.let { currentEp ->
            updateEpisodeResumePosition(currentEp.id, target, currentEp.durationMs)
        }
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 0.8f)
        val current = _playerUiState.value.playbackSpeed
        val nextSpeed = speeds[(speeds.indexOf(current) + 1) % speeds.size]
        setPlaybackSpeed(nextSpeed)
    }

    fun setPlaybackSpeed(speed: Float) {
        activePlayer.setPlaybackSpeed(speed)
        _playerUiState.update { it.copy(playbackSpeed = speed) }
    }

    fun setSkipSilence(enabled: Boolean) {
        _playerUiState.update { it.copy(skipSilenceEnabled = enabled) }
        localStorage.saveSkipSilence(enabled)
        fallbackExoPlayer.skipSilenceEnabled = enabled

        val args = Bundle().apply {
            putBoolean(PodcastMediaService.EXTRA_SKIP_SILENCE, enabled)
        }
        mediaController?.sendCustomCommand(
            SessionCommand(PodcastMediaService.CUSTOM_ACTION_SET_SKIP_SILENCE, Bundle.EMPTY),
            args
        )
    }

    fun toggleSkipSilence() {
        setSkipSilence(!_playerUiState.value.skipSilenceEnabled)
    }

    fun setVoiceBoost(level: VoiceBoostLevel) {
        _playerUiState.update { it.copy(voiceBoostLevel = level) }
        localStorage.saveVoiceBoostDb(level.gainDb)
        applyFallbackVoiceBoost(level.gainDb)

        val args = Bundle().apply {
            putInt(PodcastMediaService.EXTRA_BOOST_DB, level.gainDb)
        }
        mediaController?.sendCustomCommand(
            SessionCommand(PodcastMediaService.CUSTOM_ACTION_SET_VOICE_BOOST, Bundle.EMPTY),
            args
        )
    }

    fun cycleVoiceBoost() {
        val levels = VoiceBoostLevel.entries
        val nextLevel = levels[(levels.indexOf(_playerUiState.value.voiceBoostLevel) + 1) % levels.size]
        setVoiceBoost(nextLevel)
    }

    private fun setupFallbackLoudnessEnhancer(audioSessionId: Int, boostDb: Int) {
        runCatching {
            fallbackLoudnessEnhancer?.release()
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                fallbackLoudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    setTargetGain(boostDb * 100)
                    enabled = boostDb > 0
                }
            }
        }
    }

    private fun applyFallbackVoiceBoost(boostDb: Int) {
        runCatching {
            val enhancer = fallbackLoudnessEnhancer
            if (enhancer == null && fallbackExoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                setupFallbackLoudnessEnhancer(fallbackExoPlayer.audioSessionId, boostDb)
            } else if (enhancer != null) {
                enhancer.setTargetGain(boostDb * 100)
                enhancer.enabled = boostDb > 0
            }
        }
    }

    // --- One-Tap Cycling Sleep Timer (Off -> 15m -> 30m -> End of Ep) ---
    fun cycleSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerMode = when (sleepTimerMode) {
            SleepTimerMode.OFF -> SleepTimerMode.MIN_15
            SleepTimerMode.MIN_15 -> SleepTimerMode.MIN_30
            SleepTimerMode.MIN_30 -> SleepTimerMode.END_OF_EPISODE
            SleepTimerMode.END_OF_EPISODE -> SleepTimerMode.OFF
        }

        when (sleepTimerMode) {
            SleepTimerMode.OFF -> {
                _playerUiState.update { it.copy(sleepTimerLabel = "Sleep") }
            }
            SleepTimerMode.MIN_15 -> {
                startCountdownSleepTimer(totalSeconds = 15 * 60)
            }
            SleepTimerMode.MIN_30 -> {
                startCountdownSleepTimer(totalSeconds = 30 * 60)
            }
            SleepTimerMode.END_OF_EPISODE -> {
                _playerUiState.update { it.copy(sleepTimerLabel = "End of Ep") }
            }
        }
    }

    private fun startCountdownSleepTimer(totalSeconds: Int) {
        sleepTimerJob = viewModelScope.launch {
            var remainingSeconds = totalSeconds
            while (remainingSeconds > 0 && isActive) {
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                _playerUiState.update {
                    it.copy(sleepTimerLabel = String.format("%02d:%02d", mins, secs))
                }
                delay(1000L)
                remainingSeconds--
            }
            if (isActive) {
                activePlayer.pause()
                sleepTimerMode = SleepTimerMode.OFF
                _playerUiState.update { it.copy(isPlaying = false, sleepTimerLabel = "Sleep") }
            }
        }
    }

    // --- RSS & OPML Actions ---
    fun addRssFeedUrl(url: String) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return

        if (subscribedFeedUrls.none { it.equals(normalizedUrl, ignoreCase = true) }) {
            subscribedFeedUrls.add(normalizedUrl)
            localStorage.saveSubscribedUrls(subscribedFeedUrls)
        }

        viewModelScope.launch {
            PodcastXmlParser.fetchAndParseRss(normalizedUrl).onSuccess { parsed ->
                // Guard against race condition if the user unsubscribed while fetchAndParseRss was in flight
                if (subscribedFeedUrls.none { it.equals(normalizedUrl, ignoreCase = true) }) {
                    return@onSuccess
                }

                val queuedIds = _queueEpisodes.value.map { it.id }.toSet()
                val savedPositions = localStorage.loadAllPositions()
                val playedIds = localStorage.loadPlayedEpisodeIds()
                val syncedEpisodes = parsed.episodes.map { ep ->
                    ep.copy(
                        isQueued = ep.id in queuedIds,
                        isPlayed = ep.id in playedIds,
                        resumePositionMs = savedPositions[ep.id] ?: ep.resumePositionMs
                    )
                }

                // Replace any existing subscription for this feedUrl so title/podcastName stay in sync
                _subscriptions.update { current ->
                    val existingIndex = current.indexOfFirst {
                        it.feedUrl.trim().equals(normalizedUrl, ignoreCase = true)
                    }
                    if (existingIndex != -1) {
                        current.toMutableList().apply { set(existingIndex, parsed.subscription) }
                    } else {
                        current + parsed.subscription
                    }
                }

                val activeTitles = _subscriptions.value.map { it.title.lowercase() }.toSet()
                _feedEpisodes.update { current ->
                    (syncedEpisodes + current)
                        .distinctBy { ep -> ep.id }
                        .filter { ep -> ep.podcastName.lowercase() in activeTitles }
                }

                PodcastMediaService.cachedFeedItems = _feedEpisodes.value.map { it.toMediaItem() }
            }
        }
    }

    fun importOpmlUri(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            PodcastXmlParser.parseOpmlUri(context, uri).onSuccess { urls ->
                val limitedUrls = urls.map { it.trim() }.filter { it.isNotEmpty() }.take(15)
                subscribedFeedUrls.addAll(limitedUrls)
                localStorage.saveSubscribedUrls(subscribedFeedUrls)
                limitedUrls.forEach { feedUrl -> addRssFeedUrl(feedUrl) }
            }
        }
    }

    fun exportOpmlUri(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val currentSubs = _subscriptions.value
            PodcastXmlParser.exportOpmlToUri(context, uri, currentSubs)
        }
    }

    fun unsubscribeFromPodcast(subscription: PodcastSubscriptionUiModel) {
        val cleanUrl = subscription.feedUrl.trim()
        subscribedFeedUrls.removeAll { it.equals(cleanUrl, ignoreCase = true) }
        // Immediately persist to disk via commit() before any async work
        localStorage.saveSubscribedUrls(subscribedFeedUrls)

        _subscriptions.update { list ->
            list.filterNot {
                it.id == subscription.id || it.feedUrl.trim().equals(cleanUrl, ignoreCase = true)
            }
        }

        val remainingShowTitles = _subscriptions.value.map { it.title.lowercase() }.toSet()
        _feedEpisodes.update { list ->
            list.filterNot { ep ->
                ep.podcastName.equals(subscription.title, ignoreCase = true) ||
                    ep.podcastName.lowercase() !in remainingShowTitles
            }
        }

        PodcastMediaService.cachedFeedItems = _feedEpisodes.value.map { it.toMediaItem() }
    }

    // --- Offline Download Management ---
    fun downloadEpisode(episode: HomeEpisodeUiModel) {
        val context = getApplication<Application>()
        runCatching {
            PodcastDownloadHelper.enqueueDownload(context, episode)
        }
        _downloadedEpisodes.update { current ->
            if (current.any { it.id == episode.id }) {
                current
            } else {
                listOf(episode) + current
            }
        }
        localStorage.saveDownloads(_downloadedEpisodes.value)
    }

    fun deleteDownload(episode: HomeEpisodeUiModel) {
        val context = getApplication<Application>()
        runCatching {
            PodcastDownloadHelper.deleteDownloadedFile(context, episode.id)
        }
        _downloadedEpisodes.update { current ->
            current.filterNot { it.id == episode.id }
        }
        localStorage.saveDownloads(_downloadedEpisodes.value)
    }

    fun toggleDownloadEpisode(episode: HomeEpisodeUiModel) {
        if (_downloadedEpisodes.value.any { it.id == episode.id }) {
            deleteDownload(episode)
        } else {
            downloadEpisode(episode)
        }
    }

    // --- Explore Search Actions ---
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchDebounceJob?.cancel()
        if (query.isBlank()) {
            _searchUiState.value = SearchUiState.Idle
            return
        }
        searchDebounceJob = viewModelScope.launch {
            delay(350L)
            executeSearch(query)
        }
    }

    fun executeSearch(query: String = _searchQuery.value) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _searchUiState.value = SearchUiState.Idle
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _searchUiState.value = SearchUiState.Loading
            runCatching {
                itunesApi.searchPodcasts(term = trimmed).results
                    .filter { !it.feedUrl.isNullOrBlank() && !it.collectionName.isNullOrBlank() }
            }.fold(
                onSuccess = { items ->
                    _searchUiState.value = SearchUiState.Success(items, trimmed)
                },
                onFailure = { err ->
                    _searchUiState.value = SearchUiState.Error(
                        err.localizedMessage ?: "Unable to reach iTunes Search API."
                    )
                }
            )
        }
    }

    fun subscribeToItunesItem(item: ItunesPodcastItem) {
        item.feedUrl?.let { addRssFeedUrl(it) }
    }

    val subscribedPodcastEntities: List<PodcastEntity>
        get() = _subscriptions.value.map { sub ->
            PodcastEntity(
                feedUrl = sub.feedUrl,
                title = sub.title,
                author = sub.title,
                description = "",
                artworkUrl = sub.artworkUrl
            )
        }

    private fun addToHistory(episode: HomeEpisodeUiModel) {
        _historyEpisodes.update { list ->
            listOf(episode) + list.filterNot { it.id == episode.id }
        }
        localStorage.saveHistory(_historyEpisodes.value)
        viewModelScope.launch {
            PodcastGlanceWidget.updateAllWidgets(getApplication())
        }
    }

    fun clearHistory() {
        _historyEpisodes.value = emptyList()
        localStorage.saveHistory(emptyList())
    }

    private fun loadStarterPodcast() {
        // Loads Android Developers Backstage right away so you have real audio to test
        addRssFeedUrl("https://feeds.feedburner.com/blogspot/AndroidDevelopersBackstage")
    }

    private fun HomeEpisodeUiModel.toMediaItem(
        resolvedUri: String = PodcastDownloadHelper.getOfflinePlaybackUri(getApplication(), this)
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(podcastName)
            .setSubtitle(podcastName)
            .setDescription(description)
            .setArtworkUri(Uri.parse(artworkUrl))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(resolvedUri)
            .setMediaMetadata(metadata)
            .build()
    }

    override fun onCleared() {
        saveCurrentPlaybackPosition()
        sleepTimerJob?.cancel()
        fallbackLoudnessEnhancer?.release()
        fallbackLoudnessEnhancer = null
        mediaController?.release()
        fallbackExoPlayer.release()
        super.onCleared()
    }
}

typealias PodViewModel = PodcastViewModel
