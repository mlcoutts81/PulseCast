package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.GooglePodcastsActivityScreen
import com.example.ui.screens.GooglePodcastsHomeScreen
import com.example.ui.theme.GooglePodcastsDynamicTheme
import com.example.viewmodel.PodcastViewModel

@UnstableApi
class MainActivity : ComponentActivity() {

    private val podcastViewModel: PodcastViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeConfig by podcastViewModel.themeConfig.collectAsStateWithLifecycle()
            val subscriptions by podcastViewModel.subscriptions.collectAsStateWithLifecycle()
            val feedEpisodes by podcastViewModel.feedEpisodes.collectAsStateWithLifecycle()
            val queueEpisodes by podcastViewModel.queueEpisodes.collectAsStateWithLifecycle()
            val downloadedEpisodes by podcastViewModel.downloadedEpisodes.collectAsStateWithLifecycle()
            val historyEpisodes by podcastViewModel.historyEpisodes.collectAsStateWithLifecycle()
            val hidePlayedEpisodes by podcastViewModel.hidePlayedEpisodes.collectAsStateWithLifecycle()
            val playerUiState by podcastViewModel.playerUiState.collectAsStateWithLifecycle()
            val searchQuery by podcastViewModel.searchQuery.collectAsStateWithLifecycle()
            val searchUiState by podcastViewModel.searchUiState.collectAsStateWithLifecycle()

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

            GooglePodcastsDynamicTheme(themeConfig = themeConfig) {
                AdaptivePodcastAppShell(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    playerState = playerUiState,
                    onPlayPause = { podcastViewModel.togglePlayPause() },
                    onSeekTo = { podcastViewModel.seekTo(it) },
                    onSkipForward = { podcastViewModel.skipForward() },
                    onSkipBack = { podcastViewModel.skipBack() },
                    onCycleSpeed = { podcastViewModel.cyclePlaybackSpeed() },
                    onCycleSleepTimer = { podcastViewModel.cycleSleepTimer() },
                    onSelectSpeed = { podcastViewModel.setPlaybackSpeed(it) },
                    onToggleSkipSilence = { podcastViewModel.setSkipSilence(it) },
                    onSelectVoiceBoost = { podcastViewModel.setVoiceBoost(it) },
                    onSelectEpisodeFromQueue = { queuedEp ->
                        podcastViewModel.playEpisodeFromQueue(queuedEp)
                    },
                    onRemoveFromQueue = { queuedEp ->
                        podcastViewModel.removeFromQueue(queuedEp)
                    },
                    screenContent = { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = innerPadding.calculateBottomPadding())
                        ) {
                            when (selectedTab) {
                                MainNavTab.HOME -> {
                                    GooglePodcastsHomeScreen(
                                        subscriptions = subscriptions,
                                        feedEpisodes = feedEpisodes,
                                        hidePlayedEpisodes = hidePlayedEpisodes,
                                        onToggleHidePlayed = {
                                            podcastViewModel.toggleHidePlayed()
                                        },
                                        themeConfig = themeConfig,
                                        onThemeConfigChange = { podcastViewModel.updateThemeConfig(it) },
                                        onUnsubscribePodcast = { sub ->
                                            podcastViewModel.unsubscribeFromPodcast(sub)
                                        },
                                        onPlayEpisode = { episode ->
                                            podcastViewModel.playEpisode(episode)
                                        },
                                        onToggleQueue = { episode ->
                                            podcastViewModel.toggleQueue(episode)
                                        },
                                        onTogglePlayed = { episode ->
                                            podcastViewModel.toggleEpisodePlayed(episode)
                                        },
                                        onDownloadEpisode = { episode ->
                                            podcastViewModel.downloadEpisode(episode)
                                        },
                                        onAddRssUrl = { rssUrl ->
                                            podcastViewModel.addRssFeedUrl(rssUrl)
                                        },
                                        onImportOpmlUri = { uri ->
                                            podcastViewModel.importOpmlUri(uri)
                                        },
                                        onExportOpmlUri = { uri ->
                                            podcastViewModel.exportOpmlUri(uri)
                                        },
                                        modifier = Modifier.testTag("home_screen")
                                    )
                                }

                                MainNavTab.EXPLORE -> {
                                    ExploreScreen(
                                        searchQuery = searchQuery,
                                        searchUiState = searchUiState,
                                        subscribedPodcasts = subscribedEntities,
                                        onQueryChange = { podcastViewModel.updateSearchQuery(it) },
                                        onExecuteSearch = { podcastViewModel.executeSearch(it) },
                                        onSubscribeItunesItem = { podcastViewModel.subscribeToItunesItem(it) }
                                    )
                                }

                                MainNavTab.ACTIVITY -> {
                                    GooglePodcastsActivityScreen(
                                        queueEpisodes = queueEpisodes,
                                        downloadedEpisodes = downloadedEpisodes,
                                        historyEpisodes = historyEpisodes,
                                        playerUiState = playerUiState,
                                        onPlayEpisode = { episode -> podcastViewModel.playEpisode(episode) },
                                        onToggleQueue = { episode -> podcastViewModel.toggleQueue(episode) },
                                        onTogglePlayed = { episode -> podcastViewModel.toggleEpisodePlayed(episode) },
                                        onRemoveFromQueue = { episode -> podcastViewModel.removeFromQueue(episode) },
                                        onMoveQueueItemUp = { index -> podcastViewModel.moveQueueItemUp(index) },
                                        onMoveQueueItemDown = { index -> podcastViewModel.moveQueueItemDown(index) },
                                        onDownloadEpisode = { episode -> podcastViewModel.deleteDownload(episode) },
                                        onClearHistory = { podcastViewModel.clearHistory() }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
