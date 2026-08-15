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
}