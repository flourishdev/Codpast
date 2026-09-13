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
import com.codpast.player.util.toMediaItem
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    init {
        initializeController()
        startProgressTracker()
        observeQueue()
    }

    private fun observeQueue() {
        viewModelScope.launch {
            combine(
                repository.getQueueEpisodes(),
                _state.map { it.currentEpisode?.id }.distinctUntilChanged()
            ) { queue, currentId ->
                if (currentId != null) {
                    val currentIndex = queue.indexOfFirst { it.id == currentId }
                    currentIndex != -1 && currentIndex < queue.size - 1
                } else {
                    queue.isNotEmpty()
                }
            }.collect { hasNext ->
                _state.update { it.copy(hasNextEpisode = hasNext) }
            }
        }
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PodcastPlaybackService::class.java)
        )

        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            try {
                mediaController = mediaControllerFuture?.get()
                setupPlayerListener()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupPlayerListener() {
        val controller = mediaController ?: return
        updateCurrentMediaItem(controller.currentMediaItem)

        // Initial state sync
        _state.update {
            it.copy(
                isPlaying = controller.isPlaying,
                isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                isPreparing = controller.playbackState == Player.STATE_BUFFERING,
                durationMs = if (controller.playbackState == Player.STATE_READY) controller.duration.coerceAtLeast(0L) else it.durationMs
            )
        }

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                _state.update {
                    it.copy(isPlaying = player.isPlaying)
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
            val realEpisode = repository.getEpisodeByIdSnapshot(episodeId)
            val realPodcast = realEpisode?.podcastId?.let { repository.getPodcastByIdSnapshot(it) }

            if (realEpisode != null) {
                _state.update {
                    it.copy(
                        currentEpisode = realEpisode,
                        currentPodcast = realPodcast,
                        durationMs = if (realEpisode.duration > 0L) realEpisode.duration else (mediaController?.duration?.coerceAtLeast(0L) ?: 0L)
                    )
                }
            } else {
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
                    val currentPos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
                    _state.update {
                        it.copy(currentPositionMs = currentPos)
                    }
                    _state.value.currentEpisode?.id?.let { epId ->
                        repository.updatePlaybackPosition(epId, currentPos)
                    }
                }
                delay(500L)
            }
        }
    }

    fun onIntent(intent: PlayerIntent) {
        val controller = mediaController
        when (intent) {
            is PlayerIntent.TogglePlayPause -> {
                if (controller == null) return
                if (controller.currentMediaItem == null) {
                    // Cold-launch fallback: fetch top uncompleted queue episode from Room
                    viewModelScope.launch {
                        val queueSnapshot = repository.getQueueSnapshotWithEpisodes()
                        val topItem = queueSnapshot.firstOrNull { !it.isCompleted }
                        if (topItem != null) {
                            val podcast = repository.getPodcastByIdSnapshot(topItem.podcastId)
                            val mediaItem = topItem.toMediaItem(podcast)
                            controller.setMediaItem(mediaItem)
                            if (topItem.playbackPosition > 0L) {
                                controller.seekTo(topItem.playbackPosition)
                            }
                            controller.prepare()
                            controller.play()
                        }
                    }
                } else {
                    if (controller.playbackState == Player.STATE_IDLE) {
                        controller.prepare()
                    }
                    if (controller.isPlaying) {
                        controller.pause()
                    } else {
                        controller.play()
                    }
                }
            }

            is PlayerIntent.SeekTo -> {
                controller?.seekTo(intent.positionMs)
                _state.update { it.copy(currentPositionMs = intent.positionMs) }
            }

            is PlayerIntent.SkipForward -> {
                val current = controller?.currentPosition ?: 0L
                val duration = controller?.duration ?: 0L
                val skipMs = if (intent.ms > 0L) intent.ms else 30000L
                val newPos = (current + skipMs).coerceAtMost(duration)
                controller?.seekTo(newPos)
            }

            is PlayerIntent.SkipBackward -> {
                val current = controller?.currentPosition ?: 0L
                val skipMs = if (intent.ms > 0L) intent.ms else 10000L
                val newPos = (current - skipMs).coerceAtLeast(0L)
                controller?.seekTo(newPos)
            }

            is PlayerIntent.SetSpeed -> {
                controller?.playbackParameters = PlaybackParameters(intent.speed)
                _state.update { it.copy(playbackSpeed = intent.speed) }
            }

            is PlayerIntent.SkipToNext -> {
                if (controller?.hasNextMediaItem() == true) {
                    controller.seekToNextMediaItem()
                } else {
                    viewModelScope.launch {
                        val currentId = _state.value.currentEpisode?.id
                        val nextEpisode = repository.getNextEpisodeToPlay(currentId)
                        if (nextEpisode != null) {
                            repository.playEpisode(nextEpisode.id)
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaControllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        mediaController = null
    }
}