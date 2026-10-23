package com.example.data.rss

import android.util.Xml
import com.example.data.models.EpisodeEntity
import com.example.data.models.ParsedRssFeed
import com.example.data.models.PodcastEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class RssFeedParser(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    suspend fun fetchAndParseFeed(
        feedUrl: String,
        fallbackArtworkUrl: String = "",
        fallbackAuthor: String = ""
    ): Result<ParsedRssFeed> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = feedUrl.trim()
            require(normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://")) {
                "RSS URL must start with http:// or https://"
            }

            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "PulseCast/1.0 (Android; RSS Reader)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}: Failed to fetch RSS feed")
                }
                val bodyStream = response.body?.byteStream()
                    ?: throw IllegalStateException("Empty RSS feed response body")
                parseRssStream(
                    inputStream = bodyStream,
                    feedUrl = normalizedUrl,
                    fallbackArtworkUrl = fallbackArtworkUrl,
                    fallbackAuthor = fallbackAuthor
                )
            }
        }
    }

    fun parseRssStream(
        inputStream: InputStream,
        feedUrl: String,
        fallbackArtworkUrl: String = "",
        fallbackAuthor: String = ""
    ): ParsedRssFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var channelTitle = ""
        var channelAuthor = fallbackAuthor
        var channelDescription = ""
        var channelArtwork = fallbackArtworkUrl
        var channelCategory = "Podcast"

        val episodes = mutableListOf<EpisodeEntity>()

        var insideItem = false
        var insideChannelImage = false

        var itemGuid = ""
        var itemTitle = ""
        var itemDescription = ""
        var itemPubDate = ""
        var itemAudioUrl = ""
        var itemDurationSeconds = 0L
        var itemArtwork = ""
        var itemFileSize = 0L

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val rawName = parser.name ?: ""
                    val tagName = rawName.lowercase(Locale.US)

                    if (!insideItem) {
                        when (tagName) {
                            "item", "entry" -> {
                                insideItem = true
                                itemGuid = ""
                                itemTitle = ""
                                itemDescription = ""
                                itemPubDate = ""
                                itemAudioUrl = ""
                                itemDurationSeconds = 0L
                                itemArtwork = ""
                                itemFileSize = 0L
                            }
                            "image" -> {
                                insideChannelImage = true
                            }
                            "itunes:image" -> {
                                val href = parser.getAttributeValue(null, "href")
                                if (!href.isNullOrBlank()) {
                                    channelArtwork = href.trim()
                                }
                            }
                            "itunes:category" -> {
                                val catText = parser.getAttributeValue(null, "text")
                                if (!catText.isNullOrBlank() && channelCategory == "Podcast") {
                                    channelCategory = catText.trim()
                                }
                            }
                            "title" -> {
                                if (!insideChannelImage && channelTitle.isBlank()) {
                                    channelTitle = safeNextText(parser).trim()
                                }
                            }
                            "description", "itunes:summary", "subtitle" -> {
                                if (channelDescription.isBlank()) {
                                    channelDescription = stripHtml(safeNextText(parser))
                                }
                            }
                            "itunes:author", "author", "managingeditor", "dc:creator" -> {
                                val text = safeNextText(parser).trim()
                                if (text.isNotBlank() && channelAuthor.isBlank()) {
                                    channelAuthor = text
                                }
                            }
                            "url" -> {
                                if (insideChannelImage && channelArtwork.isBlank()) {
                                    channelArtwork = safeNextText(parser).trim()
                                }
                            }
                        }
                    } else {
                        when (tagName) {
                            "guid", "id" -> {
                                itemGuid = safeNextText(parser).trim()
                            }
                            "title" -> {
                                if (itemTitle.isBlank()) {
                                    itemTitle = stripHtml(safeNextText(parser))
                                }
                            }
                            "description", "itunes:summary", "content:encoded", "summary", "itunes:subtitle" -> {
                                val content = stripHtml(safeNextText(parser))
                                if (content.length > itemDescription.length) {
                                    itemDescription = content
                                }
                            }
                            "pubdate", "published", "dc:date" -> {
                                itemPubDate = safeNextText(parser).trim()
                            }
                            "enclosure" -> {
                                val url = parser.getAttributeValue(null, "url")
                                val lengthStr = parser.getAttributeValue(null, "length")
                                if (!url.isNullOrBlank()) {
                                    itemAudioUrl = url.trim()
                                }
                                itemFileSize = lengthStr?.toLongOrNull() ?: 0L
                            }
                            "media:content" -> {
                                if (itemAudioUrl.isBlank()) {
                                    val url = parser.getAttributeValue(null, "url")
                                    val type = parser.getAttributeValue(null, "type") ?: ""
                                    if (!url.isNullOrBlank() && (type.contains("audio") || url.endsWith(".mp3") || url.endsWith(".m4a"))) {
                                        itemAudioUrl = url.trim()
                                    }
                                }
                            }
                            "link" -> {
                                val rel = parser.getAttributeValue(null, "rel")
                                val href = parser.getAttributeValue(null, "href")
                                if (rel == "enclosure" && !href.isNullOrBlank() && itemAudioUrl.isBlank()) {
                                    itemAudioUrl = href.trim()
                                }
                            }
                            "itunes:duration" -> {
                                itemDurationSeconds = parseDurationToSeconds(safeNextText(parser).trim())
                            }
                            "itunes:image", "media:thumbnail" -> {
                                val href = parser.getAttributeValue(null, "href")
                                    ?: parser.getAttributeValue(null, "url")
                                if (!href.isNullOrBlank()) {
                                    itemArtwork = href.trim()
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tagName = (parser.name ?: "").lowercase(Locale.US)
                    if (tagName == "image" && !insideItem) {
                        insideChannelImage = false
                    } else if ((tagName == "item" || tagName == "entry") && insideItem) {
                        insideItem = false
                        if (itemTitle.isNotBlank() && itemAudioUrl.isNotBlank()) {
                            val resolvedPodcastTitle = channelTitle.ifBlank { "Subscribed Podcast" }
                            val resolvedAuthor = channelAuthor.ifBlank { resolvedPodcastTitle }
                            val resolvedArtwork = itemArtwork.ifBlank { channelArtwork }
                            val stableId = (itemGuid.ifBlank { "$feedUrl|$itemAudioUrl|$itemTitle" })
                            val parsedTime = parseRfc822Date(itemPubDate)

                            episodes.add(
                                EpisodeEntity(
                                    id = stableId.ifBlank { UUID.randomUUID().toString() },
                                    podcastFeedUrl = feedUrl,
                                    podcastTitle = resolvedPodcastTitle,
                                    podcastAuthor = resolvedAuthor,
                                    title = itemTitle,
                                    description = itemDescription.ifBlank { "Episode from $resolvedPodcastTitle" },
                                    pubDate = formatFriendlyDate(itemPubDate),
                                    publishedTimestamp = parsedTime,
                                    audioUrl = itemAudioUrl,
                                    durationSeconds = if (itemDurationSeconds > 0L) itemDurationSeconds else 1800L,
                                    artworkUrl = resolvedArtwork,
                                    fileSizeBytes = itemFileSize
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val finalTitle = channelTitle.ifBlank { "Custom Podcast Feed" }
        val finalAuthor = channelAuthor.ifBlank { finalTitle }
        val finalArtwork = channelArtwork.ifBlank {
            episodes.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl ?: ""
        }

        val podcast = PodcastEntity(
            feedUrl = feedUrl,
            title = finalTitle,
            author = finalAuthor,
            description = channelDescription.ifBlank { "Subscribed via RSS ($feedUrl)" },
            artworkUrl = finalArtwork,
            category = channelCategory,
            lastUpdated = System.currentTimeMillis(),
            isSubscribed = true
        )

        val normalizedEpisodes = episodes.take(60).map { ep ->
            ep.copy(
                podcastTitle = finalTitle,
                podcastAuthor = finalAuthor,
                artworkUrl = ep.artworkUrl.ifBlank { finalArtwork }
            )
        }

        return ParsedRssFeed(podcast = podcast, episodes = normalizedEpisodes)
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return try {
            parser.nextText() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private val htmlTagRegex = Regex("<[^>]*>")
        private val whitespaceRegex = Regex("\\s+")

        fun stripHtml(input: String): String {
            if (input.isBlank()) return ""
            return input
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace(htmlTagRegex, " ")
                .replace(whitespaceRegex, " ")
                .trim()
        }

        fun parseDurationToSeconds(raw: String): Long {
            if (raw.isBlank()) return 0L
            val clean = raw.trim()
            clean.toLongOrNull()?.let { return it.coerceAtLeast(0L) }

            val parts = clean.split(":").mapNotNull { it.trim().toLongOrNull() }
            return when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                1 -> parts[0]
                else -> 0L
            }
        }

        private val dateFormats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, d MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd"
        )

        fun parseRfc822Date(raw: String): Long {
            if (raw.isBlank()) return System.currentTimeMillis()
            for (pattern in dateFormats) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    val parsed = sdf.parse(raw.trim())
                    if (parsed != null) return parsed.time
                } catch (_: Exception) {
                    // try next pattern
                }
            }
            return System.currentTimeMillis()
        }

        fun formatFriendlyDate(raw: String): String {
            if (raw.isBlank()) return "Recent"
            val timestamp = parseRfc822Date(raw)
            return try {
                val outFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
                outFormat.format(timestamp)
            } catch (_: Exception) {
                raw.take(16)
            }
        }
    }
}
