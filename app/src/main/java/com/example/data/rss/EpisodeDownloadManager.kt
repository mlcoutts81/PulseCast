package com.example.data.rss

import android.content.Context
import com.example.data.models.DownloadStatus
import com.example.data.models.EpisodeEntity
import com.example.data.models.PodcastDao
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class EpisodeDownloadManager(
    private val context: Context,
    private val podcastDao: PodcastDao,
    private val okHttpClient: OkHttpClient = ItunesSearchApi.okHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloads = ConcurrentHashMap<String, Job>()

    private val offlineDir: File by lazy {
        File(context.filesDir, "offline_episodes").apply {
            if (!exists()) mkdirs()
        }
    }

    fun startDownload(
        episode: EpisodeEntity,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (activeDownloads.containsKey(episode.id)) return

        val job = scope.launch {
            try {
                podcastDao.updateDownloadState(
                    episodeId = episode.id,
                    status = DownloadStatus.DOWNLOADING,
                    progress = 5,
                    localPath = null,
                    fileSize = episode.fileSizeBytes
                )

                val safeFileName = episode.id.replace(Regex("[^a-zA-Z0-9._-]"), "_").takeLast(64) + ".mp3"
                val targetFile = File(offlineDir, safeFileName)
                val tempFile = File(offlineDir, "$safeFileName.part")

                val request = Request.Builder()
                    .url(episode.audioUrl)
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download failed with HTTP ${response.code}")
                    }

                    val body = response.body ?: throw IllegalStateException("Empty audio stream")
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()

                    FileOutputStream(tempFile).use { outputStream ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastReportedProgress = 5

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            val progress = if (contentLength > 0L) {
                                ((totalRead * 100L) / contentLength).toInt().coerceIn(5, 99)
                            } else {
                                ((totalRead * 100L) / (15L * 1024L * 1024L)).toInt().coerceIn(5, 95)
                            }

                            if (progress - lastReportedProgress >= 8) {
                                lastReportedProgress = progress
                                podcastDao.updateDownloadState(
                                    episodeId = episode.id,
                                    status = DownloadStatus.DOWNLOADING,
                                    progress = progress,
                                    localPath = null,
                                    fileSize = totalRead
                                )
                            }
                        }
                        outputStream.flush()

                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)

                        podcastDao.updateDownloadState(
                            episodeId = episode.id,
                            status = DownloadStatus.DOWNLOADED,
                            progress = 100,
                            localPath = targetFile.absolutePath,
                            fileSize = targetFile.length()
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    onComplete(true, "Downloaded \"${episode.title}\" for offline listening")
                }
            } catch (_: CancellationException) {
                podcastDao.updateDownloadState(
                    episodeId = episode.id,
                    status = DownloadStatus.NONE,
                    progress = 0,
                    localPath = null,
                    fileSize = 0L
                )
            } catch (e: Exception) {
                podcastDao.updateDownloadState(
                    episodeId = episode.id,
                    status = DownloadStatus.FAILED,
                    progress = 0,
                    localPath = null,
                    fileSize = 0L
                )
                withContext(Dispatchers.Main) {
                    onComplete(false, "Could not download \"${episode.title}\": ${e.localizedMessage ?: "Network error"}")
                }
            } finally {
                activeDownloads.remove(episode.id)
            }
        }
        activeDownloads[episode.id] = job
    }

    fun cancelOrRemoveDownload(episode: EpisodeEntity, onRemoved: () -> Unit = {}) {
        activeDownloads.remove(episode.id)?.cancel()
        scope.launch {
            episode.localFilePath?.let { path ->
                runCatching { File(path).delete() }
            }
            val safeFileName = episode.id.replace(Regex("[^a-zA-Z0-9._-]"), "_").takeLast(64) + ".mp3"
            runCatching { File(offlineDir, safeFileName).delete() }
            runCatching { File(offlineDir, "$safeFileName.part").delete() }

            podcastDao.updateDownloadState(
                episodeId = episode.id,
                status = DownloadStatus.NONE,
                progress = 0,
                localPath = null,
                fileSize = 0L
            )
            withContext(Dispatchers.Main) {
                onRemoved()
            }
        }
    }
}
