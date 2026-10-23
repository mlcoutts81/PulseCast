package com.example.data.local

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.ui.screens.HomeEpisodeUiModel
import java.io.File

object PodcastDownloadHelper {

    // Deterministic filename based on the episode ID so we can always check if it exists on disk
    private fun getFileName(episodeId: String): String {
        val safeHash = episodeId.hashCode().toUInt().toString(16)
        return "podcast_ep_$safeHash.mp3"
    }

    private fun getEpisodeFile(context: Context, episodeId: String): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
        return File(dir, getFileName(episodeId))
    }

    // 1. Enqueue background download via Android system DownloadManager
    fun enqueueDownload(context: Context, episode: HomeEpisodeUiModel): String {
        val targetFile = getEpisodeFile(context, episode.id)

        // If already downloaded on disk, return its local URI immediately
        if (targetFile.exists() && targetFile.length() > 0L) {
            return Uri.fromFile(targetFile).toString()
        }

        val request = DownloadManager.Request(Uri.parse(episode.audioUrl))
            .setTitle(episode.title)
            .setDescription("Downloading • ${episode.podcastName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_PODCASTS,
                getFileName(episode.id)
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        return Uri.fromFile(targetFile).toString()
    }

    // 2. Delete the local MP3 file when removed from the Activity -> Downloads tab
    fun deleteDownloadedFile(context: Context, episodeId: String) {
        val targetFile = getEpisodeFile(context, episodeId)
        if (targetFile.exists()) {
            targetFile.delete()
        }
    }

    // 3. Returns the local file:// URI if the download finished, or falls back to streaming URL
    fun getOfflinePlaybackUri(context: Context, episode: HomeEpisodeUiModel): String {
        val targetFile = getEpisodeFile(context, episode.id)
        return if (targetFile.exists() && targetFile.length() > 0L) {
            Uri.fromFile(targetFile).toString()
        } else {
            episode.audioUrl
        }
    }
}
