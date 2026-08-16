// File: com/codpast/player/data/repository/PodcastRepository.kt
package com.codpast.player.data.repository

import com.codpast.player.data.local.dao.PodcastDao
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity
import com.codpast.player.data.network.PodcastIndexApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import com.prof18.rssparser.RssParserBuilder
import java.text.SimpleDateFormat
import java.util.Locale

class PodcastRepository @Inject constructor(
    private val podcastDao: PodcastDao,
    private val api: PodcastIndexApi // Injected the API
) {
    // Initialize the RSS Parser
    private val rssParser = RssParserBuilder().build()

    suspend fun parseRssUrl(feedUrl: String): PodcastEntity? {
        return try {
            // Parse the raw XML feed
            val rssChannel = rssParser.getRssChannel(feedUrl)

            // Map the result to our Database Entity format
            PodcastEntity(
                id = feedUrl,
                title = rssChannel.title ?: "Unknown Title",
                description = rssChannel.description ?: "No description available", // Fixed here!
                feedUrl = feedUrl,
                artworkUrl = rssChannel.image?.url ?: "",
                isSubscribed = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
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

    suspend fun syncEpisodes(podcastId: String, feedUrl: String) {
        val episodes = fetchEpisodes(podcastId, feedUrl)
        if (episodes.isNotEmpty()) {
            saveEpisodes(episodes)
        }
    }

    suspend fun saveEpisodes(episodes: List<EpisodeEntity>) {
        podcastDao.insertEpisodes(episodes)
    }

    fun getPodcastById(podcastId: String): Flow<PodcastEntity?> {
        return podcastDao.getPodcastById(podcastId)
    }

    fun getEpisodeById(episodeId: String): Flow<EpisodeEntity?> {
        return podcastDao.getEpisodeById(episodeId)
    }

    suspend fun fetchEpisodes(podcastId: String, feedUrl: String): List<EpisodeEntity> {
        return try {
            val rssChannel = rssParser.getRssChannel(feedUrl)
            rssChannel.items.map { item ->
                val pubDateLong = try {
                    item.pubDate?.let {
                        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
                        format.parse(it)?.time
                    } ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                EpisodeEntity(
                    id = EpisodeEntity.generateId(item.guid, podcastId, item.audio ?: ""),
                    podcastId = podcastId,
                    title = item.title ?: "Unknown Episode",
                    description = item.description ?: item.content ?: "", // ADD THIS LINE!
                    audioUrl = item.audio ?: "",
                    imageUrl = item.image ?: rssChannel.image?.url ?: "",
                    duration = 0L,
                    playbackPosition = 0L,
                    isCompleted = false,
                    publishedAt = pubDateLong
                )
            }.filter { it.audioUrl.isNotBlank() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}