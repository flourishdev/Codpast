// File: com/codpast/player/data/repository/PodcastRepository.kt
package com.codpast.player.data.repository

import com.codpast.player.data.local.dao.PodcastDao
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity
import kotlinx.coroutines.flow.Flow

class PodcastRepository(
    private val podcastDao: PodcastDao
) {
    fun getSubscribedPodcasts(): Flow<List<PodcastEntity>> {
        return podcastDao.getSubscribedPodcasts()
    }

    fun getEpisodesForPodcast(podcastId: String): Flow<List<EpisodeEntity>> {
        return podcastDao.getEpisodesForPodcast(podcastId)
    }

    suspend fun savePodcast(podcast: PodcastEntity) {
        podcastDao.insertPodcast(podcast)
    }

    suspend fun saveEpisodes(episodes: List<EpisodeEntity>) {
        podcastDao.insertEpisodes(episodes)
    }
}