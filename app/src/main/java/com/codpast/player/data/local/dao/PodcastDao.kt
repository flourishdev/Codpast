// File: com/codpast/player/data/local/dao/PodcastDao.kt
package com.codpast.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity
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

    @androidx.room.Query("DELETE FROM podcasts WHERE id = :podcastId")
    suspend fun deletePodcast(podcastId: String)

    @androidx.room.Query("DELETE FROM episodes WHERE podcastId = :podcastId")
    suspend fun deleteEpisodesByPodcastId(podcastId: String)

// --- Queue Management ---

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(queueItem: com.codpast.player.data.local.entity.QueueEntity)

    @androidx.room.Update
    suspend fun updateQueueItems(items: List<com.codpast.player.data.local.entity.QueueEntity>)

    @androidx.room.Query("SELECT COALESCE(MAX(position), -1) + 1 FROM queue")
    suspend fun getNextPosition(): Int

    @androidx.room.Query("UPDATE queue SET position = position + 1 WHERE position >= :fromPosition")
    suspend fun shiftPositionsUp(fromPosition: Int)

    @androidx.room.Transaction
    suspend fun updateQueueOrder(episodes: List<com.codpast.player.data.local.entity.QueueEntity>) {
        val reindexed = episodes.mapIndexed { index, entity ->
            entity.copy(position = index)
        }
        updateQueueItems(reindexed)
    }

    @androidx.room.Query(
        "SELECT episodes.* FROM episodes " +
                "INNER JOIN queue ON episodes.id = queue.episodeId " +
                "ORDER BY queue.position ASC"
    )
    fun getQueueEpisodes(): kotlinx.coroutines.flow.Flow<List<com.codpast.player.data.local.entity.EpisodeEntity>>
}