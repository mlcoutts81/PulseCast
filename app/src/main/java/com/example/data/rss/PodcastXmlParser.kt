package com.example.data.rss

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.ui.screens.HomeEpisodeUiModel
import com.example.ui.screens.PodcastSubscriptionUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class ParsedPodcastFeed(
    val subscription: PodcastSubscriptionUiModel,
    val episodes: List<HomeEpisodeUiModel>
)

object PodcastXmlParser {

    // 1. Fetch and parse a live RSS feed URL
    suspend fun fetchAndParseRss(feedUrl: String): Result<ParsedPodcastFeed> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "MaterialPodcasts/1.0")
            }
            connection.inputStream.use { stream ->
                parseRssStream(stream, feedUrl)
            }
        }
    }

    // 2. Parse an OPML file from the system file picker and return all RSS feed URLs
    suspend fun parseOpmlUri(context: Context, uri: Uri): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val feedUrls = mutableListOf<String>()
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(stream, null)
                }
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name.equals("outline", ignoreCase = true)) {
                        val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                            ?: parser.getAttributeValue(null, "xmlurl")
                        if (!xmlUrl.isNullOrBlank()) {
                            feedUrls.add(xmlUrl.trim())
                        }
                    }
                    eventType = parser.next()
                }
                feedUrls.distinct()
            } ?: emptyList()
        }
    }

    // 3. Writes current subscriptions into a standard OPML 2.0 XML file at the chosen Uri
    suspend fun exportOpmlToUri(
        context: Context,
        uri: Uri,
        subscriptions: List<PodcastSubscriptionUiModel>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                writer.write("<opml version=\"2.0\">\n")
                writer.write("  <head>\n")
                writer.write("    <title>Material Podcasts Subscriptions</title>\n")
                writer.write("  </head>\n")
                writer.write("  <body>\n")
                writer.write("    <outline text=\"feeds\">\n")

                subscriptions.forEach { sub ->
                    val safeTitle = sub.title
                        .replace("&", "&amp;")
                        .replace("\"", "&quot;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                    val safeUrl = sub.feedUrl
                        .replace("&", "&amp;")
                        .replace("\"", "&quot;")

                    writer.write(
                        "      <outline type=\"rss\" text=\"$safeTitle\" title=\"$safeTitle\" xmlUrl=\"$safeUrl\" />\n"
                    )
                }

                writer.write("    </outline>\n")
                writer.write("  </body>\n")
                writer.write("</opml>\n")
                writer.flush()
            } ?: error("Unable to open output stream for OPML export")
        }
    }

    // Internal RSS XML stream parser
    internal fun parseRssStream(inputStream: InputStream, feedUrl: String): ParsedPodcastFeed {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(inputStream, null)
        }

        var podcastTitle = "Unknown Podcast"
        var podcastArtwork = ""
        val episodes = mutableListOf<HomeEpisodeUiModel>()

        var insideItem = false
        var insideChannelImage = false

        var epTitle = ""
        var epDescription = ""
        var epPubDate = ""
        var epDuration = ""
        var epAudioUrl = ""
        var epArtwork = ""
        var epGuid = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when {
                        tagName.equals("item", ignoreCase = true) -> {
                            insideItem = true
                            epTitle = ""
                            epDescription = ""
                            epPubDate = ""
                            epDuration = ""
                            epAudioUrl = ""
                            epArtwork = ""
                            epGuid = ""
                        }
                        !insideItem && tagName.equals("image", ignoreCase = true) -> {
                            insideChannelImage = true
                        }
                        tagName.equals("title", ignoreCase = true) -> {
                            val text = parser.nextText().trim()
                            if (insideItem) epTitle = text
                            else if (!insideChannelImage && podcastTitle == "Unknown Podcast") podcastTitle = text
                        }
                        tagName.equals("description", ignoreCase = true) || tagName.equals("itunes:summary", ignoreCase = true) -> {
                            if (insideItem && epDescription.isBlank()) {
                                epDescription = parser.nextText().replace(Regex("<.*?>"), "").trim()
                            }
                        }
                        tagName.equals("pubDate", ignoreCase = true) -> {
                            if (insideItem) epPubDate = formatPubDate(parser.nextText().trim())
                        }
                        tagName.equals("itunes:duration", ignoreCase = true) -> {
                            if (insideItem) epDuration = parser.nextText().trim()
                        }
                        tagName.equals("guid", ignoreCase = true) -> {
                            if (insideItem) epGuid = parser.nextText().trim()
                        }
                        tagName.equals("enclosure", ignoreCase = true) -> {
                            if (insideItem) {
                                epAudioUrl = parser.getAttributeValue(null, "url") ?: ""
                            }
                        }
                        tagName.equals("itunes:image", ignoreCase = true) -> {
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            if (insideItem) epArtwork = href
                            else if (podcastArtwork.isBlank()) podcastArtwork = href
                        }
                        insideChannelImage && tagName.equals("url", ignoreCase = true) -> {
                            if (podcastArtwork.isBlank()) podcastArtwork = parser.nextText().trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when {
                        tagName.equals("image", ignoreCase = true) -> insideChannelImage = false
                        tagName.equals("item", ignoreCase = true) -> {
                            insideItem = false
                            if (epTitle.isNotBlank() && epAudioUrl.isNotBlank()) {
                                val durationMs = parseDurationToMs(epDuration)
                                episodes.add(
                                    HomeEpisodeUiModel(
                                        id = epGuid.ifBlank { epAudioUrl },
                                        title = epTitle,
                                        podcastName = podcastTitle,
                                        publishedDate = epPubDate,
                                        description = epDescription,
                                        durationText = formatDurationLabel(durationMs),
                                        durationMs = durationMs,
                                        audioUrl = epAudioUrl,
                                        artworkUrl = epArtwork.ifBlank { podcastArtwork }
                                    )
                                )
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val subscription = PodcastSubscriptionUiModel(
            id = UUID.nameUUIDFromBytes(feedUrl.toByteArray()).toString(),
            title = podcastTitle,
            artworkUrl = podcastArtwork,
            feedUrl = feedUrl,
            hasNewEpisodes = episodes.isNotEmpty()
        )

        return ParsedPodcastFeed(subscription = subscription, episodes = episodes)
    }

    private fun formatPubDate(rawDate: String): String {
        // Extracts e.g. "18 Oct 2026" from standard RFC-822 "Sun, 18 Oct 2026 12:00:00 GMT"
        val parts = rawDate.split(" ")
        return if (parts.size >= 4) "${parts[1]} ${parts[2]} ${parts[3]}" else rawDate
    }

    private fun parseDurationToMs(raw: String): Long {
        if (raw.isBlank()) return 1800_000L // Default 30m fallback
        val parts = raw.split(":").mapNotNull { it.toLongOrNull() }
        val seconds = when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 1800L
        }
        return seconds * 1000L
    }

    private fun formatDurationLabel(durationMs: Long): String {
        val totalMinutes = (durationMs / 60000).coerceAtLeast(1)
        return "$totalMinutes min"
    }
}
