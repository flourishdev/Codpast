// File: com/codpast/player/data/local/dao/PodcastDao.kt
package com.codpast.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity
import com.codpast.player.data.local.entity.QueueEntity
import com.codpast.player.data.local.entity.QueueWithEpisode
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(podcast: PodcastEntity)

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1")
    fun getSubscribedPodcasts(): Flow<List<PodcastEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    fun getEpisodesForPodcast(podcastId: String): Flow<List<EpisodeEntity>>

    @Query("UPDATE episodes SET playbackPosition = :position WHERE id = :episodeId")
    suspend fun updatePlaybackPosition(episodeId: String, position: Long)

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1")
    suspend fun getSubscribedPodcastsSnapshot(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE id = :podcastId")
    fun getPodcastById(podcastId: String): Flow<PodcastEntity?>

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    fun getEpisodeById(episodeId: String): Flow<EpisodeEntity?>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    fun getEpisodesByPodcastId(podcastId: String): Flow<List<EpisodeEntity>>

    @Query("DELETE FROM podcasts WHERE id = :podcastId")
    suspend fun deletePodcast(podcastId: String)

    @Query("DELETE FROM episodes WHERE podcastId = :podcastId")
    suspend fun deleteEpisodesByPodcastId(podcastId: String)

// --- Queue Management ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(queueItem: QueueEntity)

    @androidx.room.Update
    suspend fun updateQueueItems(items: List<QueueEntity>)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM queue")
    suspend fun getNextPosition(): Int

    @Query("UPDATE queue SET position = position + 1 WHERE position >= :fromPosition")
    suspend fun shiftPositionsUp(fromPosition: Int)

    @androidx.room.Transaction
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

    @Query("UPDATE episodes SET isCompleted = :isCompleted, playbackPosition = :position WHERE id = :episodeId")
    suspend fun updateEpisodeCompletion(episodeId: String, isCompleted: Boolean, position: Long = 0L)

    @androidx.room.Transaction
    suspend fun markCompletedAndRemoveFromQueue(episodeId: String) {
        updateEpisodeCompletion(episodeId, isCompleted = true, position = 0L)
        deleteQueueItem(episodeId)
    }

    @androidx.room.Transaction
    suspend fun reEnqueueEpisode(episodeId: String, nextPosition: Int) {
        updateEpisodeCompletion(episodeId, isCompleted = false, position = 0L)
        insertQueueItem(QueueEntity(episodeId, nextPosition))
    }

    @androidx.room.Transaction
    @Query("SELECT * FROM queue ORDER BY position ASC")
    suspend fun getQueueSnapshotWithEpisodes(): List<QueueWithEpisode>

    @androidx.room.Transaction
    @Query("SELECT * FROM queue ORDER BY position ASC")
    suspend fun getQueueSync(): List<QueueWithEpisode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToQueue(queueItem: QueueEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM queue WHERE episodeId = :episodeId)")
    suspend fun isEpisodeInQueue(episodeId: String): Boolean

    @Query("SELECT MAX(position) FROM queue")
    suspend fun getMaxQueuePosition(): Int?

    @androidx.room.Transaction
    @Query("SELECT * FROM queue ORDER BY position ASC")
    fun getQueue(): Flow<List<QueueWithEpisode>>
}