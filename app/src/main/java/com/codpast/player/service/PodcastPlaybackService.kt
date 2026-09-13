package com.codpast.player.service

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.codpast.player.R
import com.codpast.player.data.local.entity.DownloadStatus
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.repository.PlaybackProgressManager
import com.codpast.player.data.repository.PodcastRepository
import com.codpast.player.util.toMediaItem
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class PodcastPlaybackService : MediaLibraryService() {

    @Inject
    lateinit var progressManager: PlaybackProgressManager

    @Inject
    lateinit var repository: PodcastRepository

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var playerListener: Player.Listener

    private var isInitialColdStart = true

    override fun onCreate() {
        super.onCreate()

        // Configure ExoPlayer explicitly for spoken audio using stable AudioAttributes
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Configure ExoPlayer using 100% stable APIs
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true) // Automatically pause when headphones disconnect
            .build()
        player = exoPlayer

        // Attach lifecycle listeners for progress persistence triggers
        playerListener = createPlayerListener(exoPlayer)
        exoPlayer.addListener(playerListener)

        // Construct the MediaLibrarySession for system & Android Auto integration
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            AndroidAutoTreeCallback()
        ).build()

        // Start memory position ticker and observe playback manager for queue/cold start loading
        startMemoryTicker()
        observePlaybackManager()
    }

    /**
     * Unified suspending helper to fetch episode, resolve local download paths,
     * attach full podcast metadata, restore saved position, and prepare ExoPlayer.
     */
    private suspend fun prepareAndSeekEpisode(
        episodeId: String,
        autoPlay: Boolean = false
    ) {
        val ep = repository.getEpisodeByIdSnapshot(episodeId) ?: return
        if (ep.audioUrl.isNullOrEmpty()) return

        // Resolve local downloaded file vs remote URL
        val download = repository.getDownloadForEpisodeSnapshot(ep.id)
        val finalAudioUrl = if (download?.status == DownloadStatus.COMPLETED) {
            val localFile = File(download.localPath)
            if (localFile.exists() && localFile.length() > 0) {
                Uri.fromFile(localFile).toString()
            } else {
                ep.audioUrl
            }
        } else {
            ep.audioUrl
        }

        val playableEpisode = ep.copy(audioUrl = finalAudioUrl)
        val podcast = repository.getPodcastByIdSnapshot(ep.podcastId)
        val mediaItem = playableEpisode.toMediaItem(podcast)

        withContext(Dispatchers.Main) {
            player?.apply {
                if (currentMediaItem?.mediaId != mediaItem.mediaId) {
                    stop()
                    setMediaItem(mediaItem)
                    if (ep.playbackPosition > 0L && !ep.isCompleted) {
                        seekTo(ep.playbackPosition)
                    }
                    prepare()
                    playWhenReady = autoPlay
                } else if (playbackState == Player.STATE_IDLE) {
                    prepare()
                    playWhenReady = autoPlay
                }
            }
        }
    }

    private fun observePlaybackManager() {
        serviceScope.launch {
            progressManager.currentEpisode.collect { episode: EpisodeEntity? ->
                episode?.let { ep ->
                    val shouldAutoPlay = if (isInitialColdStart) {
                        isInitialColdStart = false
                        false
                    } else {
                        true
                    }
                    prepareAndSeekEpisode(ep.id, autoPlay = shouldAutoPlay)
                }
            }
        }
    }

    private fun createPlayerListener(exoPlayer: ExoPlayer): Player.Listener {
        return object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    // Bypass 5000ms timer & flush immediately to disk when paused
                    progressManager.triggerImmediateFlush()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    progressManager.triggerImmediateFlush()
                }

                if (playbackState == Player.STATE_ENDED) {
                    val currentMediaId = exoPlayer.currentMediaItem?.mediaId
                    if (currentMediaId != null) {
                        serviceScope.launch {
                            // 1. Mark finished and remove from queue
                            repository.markCompletedAndRemoveFromQueue(currentMediaId)

                            // 2. Figure out what to play next using wrap-around
                            val nextEpisode = repository.getNextEpisodeToPlay(currentMediaId)

                            // Defensive check: Service might be destroying, or player instance changed
                            if (!isActive || player != exoPlayer) return@launch

                            if (nextEpisode != null) {
                                prepareAndSeekEpisode(nextEpisode.id, autoPlay = true)
                            } else {
                                withContext(Dispatchers.Main) {
                                    exoPlayer.stop()
                                }
                            }
                        }
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    // Immediate flush on manual scrubber seeks
                    progressManager.triggerImmediateFlush()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Immediate flush during item transition omitted to avoid 0L overwrite on initial load
            }
        }
    }

    private fun startMemoryTicker() {
        serviceScope.launch {
            while (isActive) {
                val activePlayer = player
                if (activePlayer != null && activePlayer.isPlaying) {
                    activePlayer.currentMediaItem?.mediaId?.let { activeId ->
                        progressManager.updateProgress(activeId, activePlayer.currentPosition)
                    }
                }
                delay(duration = 5.seconds)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Completely stop audio playback and release player resources when app is swiped away
        player?.let { exoPlayer ->
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }

        // Stop the foreground service and remove notification
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // 1. Cancel scope to stop tickers and library observers immediately
        serviceScope.cancel()

        // 2. Clear progress management
        progressManager.onTeardown()

        // 3. Robust player teardown to prevent MediaCodec dead thread issues
        player?.let { exoPlayer ->
            exoPlayer.stop()
            exoPlayer.removeListener(playerListener)
            mediaLibrarySession?.release()
            mediaLibrarySession = null
            exoPlayer.release()
            player = null
        }

        super.onDestroy()
    }

    /**
     * Serves the 3-Tier Media Tree navigation for Android Auto dashboard integration.
     * Uses 100% stable Media3 APIs.
     */
    private inner class AndroidAutoTreeCallback : MediaLibrarySession.Callback {

        private val subscribedItem = buildBrowsableMediaItem(
            id = "tier_subscriptions",
            title = "Follows",
            iconResId = R.drawable.ic_launcher_foreground
        )
        private val upNextItem = buildBrowsableMediaItem(
            id = "tier_up_next",
            title = "Queue",
            iconResId = R.drawable.ic_launcher_foreground
        )
        private val downloadedItem = buildBrowsableMediaItem(
            id = "tier_downloads",
            title = "Downloads",
            iconResId = R.drawable.ic_launcher_foreground
        )

        private fun buildBrowsableMediaItem(id: String, title: String, iconResId: Int): MediaItem {
            val iconUri = "android.resource://$packageName/$iconResId".toUri()
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle(title)
                        .setArtworkUri(iconUri)
                        .build()
                )
                .build()
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Standard ConnectionResult.accept using pure stable session & player commands
            return super.onConnect(session, controller)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId("root_id")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("Codpast Library")
                        .build()
                ).build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return when (parentId) {
                "root_id" -> Futures.immediateFuture(
                    LibraryResult.ofItemList(ImmutableList.of(subscribedItem, upNextItem, downloadedItem), params)
                )
                "tier_subscriptions" -> serviceScope.future {
                    val subscriptions = repository.getSubscribedPodcastsSnapshot()
                    val mediaItems = subscriptions.map { podcast ->
                        MediaItem.Builder()
                            .setMediaId(podcast.id)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setTitle(podcast.title)
                                    .setArtworkUri(if (podcast.artworkUrl.isNotBlank()) podcast.artworkUrl.toUri() else null)
                                    .build()
                            ).build()
                    }
                    LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                }
                "tier_up_next" -> serviceScope.future {
                    val queueEpisodes = repository.getQueueSnapshotWithEpisodes()
                    val mediaItems = queueEpisodes.map { ep -> ep.toMediaItem(null) }
                    LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                }
                "tier_downloads" -> serviceScope.future {
                    val completedDownloads = repository.getAllCompletedDownloadsSnapshot()
                    val mediaItems = completedDownloads.mapNotNull { download ->
                        val episode = repository.getEpisodeByIdSnapshot(download.episodeId)
                        val podcast = episode?.let { repository.getPodcastByIdSnapshot(it.podcastId) }

                        episode?.let { ep ->
                            val localFileUri = Uri.fromFile(File(download.localPath)).toString()
                            ep.copy(audioUrl = localFileUri).toMediaItem(podcast)
                        }
                    }
                    LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                }
                else -> serviceScope.future {
                    val episodes = repository.getEpisodesForPodcastSnapshot(parentId)
                    val podcast = repository.getPodcastByIdSnapshot(parentId)
                    val mediaItems = episodes.map { ep -> ep.toMediaItem(podcast) }
                    LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                }
            }
        }
    }
}