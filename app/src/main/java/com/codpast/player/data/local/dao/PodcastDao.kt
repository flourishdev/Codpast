package com.codpast.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity
import com.codpast.player.data.local.entity.QueueEntity
import com.codpast.player.data.local.entity.QueueWithEpisode
import com.codpast.player.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {

    // --- Podcast Queries ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(podcast: PodcastEntity)

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1")
    fun getSubscribedPodcasts(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1")
    suspend fun getSubscribedPodcastsSnapshot(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE id = :podcastId")
    fun getPodcastById(podcastId: String): Flow<PodcastEntity?>

    @Query("UPDATE podcasts SET isSubscribed = :isSubscribed WHERE id = :podcastId")
    suspend fun updateSubscriptionStatus(podcastId: String, isSubscribed: Boolean)

    @Query("SELECT EXISTS(SELECT 1 FROM podcasts WHERE id = :podcastId)")
    suspend fun isPodcastSaved(podcastId: String): Boolean

    @Query("DELETE FROM podcasts WHERE id = :podcastId")
    suspend fun deletePodcast(podcastId: String)

    // --- Episode Queries ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    fun getEpisodesForPodcast(podcastId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    fun getEpisodeById(episodeId: String): Flow<EpisodeEntity?>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    fun getEpisodesByPodcastId(podcastId: String): Flow<List<EpisodeEntity>>

    @Query("UPDATE episodes SET playbackPosition = :position WHERE id = :episodeId")
    suspend fun updatePlaybackPosition(episodeId: String, position: Long)

    @Query("UPDATE episodes SET isCompleted = :isCompleted, playbackPosition = :position WHERE id = :episodeId")
    suspend fun updateEpisodeCompletion(episodeId: String, isCompleted: Boolean, position: Long = 0L)

    @Query("DELETE FROM episodes WHERE podcastId = :podcastId")
    suspend fun deleteEpisodesByPodcastId(podcastId: String)

    // --- Queue Management ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(queueItem: QueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToQueue(queueItem: QueueEntity)

    @Update
    suspend fun updateQueueItems(items: List<QueueEntity>)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM queue")
    suspend fun getNextPosition(): Int

    @Query("SELECT MAX(position) FROM queue")
    suspend fun getMaxQueuePosition(): Int?

    @Query("SELECT EXISTS(SELECT 1 FROM queue WHERE episodeId = :episodeId)")
    suspend fun isEpisodeInQueue(episodeId: String): Boolean

    @Query("UPDATE queue SET position = position + 1 WHERE position >= :fromPosition")
    suspend fun shiftPositionsUp(fromPosition: Int)

    @Transaction
    suspend fun updateQueueOrder(episodes: List<QueueEntity>) {
        val reindexed = episodes.mapIndexed { index, entity ->
            entity.copy(position = index)
        }
        updateQueueItems(reindexed)
    }

    @Query("DELETE FROM queue WHERE episodeId = :episodeId")
    suspend fun deleteQueueItem(episodeId: String)

    @Query("DELETE FROM queue")
    suspend fun clearQueue()

    @Query(
        "SELECT episodes.* FROM episodes " +
                "INNER JOIN queue ON episodes.id = queue.episodeId " +
                "ORDER BY queue.position ASC"
    )
    fun getQueueEpisodes(): Flow<List<EpisodeEntity>>

    @Transaction
    suspend fun markCompletedAndRemoveFromQueue(episodeId: String) {
        updateEpisodeCompletion(episodeId, isCompleted = true, position = 0L)
        deleteQueueItem(episodeId)
    }

    @Transaction
    suspend fun reEnqueueEpisode(episodeId: String, nextPosition: Int) {
        updateEpisodeCompletion(episodeId, isCompleted = false, position = 0L)
        insertQueueItem(QueueEntity(episodeId, nextPosition))
    }

    @Transaction
    @Query("SELECT * FROM queue ORDER BY position ASC")
    suspend fun getQueueSnapshotWithEpisodes(): List<QueueWithEpisode>

    @Transaction
    @Query("SELECT * FROM queue ORDER BY position ASC")
    suspend fun getQueueSync(): List<QueueWithEpisode>

    @Transaction
    @Query("SELECT * FROM queue ORDER BY position ASC")
    fun getQueue(): Flow<List<QueueWithEpisode>>

    // --- Download Management ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDownload(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE episodeId = :episodeId")
    fun getDownloadForEpisode(episodeId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE episodeId = :episodeId")
    suspend fun getDownloadForEpisodeSnapshot(episodeId: String): DownloadEntity?

    @Query("SELECT * FROM downloads")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("DELETE FROM downloads WHERE episodeId = :episodeId")
    suspend fun deleteDownload(episodeId: String)
}