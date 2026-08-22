// File: com/codpast/player/service/PodcastPlaybackService.kt
package com.codpast.player.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.codpast.player.data.repository.PlaybackProgressManager
import com.codpast.player.data.repository.PodcastRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.guava.future
import android.net.Uri
import javax.inject.Inject
import com.codpast.player.util.toMediaItem

@AndroidEntryPoint
class PodcastPlaybackService : MediaLibraryService() {

    @Inject
    lateinit var progressManager: PlaybackProgressManager

    @Inject
    lateinit var playbackProgressManager: PlaybackProgressManager

    @Inject
    lateinit var repository: PodcastRepository

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // 1. Configure ExoPlayer explicitly for spoken audio (pitch-preserved speech)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true) // Automatically pause when headphones disconnect
            .build()

        // 2. Attach lifecycle listeners for progress persistence triggers
        setupPlayerListeners()

        // 3. Construct the MediaLibrarySession for system & Android Auto integration
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player,
            AndroidAutoTreeCallback()
        ).build()

        // 4. Start the 500ms ticker for smooth UI position updates
        startMemoryTicker()
        observePlaybackManager()
    }

    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {
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

                // Add the Architect's Queue Resolution Logic
                if (playbackState == Player.STATE_ENDED) {
                    val currentMediaId = player.currentMediaItem?.mediaId
                    if (currentMediaId != null) {
                        serviceScope.launch {
                            // 1. Mark finished and remove
                            repository.markCompletedAndRemoveFromQueue(currentMediaId)

                            // 2. Figure out what to play next using wrap-around
                            val nextEpisode = repository.getNextEpisodeToPlay(currentMediaId)

                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                if (nextEpisode != null) {
                                    // Architecture Note: You might want to grab the full PodcastEntity here
                                    // if you need its artwork for the MediaItem conversion
                                    val nextMediaItem = nextEpisode.toMediaItem(null)
                                    player.setMediaItem(nextMediaItem)
                                    player.prepare()
                                    player.play()
                                } else {
                                    player.stop()
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
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    // Immediate flush on manual scrubber seeks
                    progressManager.triggerImmediateFlush()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Immediate flush when switching episodes
                progressManager.triggerImmediateFlush()
            }
        })
    }

    private fun startMemoryTicker() {
        serviceScope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    player.currentMediaItem?.mediaId?.let { activeId ->
                        progressManager.updateProgress(activeId, player.currentPosition)
                    }
                }
                delay(500L)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // User swiped app away from recent tasks -> force synchronous DB flush
        progressManager.onTeardown()
    }

    override fun onDestroy() {
        // System killing service -> force synchronous DB flush and clean up resources
        serviceScope.cancel()
        progressManager.onTeardown()
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }

    /**
     * Serves the 3-Tier Media Tree navigation for Android Auto dashboard integration.
     */
    private inner class AndroidAutoTreeCallback : MediaLibrarySession.Callback {
        private val rootItem = buildBrowsableMediaItem("root_id", "Codpast")
        private val subscribedItem = buildBrowsableMediaItem("tier_subscriptions", "Subscribed Podcasts")
        private val upNextItem = buildBrowsableMediaItem("tier_up_next", "Up Next Queue")
        private val downloadedItem = buildBrowsableMediaItem("tier_downloads", "Downloaded Episodes")

        private fun buildBrowsableMediaItem(id: String, title: String): MediaItem {
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle(title)
                        .build()
                ).build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
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
                    LibraryResult.ofItemList(
                        ImmutableList.of(subscribedItem, upNextItem, downloadedItem),
                        params
                    )
                )
                "tier_subscriptions" -> serviceScope.future {
                    // 1. Fetch live data from Room
                    val subscriptions = repository.getSubscribedPodcastsSnapshot()

                    // 2. Map Database Entities to Media3 MediaItems
                    val mediaItems = subscriptions.map { podcast ->
                        MediaItem.Builder()
                            .setMediaId(podcast.id)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setIsBrowsable(true) // Browsable because clicking a podcast opens its episodes
                                    .setIsPlayable(false)
                                    .setTitle(podcast.title)
                                    .setArtworkUri(Uri.parse(podcast.artworkUrl))
                                    .build()
                            ).build()
                    }
                    LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                }
                "tier_up_next", "tier_downloads" -> {
                    Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
                }
                else -> Futures.immediateFuture(
                    LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE)
                )
            }
        }
    }
    private fun observePlaybackManager() {
        serviceScope.launch {
            playbackProgressManager.currentEpisode.collect { episode ->
                episode?.let { ep ->
                    player?.let { exoPlayer ->
                        val currentMediaId = exoPlayer.currentMediaItem?.mediaId
                        if (currentMediaId != ep.id && !ep.audioUrl.isNullOrEmpty()) {
                            val metadata = MediaMetadata.Builder()
                                .setTitle(ep.title)
                                .setArtworkUri(ep.imageUrl?.let { Uri.parse(it) })
                                .build()

                            val mediaItem = MediaItem.Builder()
                                .setMediaId(ep.id)
                                .setUri(ep.audioUrl)
                                .setMediaMetadata(metadata)
                                .build()

                            exoPlayer.setMediaItem(mediaItem)
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                    }
                }
            }
        }
    }
}