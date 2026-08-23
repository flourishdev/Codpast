package com.codpast.player.ui.screens

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.codpast.player.data.repository.PodcastRepository
import com.codpast.player.service.PodcastPlaybackService
import com.codpast.player.ui.mvi.PlayerIntent
import com.codpast.player.ui.mvi.PlayerUiState
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: PodcastRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    // The bridge to our background service
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    init {
        initializeController()
        startProgressTracker()
    }

    private fun initializeController() {
        // 1. Point the token to our specific PodcastPlaybackService
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PodcastPlaybackService::class.java)
        )

        // 2. Build the controller asynchronously
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        // 3. Wait for it to connect, then set up our listeners
        mediaControllerFuture?.addListener({
            mediaController = mediaControllerFuture?.get()
            setupPlayerListener()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupPlayerListener() {
        // 1. Grab the currently playing item immediately upon connection (in case it's already playing)
        updateCurrentMediaItem(mediaController?.currentMediaItem)

        mediaController?.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                _state.update {
                    it.copy(
                        isPlaying = player.isPlaying,
                        hasNextEpisode = player.hasNextMediaItem() // Flips boolean to unlock Next button
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        isPreparing = playbackState == Player.STATE_BUFFERING,
                        durationMs = if (playbackState == Player.STATE_READY) {
                            mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                        } else {
                            it.durationMs
                        }
                    )
                }
            }

            // 2. Listen for track changes to update MiniPlayer artwork and title
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateCurrentMediaItem(mediaItem)
            }
        })
    }

    private fun updateCurrentMediaItem(mediaItem: androidx.media3.common.MediaItem?) {
        if (mediaItem == null) {
            _state.update {
                it.copy(
                    currentEpisode = null,
                    currentPodcast = null
                )
            }
            return
        }

        val episodeId = mediaItem.mediaId

        viewModelScope.launch {
            // Attempt to fetch real entities from Room SSOT using episodeId
            val realEpisode = repository.getEpisodeByIdSnapshot(episodeId)
            val realPodcast = realEpisode?.podcastId?.let { repository.getPodcastByIdSnapshot(it) }

            if (realEpisode != null) {
                _state.update {
                    it.copy(
                        currentEpisode = realEpisode,
                        currentPodcast = realPodcast,
                        durationMs = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                    )
                }
            } else {
                // Fallback for external URIs using MediaMetadata payload
                val metadata = mediaItem.mediaMetadata

                val fallbackEpisode = com.codpast.player.data.local.entity.EpisodeEntity(
                    id = mediaItem.mediaId,
                    podcastId = "unknown_podcast",
                    title = metadata.title?.toString() ?: "Unknown Episode",
                    description = "",
                    audioUrl = "",
                    imageUrl = metadata.artworkUri?.toString() ?: "",
                    publishedAt = 0L,
                    duration = 0L,
                    isCompleted = false,
                    playbackPosition = 0L
                )

                val fallbackPodcast = com.codpast.player.data.local.entity.PodcastEntity(
                    id = "unknown_podcast",
                    title = metadata.artist?.toString() ?: metadata.albumTitle?.toString() ?: "Unknown Podcast",
                    description = "",
                    artworkUrl = metadata.artworkUri?.toString() ?: "",
                    feedUrl = "",
                    isSubscribed = false
                )

                _state.update {
                    it.copy(
                        currentEpisode = fallbackEpisode,
                        currentPodcast = fallbackPodcast,
                        durationMs = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                    )
                }
            }
        }
    }

    private fun startProgressTracker() {
        viewModelScope.launch {
            while (true) {
                if (_state.value.isPlaying) {
                    _state.update {
                        it.copy(currentPositionMs = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L)
                    }
                }
                delay(1000L)
            }
        }
    }

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.TogglePlayPause -> {
                if (mediaController?.isPlaying == true) {
                    mediaController?.pause()
                } else {
                    mediaController?.play()
                }
            }
            is PlayerIntent.SeekTo -> {
                mediaController?.seekTo(intent.positionMs)
                _state.update { it.copy(currentPositionMs = intent.positionMs) }
            }
            is PlayerIntent.SkipForward -> {
                val current = mediaController?.currentPosition ?: 0L
                val duration = mediaController?.duration ?: 0L
                val newPos = (current + intent.ms).coerceAtMost(duration)
                mediaController?.seekTo(newPos)
            }
            is PlayerIntent.SkipBackward -> {
                val current = mediaController?.currentPosition ?: 0L
                val newPos = (current - intent.ms).coerceAtLeast(0L)
                mediaController?.seekTo(newPos)
            }
            is PlayerIntent.SetSpeed -> {
                mediaController?.playbackParameters = PlaybackParameters(intent.speed)
                _state.update { it.copy(playbackSpeed = intent.speed) }
            }
            is PlayerIntent.SkipToNext -> {
                if (mediaController?.hasNextMediaItem() == true) {
                    mediaController?.seekToNextMediaItem()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
    }
}