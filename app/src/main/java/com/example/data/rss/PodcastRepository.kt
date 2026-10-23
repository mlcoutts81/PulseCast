package com.example.data.rss

import android.content.Context
import android.net.Uri
import com.example.data.models.EpisodeEntity
import com.example.data.models.ItunesPodcastItem
import com.example.data.models.OpmlOutline
import com.example.data.models.PodcastDao
import com.example.data.models.PodcastEntity
import com.example.data.models.PulseCastDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class PodcastRepository private constructor(
    private val appContext: Context,
    private val podcastDao: PodcastDao,
    private val itunesApi: ItunesSearchApi,
    private val rssFeedParser: RssFeedParser,
    private val downloadManager: EpisodeDownloadManager
) {
    val subscribedPodcasts: Flow<List<PodcastEntity>> = podcastDao.getSubscribedPodcasts()
    val allEpisodes: Flow<List<EpisodeEntity>> = podcastDao.getAllEpisodes()
    val queuedEpisodes: Flow<List<EpisodeEntity>> = podcastDao.getQueuedEpisodes()
    val downloadedEpisodes: Flow<List<EpisodeEntity>> = podcastDao.getDownloadedEpisodes()
    val historyEpisodes: Flow<List<EpisodeEntity>> = podcastDao.getHistoryEpisodes()

    fun getEpisodesForPodcast(feedUrl: String): Flow<List<EpisodeEntity>> =
        podcastDao.getEpisodesForPodcast(feedUrl)

    suspend fun searchItunesPodcasts(query: String): Result<List<ItunesPodcastItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = query.trim()
                if (trimmed.isBlank()) return@runCatching emptyList()
                val response = itunesApi.searchPodcasts(term = trimmed)
                response.results.filter { !it.feedUrl.isNullOrBlank() && !it.collectionName.isNullOrBlank() }
            }
        }

    suspend fun subscribeByRssUrl(
        feedUrl: String,
        fallbackArtworkUrl: String = "",
        fallbackAuthor: String = ""
    ): Result<PodcastEntity> = withContext(Dispatchers.IO) {
        val parseResult = rssFeedParser.fetchAndParseFeed(
            feedUrl = feedUrl,
            fallbackArtworkUrl = fallbackArtworkUrl,
            fallbackAuthor = fallbackAuthor
        )
        parseResult.map { parsed ->
            podcastDao.insertPodcast(parsed.podcast.copy(isSubscribed = true))
            podcastDao.upsertEpisodesPreservingUserState(parsed.episodes)

            val currentQueue = podcastDao.getQueuedEpisodesOnce()
            if (currentQueue.isEmpty() && parsed.episodes.isNotEmpty()) {
                val latest = parsed.episodes.first()
                podcastDao.updateQueueState(latest.id, isQueued = true, order = 1)
            }
            parsed.podcast
        }
    }

    suspend fun unsubscribePodcast(feedUrl: String) = withContext(Dispatchers.IO) {
        podcastDao.deletePodcast(feedUrl)
    }

    suspend fun refreshAllSubscribedFeeds(): Int = withContext(Dispatchers.IO) {
        val currentSubs = subscribedPodcasts.first()
        var refreshedCount = 0
        for (sub in currentSubs) {
            val result = rssFeedParser.fetchAndParseFeed(
                feedUrl = sub.feedUrl,
                fallbackArtworkUrl = sub.artworkUrl,
                fallbackAuthor = sub.author
            )
            result.onSuccess { parsed ->
                podcastDao.insertPodcast(
                    parsed.podcast.copy(
                        artworkUrl = parsed.podcast.artworkUrl.ifBlank { sub.artworkUrl },
                        isSubscribed = true
                    )
                )
                podcastDao.upsertEpisodesPreservingUserState(parsed.episodes)
                refreshedCount++
            }
        }
        refreshedCount
    }

    suspend fun importOpmlFromUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val outlines = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                OpmlParser.parse(stream)
            } ?: throw IllegalStateException("Unable to read selected OPML file")

            importOpmlOutlines(outlines)
        }
    }

    suspend fun importOpmlXmlString(xmlContent: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val outlines = OpmlParser.parseXmlString(xmlContent)
            importOpmlOutlines(outlines)
        }
    }

    private suspend fun importOpmlOutlines(outlines: List<OpmlOutline>): Int {
        if (outlines.isEmpty()) {
            throw IllegalArgumentException("No valid <outline xmlUrl=\"...\"> entries found in OPML file")
        }
        var imported = 0
        for (outline in outlines) {
            val feedResult = subscribeByRssUrl(
                feedUrl = outline.xmlUrl,
                fallbackAuthor = outline.title
            )
            if (feedResult.isSuccess) {
                imported++
            } else {
                val existing = podcastDao.getPodcastByUrl(outline.xmlUrl)
                if (existing == null) {
                    podcastDao.insertPodcast(
                        PodcastEntity(
                            feedUrl = outline.xmlUrl,
                            title = outline.title,
                            author = "OPML Imported Show",
                            description = "Imported from OPML (${outline.xmlUrl})",
                            artworkUrl = "",
                            category = "OPML Import",
                            isSubscribed = true
                        )
                    )
                    imported++
                }
            }
        }
        return imported
    }

    suspend fun addToQueue(episode: EpisodeEntity) = withContext(Dispatchers.IO) {
        val existing = podcastDao.getEpisodeById(episode.id)
        if (existing == null) {
            podcastDao.insertEpisode(episode)
        }
        val maxOrder = podcastDao.getMaxQueueOrder()
        podcastDao.updateQueueState(
            episodeId = episode.id,
            isQueued = true,
            order = maxOrder + 1
        )
    }

    suspend fun removeFromQueue(episodeId: String) = withContext(Dispatchers.IO) {
        podcastDao.updateQueueState(episodeId = episodeId, isQueued = false, order = 0)
        normalizeQueueOrders()
    }

    suspend fun moveQueueItem(fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val currentQueue = podcastDao.getQueuedEpisodesOnce().toMutableList()
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) {
            return@withContext
        }
        val moved = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, moved)
        currentQueue.forEachIndexed { index, item ->
            podcastDao.updateQueueState(item.id, isQueued = true, order = index + 1)
        }
    }

    suspend fun clearQueue() = withContext(Dispatchers.IO) {
        val currentQueue = podcastDao.getQueuedEpisodesOnce()
        for (item in currentQueue) {
            podcastDao.updateQueueState(item.id, isQueued = false, order = 0)
        }
    }

    private suspend fun normalizeQueueOrders() {
        val remaining = podcastDao.getQueuedEpisodesOnce()
        remaining.forEachIndexed { index, ep ->
            podcastDao.updateQueueState(ep.id, isQueued = true, order = index + 1)
        }
    }

    suspend fun updatePlaybackProgress(
        episodeId: String,
        positionMs: Long,
        completed: Boolean = false
    ) = withContext(Dispatchers.IO) {
        podcastDao.updatePlaybackProgress(
            episodeId = episodeId,
            positionMs = positionMs,
            playedAt = System.currentTimeMillis(),
            completed = completed
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        podcastDao.clearPlaybackHistory()
    }

    fun downloadEpisodeOffline(episode: EpisodeEntity, onResult: (Boolean, String) -> Unit) {
        downloadManager.startDownload(episode, onResult)
    }

    fun removeOfflineDownload(episode: EpisodeEntity, onRemoved: () -> Unit = {}) {
        downloadManager.cancelOrRemoveDownload(episode, onRemoved)
    }

    /**
     * Populates the initial state with 4 subscriptions and 5 episodes so the classic
     * Google Podcasts Home layout ('For You' feed and 80dp subscription squircles) is immediately testable.
     */
    suspend fun ensureStarterDataSeeded() = withContext(Dispatchers.IO) {
        val existingSubs = subscribedPodcasts.first()
        if (existingSubs.isNotEmpty()) return@withContext

        val now = System.currentTimeMillis()
        val starterPodcasts = listOf(
            PodcastEntity(
                feedUrl = "https://feeds.99percentinvisible.org/99percentinvisible",
                title = "99% Invisible",
                author = "Roman Mars",
                description = "Design is everywhere in our lives, perhaps most importantly in the places where we've just stopped noticing. Weekly exploration of the process and power of design and architecture.",
                artworkUrl = "https://m.media-amazon.com/images/I/51w2+s4c1eL.jpg",
                category = "Design & Architecture",
                lastUpdated = now - 4000L
            ),
            PodcastEntity(
                feedUrl = "https://feeds.simplecast.com/l2i9YnTd",
                title = "Hard Fork",
                author = "The New York Times",
                description = "Each week, journalists Kevin Roose and Casey Newton explore and make sense of the rapidly changing world of technology, silicon, and digital culture.",
                artworkUrl = "https://image.simplecastcdn.com/images/02d8b493-8fb3-43c8-851c-911f6814c89b/09b1a345-413b-4f7c-823f-6e29c2d810c1/3000x3000/hard-fork-album-art.jpg",
                category = "Technology",
                lastUpdated = now - 3000L
            ),
            PodcastEntity(
                feedUrl = "https://feeds.npr.org/510289/podcast.xml",
                title = "Planet Money",
                author = "NPR",
                description = "Wanna see a trick? Give us any topic and we can tie it back to the economy. At Planet Money, we explore the forces that shape our lives.",
                artworkUrl = "https://media.npr.org/assets/img/2022/09/23/planet-money_tile_sq-76760b52f590011c52d2751b64234a088a12b611.jpg",
                category = "Business & Economics",
                lastUpdated = now - 2000L
            ),
            PodcastEntity(
                feedUrl = "https://feeds.simplecast.com/54nAGcIl",
                title = "The Daily",
                author = "The New York Times",
                description = "This is how the news should sound. Twenty minutes a day, five days a week, hosted by Michael Barbaro and Sabrina Tavernise.",
                artworkUrl = "https://image.simplecastcdn.com/images/7f2f4c05-9c2f-422e-b625-18a62a81167c/14408981-6328-401d-b530-9f562b4f2123/3000x3000/the-daily-album-art-2022.jpg?aid=rss_feed",
                category = "Daily News",
                lastUpdated = now - 1000L
            )
        )

        val starterEpisodes = listOf(
            EpisodeEntity(
                id = "ep-1-99pi-acoustic-architecture",
                podcastFeedUrl = "https://feeds.99percentinvisible.org/99percentinvisible",
                podcastTitle = "99% Invisible",
                podcastAuthor = "Roman Mars",
                title = "The Sound of Space: Acoustic Architecture in Modern Cities",
                description = "How subtle architectural materials, whispered galleries, and urban soundscapes shape the way we experience conversation, solitude, and music in public spaces.",
                pubDate = "2 hr ago",
                publishedTimestamp = now - 2L * 3600_000L,
                audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
                durationSeconds = 2700L, // 45 min
                artworkUrl = "https://m.media-amazon.com/images/I/51w2+s4c1eL.jpg",
                isQueued = true,
                queueOrder = 1
            ),
            EpisodeEntity(
                id = "ep-2-hard-fork-silicon-frontier",
                podcastFeedUrl = "https://feeds.simplecast.com/l2i9YnTd",
                podcastTitle = "Hard Fork",
                podcastAuthor = "The New York Times",
                title = "Inside the Next Wave of Custom Mobile Silicon & Neural Engines",
                description = "Kevin and Casey break down why 2-nanometer chip fabrication, ultra-dense memory bandwidth, and open hardware standards are reshaping mobile computing.",
                pubDate = "6 hr ago",
                publishedTimestamp = now - 6L * 3600_000L,
                audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3",
                durationSeconds = 3120L, // 52 min
                artworkUrl = "https://image.simplecastcdn.com/images/02d8b493-8fb3-43c8-851c-911f6814c89b/09b1a345-413b-4f7c-823f-6e29c2d810c1/3000x3000/hard-fork-album-art.jpg",
                isQueued = true,
                queueOrder = 2
            ),
            EpisodeEntity(
                id = "ep-3-planet-money-supply-chain",
                podcastFeedUrl = "https://feeds.npr.org/510289/podcast.xml",
                podcastTitle = "Planet Money",
                podcastAuthor = "NPR",
                title = "The Hidden Supply Chain Inside Your Pocket",
                description = "We trace a single quartz crystal across four continents to understand how global shipping lanes, lithography machines, and rare minerals keep modern life running.",
                pubDate = "Yesterday",
                publishedTimestamp = now - 26L * 3600_000L,
                audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
                durationSeconds = 1680L, // 28 min
                artworkUrl = "https://media.npr.org/assets/img/2022/09/23/planet-money_tile_sq-76760b52f590011c52d2751b64234a088a12b611.jpg",
                isQueued = true,
                queueOrder = 3
            ),
            EpisodeEntity(
                id = "ep-4-the-daily-power-grid",
                podcastFeedUrl = "https://feeds.simplecast.com/54nAGcIl",
                podcastTitle = "The Daily",
                podcastAuthor = "The New York Times",
                title = "Rewiring the Continental Power Grid for the Clean Energy Era",
                description = "Why ultra-high-voltage direct current transmission lines and grid-scale battery storage have become the defining engineering challenge of the decade.",
                pubDate = "2 days ago",
                publishedTimestamp = now - 50L * 3600_000L,
                audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3",
                durationSeconds = 2280L, // 38 min
                artworkUrl = "https://image.simplecastcdn.com/images/7f2f4c05-9c2f-422e-b625-18a62a81167c/14408981-6328-401d-b530-9f562b4f2123/3000x3000/the-daily-album-art-2022.jpg?aid=rss_feed",
                isQueued = false,
                queueOrder = 0
            ),
            EpisodeEntity(
                id = "ep-5-99pi-subway-maps",
                podcastFeedUrl = "https://feeds.99percentinvisible.org/99percentinvisible",
                podcastTitle = "99% Invisible",
                podcastAuthor = "Roman Mars",
                title = " Vignelli's Diagram: Designing Subway Maps for the 21st Century",
                description = "The story behind Massimo Vignelli's iconic 1972 New York City subway diagram and the eternal debate between geographic accuracy and modernist clarity.",
                pubDate = "4 days ago",
                publishedTimestamp = now - 96L * 3600_000L,
                audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
                durationSeconds = 2040L, // 34 min
                artworkUrl = "https://m.media-amazon.com/images/I/51w2+s4c1eL.jpg",
                isQueued = false,
                queueOrder = 0,
                lastPlayedPositionMs = 480_000L,
                lastPlayedTimestamp = now - 4L * 3600_000L
            )
        )

        starterPodcasts.forEach { podcastDao.insertPodcast(it) }
        podcastDao.insertEpisodes(starterEpisodes)
    }

    companion object {
        @Volatile
        private var INSTANCE: PodcastRepository? = null

        fun getInstance(context: Context): PodcastRepository {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                val db = PulseCastDatabase.getInstance(appCtx)
                val dao = db.podcastDao()
                val api = ItunesSearchApi.create()
                val parser = RssFeedParser()
                val downloader = EpisodeDownloadManager(appCtx, dao)
                val instance = PodcastRepository(appCtx, dao, api, parser, downloader)
                INSTANCE = instance
                instance
            }
        }
    }
}
