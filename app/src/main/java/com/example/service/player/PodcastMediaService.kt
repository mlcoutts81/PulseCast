package com.example.service.player

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.data.local.PodcastDownloadHelper
import com.example.data.local.PodcastLocalStorage
import com.example.data.models.DownloadStatus
import com.example.data.models.EpisodeEntity
import com.example.data.models.PodcastEntity
import com.example.ui.screens.DefaultDummyEpisodes
import com.example.ui.screens.HomeEpisodeUiModel
import com.example.ui.widget.PodcastGlanceWidget
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * AndroidX Media3 [MediaLibraryService] providing background audio playback,
 * Android Auto car launcher integration, and full-screen car media player support.
 */
@UnstableApi
class PodcastMediaService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentBoostDb: Int = 0
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(15_000L)
            .setSeekForwardIncrementMs(30_000L)
            .build()

        player = exoPlayer

        val localStorage = PodcastLocalStorage(this)
        currentBoostDb = localStorage.loadVoiceBoostDb()
        exoPlayer.skipSilenceEnabled = localStorage.loadSkipSilence()

        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupLoudnessEnhancer(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isCurrentlyPlaying = isPlaying
                if (!isPlaying) {
                    val item = exoPlayer.currentMediaItem
                    if (item != null) {
                        val posMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val durMs = exoPlayer.duration.takeIf { it > 0L } ?: 0L
                        localStorage.saveEpisodePosition(item.mediaId, posMs, durMs)
                    }
                }
                serviceScope.launch {
                    PodcastGlanceWidget.updateAllWidgets(this@PodcastMediaService)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                isCurrentlyPlaying = exoPlayer.isPlaying
                serviceScope.launch {
                    PodcastGlanceWidget.updateAllWidgets(this@PodcastMediaService)
                }
            }
        })
        if (exoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            setupLoudnessEnhancer(exoPlayer.audioSessionId)
        }

        val lastPlayedEp = localStorage.loadHistory().firstOrNull()
        val savedQueueEps = localStorage.loadQueue()

        // Seed cachedFeedItems from local storage or starter episodes if empty when launched from Android Auto
        if (cachedFeedItems.isEmpty()) {
            val initialEpisodes = when {
                savedQueueEps.isNotEmpty() -> savedQueueEps
                lastPlayedEp != null -> listOf(lastPlayedEp)
                else -> DefaultDummyEpisodes
            }
            cachedFeedItems = initialEpisodes.map { it.toServiceMediaItem() }
        }

        val startupPlaylist = buildList {
            if (lastPlayedEp != null) add(lastPlayedEp.toServiceMediaItem())
            savedQueueEps
                .filterNot { it.id == lastPlayedEp?.id }
                .forEach { add(it.toServiceMediaItem()) }
        }

        if (startupPlaylist.isNotEmpty()) {
            val startPosMs = lastPlayedEp?.let {
                localStorage.getEpisodePosition(it.id).takeIf { pos -> pos > 0L }
                    ?: it.resumePositionMs.coerceAtLeast(0L)
            } ?: 0L
            exoPlayer.setMediaItems(startupPlaylist, 0, startPosMs)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = false
        }

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            LibrarySessionCallback()
        ).build()
    }

    private fun setupLoudnessEnhancer(audioSessionId: Int) {
        runCatching {
            loudnessEnhancer?.release()
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    // targetGain is in millibels (mB): 1 dB = 100 mB
                    setTargetGain(currentBoostDb * 100)
                    enabled = currentBoostDb > 0
                }
            }
        }
    }

    private fun applyVoiceBoostDb(boostDb: Int) {
        currentBoostDb = boostDb
        runCatching {
            val enhancer = loudnessEnhancer
            val exoPlayer = player
            if (enhancer == null && exoPlayer?.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                exoPlayer?.audioSessionId?.let { setupLoudnessEnhancer(it) }
            } else if (enhancer != null) {
                enhancer.setTargetGain(boostDb * 100) // e.g., 6dB -> 600mB, 12dB -> 1200mB
                enhancer.enabled = boostDb > 0
            }
        }
    }

    internal fun createLibrarySessionCallback(): MediaLibrarySession.Callback =
        LibrarySessionCallback()

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        // A. Explicitly grant ALL Session & Player commands to Android Auto ("com.google.android.projection.gearhead")
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(CUSTOM_ACTION_REWIND_15, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_ACTION_FORWARD_30, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_ACTION_SET_SKIP_SILENCE, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_ACTION_SET_VOICE_BOOST, Bundle.EMPTY))
                .build()

            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                .buildUpon()
                .add(Player.COMMAND_SEEK_BACK)
                .add(Player.COMMAND_SEEK_FORWARD)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val exoPlayer = player
            when (customCommand.customAction) {
                CUSTOM_ACTION_REWIND_15 -> session.player.seekBack()
                CUSTOM_ACTION_FORWARD_30 -> session.player.seekForward()
                CUSTOM_ACTION_SET_SKIP_SILENCE -> {
                    val enabled = args.getBoolean(EXTRA_SKIP_SILENCE, false)
                    exoPlayer?.skipSilenceEnabled = enabled
                }
                CUSTOM_ACTION_SET_VOICE_BOOST -> {
                    val boostDb = args.getInt(EXTRA_BOOST_DB, 0)
                    applyVoiceBoostDb(boostDb)
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootExtras = Bundle().apply {
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                )
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                )
                putBoolean(MediaConstants.EXTRAS_KEY_MEDIA_ART_SIZE_PIXELS, true)
            }
            val rootMetadata = MediaMetadata.Builder()
                .setTitle("PulseCast")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()

            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(rootMetadata)
                .build()

            val rootLibraryParams = LibraryParams.Builder()
                .setExtras(rootExtras)
                .build()

            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, rootLibraryParams))
        }

        // B. CRITICAL: Android Auto calls onGetItem() when expanding the Now Playing widget into the Full-Screen Car UI!
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val allItems = getAllPlayableMediaItems()
            val matched = allItems.find { it.mediaId == mediaId }
                ?: session.player.currentMediaItem?.takeIf { it.mediaId == mediaId }

            return if (matched != null) {
                Futures.immediateFuture(LibraryResult.ofItem(matched, null))
            } else {
                Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }

        // C. Build the top 3 tabs on the Android Auto screen: Up Next, For You, and Shows (Grid)
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val localStorage = PodcastLocalStorage(this@PodcastMediaService)

            return when {
                parentId == ROOT_ID -> {
                    val tabs = listOf(
                        buildBrowsableTab(
                            id = TAB_QUEUE_ID,
                            title = "Up Next",
                            style = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                        ),
                        buildBrowsableTab(
                            id = TAB_FOR_YOU_ID,
                            title = "For You",
                            style = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                        ),
                        buildBrowsableTab(
                            id = TAB_SHOWS_ID,
                            title = "Shows",
                            style = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                        )
                    )
                    Futures.immediateFuture(LibraryResult.ofItemList(tabs, params))
                }

                parentId == TAB_QUEUE_ID -> {
                    val queueItems = localStorage.loadQueue().map { it.toServiceMediaItem() }
                    Futures.immediateFuture(LibraryResult.ofItemList(queueItems, params))
                }

                parentId == TAB_FOR_YOU_ID -> {
                    val feedItems = cachedFeedItems.ifEmpty {
                        localStorage.loadHistory().map { it.toServiceMediaItem() }
                    }
                    Futures.immediateFuture(LibraryResult.ofItemList(feedItems, params))
                }

                parentId == TAB_SHOWS_ID -> {
                    // Group known episodes by podcastName to render a cover-art grid in the car
                    val allEpisodes = cachedFeedItems.ifEmpty {
                        (localStorage.loadQueue() + localStorage.loadHistory()).map { it.toServiceMediaItem() }
                    }
                    val shows = allEpisodes
                        .groupBy { it.mediaMetadata.artist?.toString() ?: "Podcast" }
                        .map { (podcastName, items) ->
                            val artwork = items.firstOrNull()?.mediaMetadata?.artworkUri
                            MediaItem.Builder()
                                .setMediaId("$SHOW_PREFIX$podcastName")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(podcastName)
                                        .setSubtitle("${items.size} episodes")
                                        .setArtworkUri(artwork)
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .build()
                                )
                                .build()
                        }
                    Futures.immediateFuture(LibraryResult.ofItemList(shows, params))
                }

                parentId.startsWith(SHOW_PREFIX) -> {
                    val showName = parentId.removePrefix(SHOW_PREFIX)
                    val showEpisodes = getAllPlayableMediaItems().filter {
                        it.mediaMetadata.artist?.toString().equals(showName, ignoreCase = true)
                    }
                    Futures.immediateFuture(LibraryResult.ofItemList(showEpisodes, params))
                }

                else -> Futures.immediateFuture(LibraryResult.ofItemList(emptyList(), params))
            }
        }

        // D. Support Android Auto subscribing to tab updates
        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            session.notifyChildrenChanged(browser, parentId, 20, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val results = searchPlayableItems(query)
            session.notifySearchResultChanged(browser, query, results.size, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val results = searchPlayableItems(query)
            return Futures.immediateFuture(LibraryResult.ofItemList(results, params))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val allItems = getAllPlayableMediaItems()
            val resolved = mediaItems.map { requested ->
                if (requested.localConfiguration?.uri != null) {
                    requested
                } else {
                    allItems.find { it.mediaId == requested.mediaId } ?: requested
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    private fun searchPlayableItems(query: String): List<MediaItem> {
        val allItems = getAllPlayableMediaItems()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return allItems
        return allItems.filter { item ->
            val title = item.mediaMetadata.title?.toString().orEmpty()
            val artist = item.mediaMetadata.artist?.toString().orEmpty()
            val description = item.mediaMetadata.description?.toString().orEmpty()
            title.contains(trimmed, ignoreCase = true) ||
                artist.contains(trimmed, ignoreCase = true) ||
                description.contains(trimmed, ignoreCase = true)
        }.ifEmpty { allItems }
    }

    private fun getAllPlayableMediaItems(): List<MediaItem> {
        val localStorage = PodcastLocalStorage(this@PodcastMediaService)
        val saved = (localStorage.loadQueue() + localStorage.loadHistory()).map { it.toServiceMediaItem() }
        return (cachedFeedItems + saved).distinctBy { it.mediaId }
    }

    private fun buildBrowsableTab(id: String, title: String, style: Int): MediaItem {
        val extras = Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, style)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, style)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun HomeEpisodeUiModel.toServiceMediaItem(): MediaItem =
        homeEpisodeToMediaItem(this)

    private fun homeEpisodeToMediaItem(episode: HomeEpisodeUiModel): MediaItem {
        val resolvedUri = PodcastDownloadHelper.getOfflinePlaybackUri(applicationContext, episode)
        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.podcastName)
            .setSubtitle(episode.podcastName)
            .setDescription(episode.description)
            .setArtworkUri(Uri.parse(episode.artworkUrl))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .build()

        return MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(resolvedUri)
            .setMediaMetadata(metadata)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val exoPlayer = player ?: return START_STICKY
        val localStorage = PodcastLocalStorage(this)

        when (intent?.action) {
            ACTION_WIDGET_TOGGLE_PLAY -> {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                } else {
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
            ACTION_WIDGET_REWIND_15 -> {
                exoPlayer.seekBack()
                exoPlayer.currentMediaItem?.let { item ->
                    localStorage.saveEpisodePosition(
                        item.mediaId,
                        exoPlayer.currentPosition.coerceAtLeast(0L),
                        exoPlayer.duration.takeIf { it > 0L } ?: 0L
                    )
                }
            }
            ACTION_WIDGET_FORWARD_30 -> {
                exoPlayer.seekForward()
                exoPlayer.currentMediaItem?.let { item ->
                    localStorage.saveEpisodePosition(
                        item.mediaId,
                        exoPlayer.currentPosition.coerceAtLeast(0L),
                        exoPlayer.duration.takeIf { it > 0L } ?: 0L
                    )
                }
            }
            ACTION_WIDGET_PLAY_EPISODE_ID -> {
                val episodeId = intent.getStringExtra(EXTRA_WIDGET_EPISODE_ID)
                if (!episodeId.isNullOrBlank()) {
                    val queue = localStorage.loadQueue()
                    val history = localStorage.loadHistory()
                    val targetEp = queue.find { it.id == episodeId }
                        ?: history.find { it.id == episodeId }

                    if (targetEp != null) {
                        val savedPos = localStorage.getEpisodePosition(targetEp.id).coerceAtLeast(0L)
                        val mediaItem = targetEp.toServiceMediaItem()

                        val existingIdx = (0 until exoPlayer.mediaItemCount).indexOfFirst {
                            exoPlayer.getMediaItemAt(it).mediaId == targetEp.id
                        }
                        val targetIdx = if (existingIdx != -1) {
                            existingIdx
                        } else {
                            exoPlayer.addMediaItem(0, mediaItem)
                            0
                        }

                        // Move played item to top of History so widget header updates immediately
                        val updatedHistory = listOf(targetEp) + history.filterNot { it.id == targetEp.id }
                        localStorage.saveHistory(updatedHistory)

                        exoPlayer.seekTo(targetIdx, savedPos)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                }
            }
        }
        isCurrentlyPlaying = exoPlayer.isPlaying
        serviceScope.launch {
            PodcastGlanceWidget.updateAllWidgets(this@PodcastMediaService)
        }
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        isCurrentlyPlaying = false
        serviceScope.cancel()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        player = null
        super.onDestroy()
    }

    companion object {
        private const val ROOT_ID = "root_podcast_id"
        private const val TAB_QUEUE_ID = "tab_queue_id"
        private const val TAB_FOR_YOU_ID = "tab_for_you_id"
        private const val TAB_SHOWS_ID = "tab_shows_id"
        private const val SHOW_PREFIX = "show_feed_"

        const val SUBSCRIPTIONS_ID = TAB_SHOWS_ID
        const val QUEUE_ID = TAB_QUEUE_ID
        const val PODCAST_PREFIX = SHOW_PREFIX

        const val CUSTOM_ACTION_REWIND_15 = "ACTION_REWIND_15"
        const val CUSTOM_ACTION_FORWARD_30 = "ACTION_FORWARD_30"
        const val CUSTOM_ACTION_SET_SKIP_SILENCE = "ACTION_SET_SKIP_SILENCE"
        const val CUSTOM_ACTION_SET_VOICE_BOOST = "ACTION_SET_VOICE_BOOST"
        const val EXTRA_SKIP_SILENCE = "EXTRA_SKIP_SILENCE"
        const val EXTRA_BOOST_DB = "EXTRA_BOOST_DB"

        const val ACTION_WIDGET_TOGGLE_PLAY = "ACTION_WIDGET_TOGGLE_PLAY"
        const val ACTION_WIDGET_REWIND_15 = "ACTION_WIDGET_REWIND_15"
        const val ACTION_WIDGET_FORWARD_30 = "ACTION_WIDGET_FORWARD_30"
        const val ACTION_WIDGET_PLAY_EPISODE_ID = "ACTION_WIDGET_PLAY_EPISODE_ID"
        const val EXTRA_WIDGET_EPISODE_ID = "EXTRA_WIDGET_EPISODE_ID"

        @Volatile
        var isCurrentlyPlaying: Boolean = false

        @Volatile
        var cachedFeedItems: List<MediaItem> = emptyList()

        @Volatile
        var cachedShowItems: List<MediaItem> = emptyList()

        fun buildRootBrowsableTabs(): List<MediaItem> {
            val subscriptionsTab = MediaItem.Builder()
                .setMediaId(SUBSCRIPTIONS_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Subscriptions")
                        .setSubtitle("Your subscribed podcast shows")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                        .build()
                )
                .build()

            val upNextQueueTab = MediaItem.Builder()
                .setMediaId(QUEUE_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Up Next Queue")
                        .setSubtitle("Episodes queued for continuous listening")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                        .build()
                )
                .build()

            return listOf(subscriptionsTab, upNextQueueTab)
        }

        fun podcastToBrowsableMediaItem(podcast: PodcastEntity): MediaItem {
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(podcast.title)
                .setArtist(podcast.author)
                .setDescription(podcast.description)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)

            if (podcast.artworkUrl.isNotBlank()) {
                metadataBuilder.setArtworkUri(Uri.parse(podcast.artworkUrl))
            }

            return MediaItem.Builder()
                .setMediaId("$PODCAST_PREFIX${podcast.feedUrl}")
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }

        fun episodeToPlayableMediaItem(episode: EpisodeEntity): MediaItem {
            val localFile = episode.localFilePath?.let { File(it) }
            val resolvedUri = if (
                episode.downloadStatus == DownloadStatus.DOWNLOADED &&
                localFile != null &&
                localFile.exists()
            ) {
                Uri.fromFile(localFile)
            } else {
                Uri.parse(episode.audioUrl)
            }

            val extras = Bundle().apply {
                putString("podcastFeedUrl", episode.podcastFeedUrl)
                putString("podcastTitle", episode.podcastTitle)
                putString("pubDate", episode.pubDate)
                putLong("durationSeconds", episode.durationSeconds)
            }

            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist(episode.podcastTitle)
                .setAlbumTitle(episode.podcastTitle)
                .setSubtitle(episode.podcastTitle)
                .setDescription(episode.description)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .setExtras(extras)

            if (episode.artworkUrl.isNotBlank()) {
                metadataBuilder.setArtworkUri(Uri.parse(episode.artworkUrl))
            }

            return MediaItem.Builder()
                .setMediaId(episode.id)
                .setUri(resolvedUri)
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }
    }
}

typealias PodcastMediaLibraryService = PodcastMediaService
