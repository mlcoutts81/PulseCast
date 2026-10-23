package com.example.data.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 ORDER BY lastUpdated ASC")
    fun getSubscribedPodcasts(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl LIMIT 1")
    suspend fun getPodcastByUrl(feedUrl: String): PodcastEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(podcast: PodcastEntity)

    @Query("UPDATE podcasts SET isSubscribed = :subscribed WHERE feedUrl = :feedUrl")
    suspend fun updateSubscriptionStatus(feedUrl: String, subscribed: Boolean)

    @Query("DELETE FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun deletePodcast(feedUrl: String)

    @Query("SELECT * FROM episodes ORDER BY publishedTimestamp DESC")
    fun getAllEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE podcastFeedUrl = :feedUrl ORDER BY publishedTimestamp DESC")
    fun getEpisodesForPodcast(feedUrl: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE podcastFeedUrl = :feedUrl ORDER BY publishedTimestamp DESC")
    suspend fun getEpisodesForPodcastOnce(feedUrl: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE isQueued = 1 ORDER BY queueOrder ASC")
    fun getQueuedEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE isQueued = 1 ORDER BY queueOrder ASC")
    suspend fun getQueuedEpisodesOnce(): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE downloadStatus = 'DOWNLOADED' OR downloadStatus = 'DOWNLOADING' ORDER BY publishedTimestamp DESC")
    fun getDownloadedEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE lastPlayedTimestamp > 0 OR lastPlayedPositionMs > 0 OR isCompleted = 1 ORDER BY lastPlayedTimestamp DESC")
    fun getHistoryEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE id = :episodeId LIMIT 1")
    suspend fun getEpisodeById(episodeId: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: EpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Update
    suspend fun updateEpisode(episode: EpisodeEntity)

    @Query("SELECT COALESCE(MAX(queueOrder), 0) FROM episodes WHERE isQueued = 1")
    suspend fun getMaxQueueOrder(): Int

    @Query("UPDATE episodes SET isQueued = :isQueued, queueOrder = :order WHERE id = :episodeId")
    suspend fun updateQueueState(episodeId: String, isQueued: Boolean, order: Int)

    @Query(
        "UPDATE episodes SET downloadStatus = :status, downloadProgress = :progress, localFilePath = :localPath, fileSizeBytes = :fileSize WHERE id = :episodeId"
    )
    suspend fun updateDownloadState(
        episodeId: String,
        status: DownloadStatus,
        progress: Int,
        localPath: String?,
        fileSize: Long
    )

    @Query(
        "UPDATE episodes SET lastPlayedPositionMs = :positionMs, lastPlayedTimestamp = :playedAt, isCompleted = :completed WHERE id = :episodeId"
    )
    suspend fun updatePlaybackProgress(
        episodeId: String,
        positionMs: Long,
        playedAt: Long = System.currentTimeMillis(),
        completed: Boolean = false
    )

    @Query("UPDATE episodes SET lastPlayedTimestamp = 0, lastPlayedPositionMs = 0, isCompleted = 0")
    suspend fun clearPlaybackHistory()

    @Transaction
    suspend fun upsertEpisodesPreservingUserState(newEpisodes: List<EpisodeEntity>) {
        for (incoming in newEpisodes) {
            val existing = getEpisodeById(incoming.id)
            if (existing != null) {
                val merged = incoming.copy(
                    isQueued = existing.isQueued,
                    queueOrder = existing.queueOrder,
                    downloadStatus = existing.downloadStatus,
                    downloadProgress = existing.downloadProgress,
                    localFilePath = existing.localFilePath,
                    fileSizeBytes = existing.fileSizeBytes,
                    lastPlayedPositionMs = existing.lastPlayedPositionMs,
                    lastPlayedTimestamp = existing.lastPlayedTimestamp,
                    isCompleted = existing.isCompleted
                )
                insertEpisode(merged)
            } else {
                insertEpisode(incoming)
            }
        }
    }
}
