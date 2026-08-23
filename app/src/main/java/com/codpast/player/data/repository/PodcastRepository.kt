// File: com/codpast/player/data/repository/PodcastRepository.kt
package com.codpast.player.data.repository

import com.codpast.player.data.local.dao.PodcastDao
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity
import com.codpast.player.data.network.PodcastIndexApi
import com.codpast.player.data.local.entity.QueueEntity
import com.codpast.player.data.local.entity.QueueWithEpisode
import com.codpast.player.data.repository.PlaybackProgressManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import com.prof18.rssparser.RssParserBuilder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.collections.mapNotNull
import kotlinx.coroutines.flow.firstOrNull

enum class PositionMode {
    APPEND,
    PLAY_NEXT
}

@Singleton
class PodcastRepository @Inject constructor(
    private val podcastDao: PodcastDao,
    private val playbackProgressManager: PlaybackProgressManager
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

    fun getEpisodesByPodcastId(podcastId: String): Flow<List<EpisodeEntity>> {
        return podcastDao.getEpisodesByPodcastId(podcastId)
    }

    suspend fun getEpisodeByIdSnapshot(episodeId: String): EpisodeEntity? {
        return podcastDao.getEpisodeById(episodeId).firstOrNull()
    }

    suspend fun getPodcastByIdSnapshot(podcastId: String): PodcastEntity? {
        return podcastDao.getPodcastById(podcastId).firstOrNull()
    }

    suspend fun savePodcastAndEpisodes(podcast: PodcastEntity, episodes: List<com.codpast.player.data.local.entity.EpisodeEntity>) {
        podcastDao.insertPodcast(podcast)
        podcastDao.insertEpisodes(episodes)
    }

    suspend fun deletePodcastAndEpisodes(podcastId: String) {
        podcastDao.deletePodcast(podcastId)
        podcastDao.deleteEpisodesByPodcastId(podcastId)
    }

    suspend fun enqueueEpisode(
        episodeId: String,
        mode: PositionMode = PositionMode.APPEND,
        currentPlayingIndex: Int = -1
    ) {
        val targetPosition = when (mode) {
            PositionMode.APPEND -> podcastDao.getNextPosition()
            PositionMode.PLAY_NEXT -> {
                val insertPos = currentPlayingIndex + 1
                podcastDao.shiftPositionsUp(insertPos)
                insertPos
            }
        }

        val queueItem = com.codpast.player.data.local.entity.QueueEntity(
            episodeId = episodeId,
            position = targetPosition
        )
        podcastDao.insertQueueItem(queueItem)
    }

    suspend fun removeFromQueue(episodeId: String) = podcastDao.deleteQueueItem(episodeId)

    suspend fun clearQueue() = podcastDao.clearQueue()

    fun getQueueEpisodes(): Flow<List<EpisodeEntity>> {
        return podcastDao.getQueueEpisodes()
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

    suspend fun getQueueSnapshotWithEpisodes(): List<EpisodeEntity> {
        return podcastDao.getQueueSnapshotWithEpisodes().mapNotNull { it.episode }
    }

    suspend fun getNextEpisodeToPlay(currentEpisodeId: String?): EpisodeEntity? {
        val queueWithEpisodes = getQueueSnapshotWithEpisodes()
        if (queueWithEpisodes.isEmpty()) return null

        val currentIndex = queueWithEpisodes.indexOfFirst { it.id == currentEpisodeId }

        if (currentIndex != -1) {
            // 1. Search forward from current position for next uncompleted episode
            for (i in (currentIndex + 1) until queueWithEpisodes.size) {
                if (!queueWithEpisodes[i].isCompleted) return queueWithEpisodes[i]
            }
            // 2. Wrap-around: Search from top of queue up to current position for skipped episodes
            for (i in 0 until currentIndex) {
                if (!queueWithEpisodes[i].isCompleted) return queueWithEpisodes[i]
            }
        } else {
            // If current episode isn't in queue, play first uncompleted item
            return queueWithEpisodes.firstOrNull { !it.isCompleted }
        }
        return null
    }

    suspend fun markCompletedAndRemoveFromQueue(episodeId: String) {
        podcastDao.markCompletedAndRemoveFromQueue(episodeId)
    }

    suspend fun reEnqueueEpisode(episodeId: String) {
        val nextPos = podcastDao.getNextPosition()
        podcastDao.reEnqueueEpisode(episodeId, nextPos)
    }

    suspend fun playEpisode(episodeId: String): EpisodeEntity? {
        if (!podcastDao.isEpisodeInQueue(episodeId)) {
            val maxPos = podcastDao.getMaxQueuePosition() ?: -1
            podcastDao.addToQueue(
                QueueEntity(
                    episodeId = episodeId,
                    position = maxPos + 1
                )
            )
        }
        playbackProgressManager.setCurrentEpisode(episodeId)
        return podcastDao.getEpisodeById(episodeId).firstOrNull()
    }

    suspend fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val currentQueue: List<QueueWithEpisode> = podcastDao.getQueueSync()
        if (fromIndex in currentQueue.indices && toIndex in currentQueue.indices) {
            val mutable = currentQueue.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)

            val updatedQueueEntities = mutable.map { it.queueItem }
            podcastDao.updateQueueOrder(updatedQueueEntities)
        }
    }
    fun getQueue(): Flow<List<QueueWithEpisode>> = podcastDao.getQueue()
}