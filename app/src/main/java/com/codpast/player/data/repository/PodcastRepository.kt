// File: com/codpast/player/data/repository/PodcastRepository.kt
package com.codpast.player.data.repository

import com.codpast.player.data.local.dao.PodcastDao
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity
import com.codpast.player.data.local.entity.QueueEntity
import com.codpast.player.data.local.entity.QueueWithEpisode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import com.prof18.rssparser.RssParserBuilder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.collections.mapNotNull
import kotlinx.coroutines.flow.firstOrNull
import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.codpast.player.data.local.entity.DownloadEntity
import com.codpast.player.service.EpisodeDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import android.net.Uri
import com.codpast.player.data.local.entity.DownloadStatus as DbDownloadStatus

enum class PositionMode {
    APPEND,
    PLAY_NEXT
}

@Singleton
class PodcastRepository @Inject constructor(
    private val podcastDao: PodcastDao,
    private val playbackProgressManager: PlaybackProgressManager,
    @ApplicationContext private val context: Context
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

    suspend fun savePodcast(podcast: PodcastEntity) {
        podcastDao.insertPodcast(podcast)
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

    suspend fun savePodcastAndEpisodes(podcast: PodcastEntity, episodes: List<EpisodeEntity>) {
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

        val queueItem = QueueEntity(
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

        var episode = podcastDao.getEpisodeById(episodeId).firstOrNull()

        // Resolving Local Offline Audio File if Downloaded
        val download = podcastDao.getDownloadForEpisodeSnapshot(episodeId)
        if (episode != null && download?.status == com.codpast.player.data.local.entity.DownloadStatus.COMPLETED) {
            val localFile = File(download.localPath)
            if (localFile.exists() && localFile.length() > 0) {
                // Convert raw file path to proper file:// URI scheme for ExoPlayer
                val fileUri = Uri.fromFile(localFile).toString()
                episode = episode.copy(audioUrl = fileUri)
            }
        }

        playbackProgressManager.setCurrentEpisode(episodeId)
        return episode
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

    suspend fun toggleSubscription(podcast: PodcastEntity) {
        val exists = podcastDao.isPodcastSaved(podcast.id)
        if (exists) {
            val current = podcastDao.getPodcastById(podcast.id).firstOrNull()
            val newStatus = !(current?.isSubscribed ?: false)
            podcastDao.updateSubscriptionStatus(podcast.id, newStatus)
        } else {
            // Podcast not yet in DB (e.g. from Search) -> Save with isSubscribed = true
            val newPodcast = podcast.copy(isSubscribed = true)
            podcastDao.insertPodcast(newPodcast)
        }
    }

    fun getDownloadForEpisode(episodeId: String): Flow<DownloadEntity?> {
        return podcastDao.getDownloadForEpisode(episodeId)
    }

    fun getAllDownloads(): Flow<List<DownloadEntity>> {
        return podcastDao.getAllDownloads()
    }

    suspend fun downloadEpisode(episode: EpisodeEntity) {
        val downloadsDir = File(context.filesDir, "downloads").apply {
            if (!exists()) mkdirs()
        }
        val targetFile = File(downloadsDir, "${episode.id.hashCode()}.mp3")

        // 1. Immediately insert DOWNLOADING record into Room SQLite SSOT so UI updates instantly
        val initialDownloadRecord = DownloadEntity(
            episodeId = episode.id,
            podcastId = episode.podcastId,
            localPath = targetFile.absolutePath,
            progress = 0,
            status = DbDownloadStatus.DOWNLOADING
        )
        podcastDao.insertOrUpdateDownload(initialDownloadRecord)

        // 2. Dispatch WorkManager for background network transfer
        val inputData = Data.Builder()
            .putString(EpisodeDownloadWorker.KEY_EPISODE_ID, episode.id)
            .putString(EpisodeDownloadWorker.KEY_PODCAST_ID, episode.podcastId)
            .putString(EpisodeDownloadWorker.KEY_AUDIO_URL, episode.audioUrl)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadWorkRequest = OneTimeWorkRequestBuilder<EpisodeDownloadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(downloadWorkRequest)
    }

    suspend fun deleteDownload(episodeId: String) {
        val download = podcastDao.getDownloadForEpisodeSnapshot(episodeId)
        if (download != null) {
            val file = File(download.localPath)
            if (file.exists()) file.delete()
            podcastDao.deleteDownload(episodeId)
        }
    }

    suspend fun getDownloadForEpisodeSnapshot(episodeId: String): DownloadEntity? {
        return podcastDao.getDownloadForEpisodeSnapshot(episodeId)
    }
}