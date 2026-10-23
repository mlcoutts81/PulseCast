package com.example.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class DownloadStatus {
    NONE,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String,
    val category: String = "Podcast",
    val lastUpdated: Long = System.currentTimeMillis(),
    val isSubscribed: Boolean = true
)

@Entity(
    tableName = "episodes",
    indices = [Index(value = ["podcastFeedUrl"]), Index(value = ["isQueued", "queueOrder"])]
)
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val podcastFeedUrl: String,
    val podcastTitle: String,
    val podcastAuthor: String,
    val title: String,
    val description: String,
    val pubDate: String,
    val publishedTimestamp: Long = System.currentTimeMillis(),
    val audioUrl: String,
    val durationSeconds: Long = 0L,
    val artworkUrl: String,
    val isQueued: Boolean = false,
    val queueOrder: Int = 0,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    val downloadProgress: Int = 0,
    val localFilePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val lastPlayedPositionMs: Long = 0L,
    val lastPlayedTimestamp: Long = 0L,
    val isCompleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ItunesSearchResponse(
    @param:Json(name = "resultCount") val resultCount: Int = 0,
    @param:Json(name = "results") val results: List<ItunesPodcastItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ItunesPodcastItem(
    @param:Json(name = "collectionId") val collectionId: Long? = null,
    @param:Json(name = "collectionName") val collectionName: String? = null,
    @param:Json(name = "artistName") val artistName: String? = null,
    @param:Json(name = "feedUrl") val feedUrl: String? = null,
    @param:Json(name = "artworkUrl600") val artworkUrl600: String? = null,
    @param:Json(name = "artworkUrl100") val artworkUrl100: String? = null,
    @param:Json(name = "primaryGenreName") val primaryGenreName: String? = null,
    @param:Json(name = "trackCount") val trackCount: Int? = null
) {
    val bestArtworkUrl: String
        get() = artworkUrl600?.takeIf { it.isNotBlank() }
            ?: artworkUrl100?.takeIf { it.isNotBlank() }
            ?: ""
}

data class OpmlOutline(
    val title: String,
    val xmlUrl: String,
    val htmlUrl: String? = null
)

data class ParsedRssFeed(
    val podcast: PodcastEntity,
    val episodes: List<EpisodeEntity>
)

data class PlaybackState(
    val currentEpisode: EpisodeEntity? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerRemainingMinutes: Int? = null,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

fun formatDurationMinutesLabel(seconds: Long): String {
    if (seconds <= 0L) return "30 min"
    val totalMinutes = (seconds / 60).coerceAtLeast(1L)
    return "$totalMinutes min"
}

fun formatDurationSeconds(seconds: Long): String {
    if (seconds <= 0L) return "30 min"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours} hr ${minutes} min"
        hours > 0 -> "${hours} hr"
        minutes > 0 -> "${minutes} min"
        else -> "1 min"
    }
}

fun formatRelativeDate(publishedTimestamp: Long, fallbackPubDate: String): String {
    val now = System.currentTimeMillis()
    val diffMs = (now - publishedTimestamp).coerceAtLeast(0L)
    val hours = diffMs / (1000L * 60L * 60L)
    val days = hours / 24L
    return when {
        hours < 1L -> "Just now"
        hours < 24L -> "$hours hr ago"
        days == 1L -> "Yesterday"
        days < 7L -> "$days days ago"
        else -> fallbackPubDate.ifBlank { "Recent" }
    }
}

fun formatMillisToTimestamp(millis: Long): String {
    val safeMillis = millis.coerceAtLeast(0L)
    val totalSeconds = safeMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
