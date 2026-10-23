package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.example.data.models.PodcastEntity
import com.example.ui.components.AdaptivePodcastAppShell
import com.example.ui.components.MainNavTab
import com.example.viewmodel.PodcastViewModel

/**
 * Default 4 dummy subscriptions so `GooglePodcastsHomeScreen` is immediately populated and testable.
 */
val DefaultDummySubscriptions = listOf(
    PodcastSubscriptionUiModel(
        id = "sub-99pi",
        title = "99% Invisible",
        artworkUrl = "https://m.media-amazon.com/images/I/51w2+s4c1eL.jpg",
        feedUrl = "https://feeds.99percentinvisible.org/99percentinvisible",
        hasNewEpisodes = true
    ),
    PodcastSubscriptionUiModel(
        id = "sub-hard-fork",
        title = "Hard Fork",
        artworkUrl = "https://image.simplecastcdn.com/images/02d8b493-8fb3-43c8-851c-911f6814c89b/09b1a345-413b-4f7c-823f-6e29c2d810c1/3000x3000/hard-fork-album-art.jpg",
        feedUrl = "https://feeds.simplecast.com/l2i9YnTd",
        hasNewEpisodes = true
    ),
    PodcastSubscriptionUiModel(
        id = "sub-planet-money",
        title = "Planet Money",
        artworkUrl = "https://media.npr.org/assets/img/2022/09/23/planet-money_tile_sq-76760b52f590011c52d2751b64234a088a12b611.jpg",
        feedUrl = "https://feeds.npr.org/510289/podcast.xml",
        hasNewEpisodes = false
    ),
    PodcastSubscriptionUiModel(
        id = "sub-the-daily",
        title = "The Daily",
        artworkUrl = "https://image.simplecastcdn.com/images/7f2f4c05-9c2f-422e-b625-18a62a81167c/14408981-6328-401d-b530-9f562b4f2123/3000x3000/the-daily-album-art-2022.jpg?aid=rss_feed",
        feedUrl = "https://feeds.simplecast.com/54nAGcIl",
        hasNewEpisodes = true
    )
)

/**
 * Default 5 dummy episodes so `GooglePodcastsHomeScreen` is immediately populated and testable.
 */
val DefaultDummyEpisodes = listOf(
    HomeEpisodeUiModel(
        id = "ep-1-99pi-acoustic-architecture",
        title = "The Sound of Space: Acoustic Architecture in Modern Cities",
        podcastName = "99% Invisible",
        publishedDate = "2 hr ago",
        description = "How subtle architectural materials, whispered galleries, and urban soundscapes shape the way we experience conversation, solitude, and music in public spaces.",
        durationText = "45 min",
        durationMs = 2_700_000L,
        audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
        artworkUrl = "https://m.media-amazon.com/images/I/51w2+s4c1eL.jpg",
        isQueued = true
    ),
    HomeEpisodeUiModel(
        id = "ep-2-hard-fork-silicon-frontier",
        title = "Inside the Next Wave of Custom Mobile Silicon & Neural Engines",
        podcastName = "Hard Fork",
        publishedDate = "6 hr ago",
        description = "Kevin and Casey break down why 2-nanometer chip fabrication, ultra-dense memory bandwidth, and open hardware standards are reshaping mobile computing.",
        durationText = "52 min",
        durationMs = 3_120_000L,
        audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3",
        artworkUrl = "https://image.simplecastcdn.com/images/02d8b493-8fb3-43c8-851c-911f6814c89b/09b1a345-413b-4f7c-823f-6e29c2d810c1/3000x3000/hard-fork-album-art.jpg",
        isQueued = true
    ),
    HomeEpisodeUiModel(
        id = "ep-3-planet-money-supply-chain",
        title = "The Hidden Supply Chain Inside Your Pocket",
        podcastName = "Planet Money",
        publishedDate = "Yesterday",
        description = "We trace a single quartz crystal across four continents to understand how global shipping lanes, lithography machines, and rare minerals keep modern life running.",
        durationText = "28 min",
        durationMs = 1_680_000L,
        audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
        artworkUrl = "https://media.npr.org/assets/img/2022/09/23/planet-money_tile_sq-76760b52f590011c52d2751b64234a088a12b611.jpg",
        isQueued = true
    ),
    HomeEpisodeUiModel(
        id = "ep-4-the-daily-power-grid",
        title = "Rewiring the Continental Power Grid for the Clean Energy Era",
        podcastName = "The Daily",
        publishedDate = "2 days ago",
        description = "Why ultra-high-voltage direct current transmission lines and grid-scale battery storage have become the defining engineering challenge of the decade.",
        durationText = "38 min",
        durationMs = 2_280_000L,
        audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3",
        artworkUrl = "https://image.simplecastcdn.com/images/7f2f4c05-9c2f-422e-b625-18a62a81167c/14408981-6328-401d-b530-9f562b4f2123/3000x3000/the-daily-album-art-2022.jpg?aid=rss_feed",
        isQueued = false
    ),
    HomeEpisodeUiModel(
        id = "ep-5-99pi-subway-maps",
        title = "Vignelli's Diagram: Designing Subway Maps for the 21st Century",
        podcastName = "99% Invisible",
        publishedDate = "4 days ago",
        description = "The story behind Massimo Vignelli's iconic 1972 New York City subway diagram and the eternal debate between geographic accuracy and modernist clarity.",
        durationText = "34 min",
        durationMs = 2_040_000L,
        audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
        artworkUrl = "https://m.media-amazon.com/images/I/51w2+s4c1eL.jpg",
        isQueued = false
    )
)

@UnstableApi
@Composable
fun PulseCastAppScreen(viewModel: PodcastViewModel) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val feedEpisodes by viewModel.feedEpisodes.collectAsStateWithLifecycle()
    val queueEpisodes by viewModel.queueEpisodes.collectAsStateWithLifecycle()
    val downloadedEpisodes by viewModel.downloadedEpisodes.collectAsStateWithLifecycle()
    val historyEpisodes by viewModel.historyEpisodes.collectAsStateWithLifecycle()
    val playerUiState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchUiState by viewModel.searchUiState.collectAsStateWithLifecycle()
    val themeConfig by viewModel.themeConfig.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(MainNavTab.HOME) }

    val subscribedEntities = remember(subscriptions) {
        subscriptions.map { sub ->
            PodcastEntity(
                feedUrl = sub.feedUrl,
                title = sub.title,
                author = sub.title,
                description = "",
                artworkUrl = sub.artworkUrl
            )
        }
    }

    BackHandler(enabled = selectedTab != MainNavTab.HOME) {
        selectedTab = MainNavTab.HOME
    }

    AdaptivePodcastAppShell(
        selectedTab = selectedTab,
        onSelectTab = { selectedTab = it },
        playerState = playerUiState,
        onPlayPause = { viewModel.togglePlayPause() },
        onSeekTo = { viewModel.seekTo(it) },
        onSkipForward = { viewModel.skipForward() },
        onSkipBack = { viewModel.skipBack() },
        onCycleSpeed = { viewModel.cyclePlaybackSpeed() },
        onCycleSleepTimer = { viewModel.cycleSleepTimer() },
        onSelectEpisodeFromQueue = { queuedEp -> viewModel.playEpisodeFromQueue(queuedEp) },
        onRemoveFromQueue = { queuedEp -> viewModel.removeFromQueue(queuedEp) },
        screenContent = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    MainNavTab.HOME -> {
                        GooglePodcastsHomeScreen(
                            subscriptions = subscriptions,
                            feedEpisodes = feedEpisodes,
                            themeConfig = themeConfig,
                            onThemeConfigChange = { viewModel.updateThemeConfig(it) },
                            onUnsubscribePodcast = { sub -> viewModel.unsubscribeFromPodcast(sub) },
                            onPlayEpisode = { episode -> viewModel.playEpisode(episode) },
                            onToggleQueue = { episode -> viewModel.toggleQueue(episode) },
                            onDownloadEpisode = { episode -> viewModel.downloadEpisode(episode) },
                            onAddRssUrl = { rssUrl -> viewModel.addRssFeedUrl(rssUrl) },
                            onImportOpmlUri = { uri -> viewModel.importOpmlUri(uri) },
                            modifier = Modifier.testTag("home_screen")
                        )
                    }

                    MainNavTab.EXPLORE -> {
                        ExploreScreen(
                            searchQuery = searchQuery,
                            searchUiState = searchUiState,
                            subscribedPodcasts = subscribedEntities,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                            onExecuteSearch = { viewModel.executeSearch(it) },
                            onSubscribeItunesItem = { viewModel.subscribeToItunesItem(it) }
                        )
                    }

                    MainNavTab.ACTIVITY -> {
                        GooglePodcastsActivityScreen(
                            queueEpisodes = queueEpisodes,
                            downloadedEpisodes = downloadedEpisodes,
                            historyEpisodes = historyEpisodes,
                            playerUiState = playerUiState,
                            onPlayEpisode = { episode -> viewModel.playEpisode(episode) },
                            onToggleQueue = { episode -> viewModel.toggleQueue(episode) },
                            onRemoveFromQueue = { episode -> viewModel.removeFromQueue(episode) },
                            onMoveQueueItemUp = { index -> viewModel.moveQueueItemUp(index) },
                            onMoveQueueItemDown = { index -> viewModel.moveQueueItemDown(index) },
                            onDownloadEpisode = { episode -> viewModel.deleteDownload(episode) },
                            onClearHistory = { viewModel.clearHistory() }
                        )
                    }
                }
            }
        }
    )
}
