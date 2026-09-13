package com.codpast.player.service

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
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
import javax.inject.Inject
import java.io.File
import com.codpast.player.R
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import kotlin.time.Duration.Companion.seconds

const val ACTION_SKIP_NEXT = "com.codpast.player.SKIP_NEXT"

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



    private val skipNextCommand = SessionCommand(ACTION_SKIP_NEXT, Bundle.EMPTY)
    private val skipNextButton by lazy {
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName(getString(R.string.skip_next))
            .setSessionCommand(skipNextCommand)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // Configure ExoPlayer explicitly for spoken audio (pitch-preserved speech)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Configure ExoPlayer with 10s rewind / 30s fast-forward increments
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true) // Automatically pause when headphones disconnect
            .setSeekBackIncrementMs(10.seconds.inWholeMilliseconds)   // 10s Rewind
            .setSeekForwardIncrementMs(30.seconds.inWholeMilliseconds) // 30s Fast-Forward
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

        // Start the 500ms ticker for smooth UI position updates
        startMemoryTicker()
        observePlaybackManager()
        mediaLibrarySession?.setCustomLayout(ImmutableList.of(skipNextButton))

        serviceScope.launch {
            val queueSnapshot = repository.getQueueSnapshotWithEpisodes()
            val topEpisode = queueSnapshot.firstOrNull { !it.isCompleted }
            if (topEpisode != null && player?.playbackState == Player.STATE_IDLE) {
                prepareAndSeekEpisode(topEpisode.id)
            }
        }
    }

    private fun prepareAndSeekEpisode(episodeId: String) {
        serviceScope.launch {
            val episode = repository.getEpisodeByIdSnapshot(episodeId) ?: return@launch
            val podcast = repository.getPodcastByIdSnapshot(episode.podcastId)
            val mediaItem = episode.toMediaItem(podcast)

            withContext(Dispatchers.Main) {
                player?.apply {
                    setMediaItem(mediaItem)
                    if (episode.playbackPosition > 0L && !episode.isCompleted) {
                        seekTo(episode.playbackPosition)
                    }
                    prepare()
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

                            // 2. Resolve next episode
                            val nextEpisode = repository.getNextEpisodeToPlay(currentMediaId)

                            // Defensive check: Service active & player instance matches
                            if (!isActive || player != exoPlayer) return@launch

                            if (nextEpisode != null) {
                                // 3. Use prepareAndSeekEpisode to load next item with restored position & metadata
                                prepareAndSeekEpisode(nextEpisode.id)
                                withContext(Dispatchers.Main) {
                                    exoPlayer.play()
                                }
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
                // Immediate flush when switching episodes
                progressManager.triggerImmediateFlush()
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
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(skipNextCommand)
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                connectionResult.availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_SKIP_NEXT) {
                val currentMediaId = player?.currentMediaItem?.mediaId
                serviceScope.launch {
                    val nextEpisode = repository.getNextEpisodeToPlay(currentMediaId)
                    if (nextEpisode != null) {
                        withContext(Dispatchers.Main) {
                            player?.apply {
                                setMediaItem(nextEpisode.toMediaItem(null))
                                prepare()
                                play()
                            }
                        }
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

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
                    // Dynamic lookup: parentId is a Podcast ID!
                    val episodes = repository.getEpisodesForPodcastSnapshot(parentId)
                    val podcast = repository.getPodcastByIdSnapshot(parentId)
                    val mediaItems = episodes.map { ep -> ep.toMediaItem(podcast) }
                    LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                }
            }
        }
    }

    private var isInitialColdStart = true

    private fun observePlaybackManager() {
        serviceScope.launch {
            progressManager.currentEpisode.collect { episode: EpisodeEntity? ->
                episode?.let { ep ->
                    player?.let { exoPlayer ->
                        val currentMediaId = exoPlayer.currentMediaItem?.mediaId
                        if (currentMediaId != ep.id && !ep.audioUrl.isNullOrEmpty()) {
                            // Immediately stop playback before swapping items to prevent progress ticker race
                            exoPlayer.stop()

                            // Check if episode is downloaded locally on disk
                            val download = repository.getDownloadForEpisodeSnapshot(ep.id)
                            val finalAudioUrl = if (download?.status == com.codpast.player.data.local.entity.DownloadStatus.COMPLETED) {
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

                            // Synchronously fetch parent podcast to populate artist and artwork in MediaMetadata
                            val podcast = repository.getPodcastByIdSnapshot(ep.podcastId)

                            // Defensive check: Coroutine might have suspended, check if we should still proceed
                            if (!isActive || player != exoPlayer) return@collect

                            val mediaItem = playableEpisode.toMediaItem(podcast)

                            exoPlayer.setMediaItem(mediaItem)

                            // Seek to saved position from Room SSOT
                            if (ep.playbackPosition > 0L) {
                                exoPlayer.seekTo(ep.playbackPosition)
                            }

                            exoPlayer.prepare()

                            // Autoplay on user action; stay paused on initial cold start restoration
                            if (isInitialColdStart) {
                                isInitialColdStart = false
                                exoPlayer.playWhenReady = false
                            } else {
                                exoPlayer.play()
                            }
                        }
                    }
                }
            }
        }
    }
}