// File: com/codpast/player/data/repository/PodcastRepository.kt
package com.codpast.player.data.repository

import com.codpast.player.data.local.dao.PodcastDao
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity
import com.codpast.player.data.network.PodcastIndexApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import com.prof18.rssparser.RssParserBuilder

class PodcastRepository @Inject constructor(
    private val podcastDao: PodcastDao,
    private val api: PodcastIndexApi // Injected the API
) {
    // Initialize the RSS Parser
    private val rssParser = RssParserBuilder().build()

    suspend fun searchOrParse(query: String) {
        if (query.startsWith("http://") || query.startsWith("https://")) {
            // It's a raw URL! Parse the RSS feed directly
            val rssChannel = rssParser.getRssChannel(query)
            println("Parsed direct RSS: ${rssChannel.title}")
            // TODO: Map RssChannel to PodcastEntity and save/display
        } else {
            // It's a text search! Use the Podcast Index API
            val response = api.searchPodcasts(query)
            println("Searched API: Found ${response.feeds.size} results")
            // TODO: Map API response to Domain Models
        }
    }

    fun getSubscribedPodcasts(): Flow<List<PodcastEntity>> {
        return podcastDao.getSubscribedPodcasts()
    }

    suspend fun getSubscribedPodcastsSnapshot(): List<PodcastEntity> {
        return podcastDao.getSubscribedPodcastsSnapshot()
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