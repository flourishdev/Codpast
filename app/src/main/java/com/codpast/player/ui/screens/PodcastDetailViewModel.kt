package com.codpast.player.ui.screens

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.SessionToken
import com.codpast.player.data.repository.PodcastRepository
import com.codpast.player.service.PodcastPlaybackService
import com.codpast.player.ui.mvi.PodcastDetailIntent
import com.codpast.player.ui.mvi.PodcastDetailUiState
import com.codpast.player.ui.mvi.QueueContract
import com.codpast.player.util.toMediaItem
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    private val repository: PodcastRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val podcastId: String? = savedStateHandle.get<String>("podcastId")
        ?.takeIf { it.isNotBlank() && it != "null" && it != "{podcastId}" }

    private val feedUrl: String? = savedStateHandle.get<String>("feedUrl")
        ?.takeIf { it.isNotBlank() && it != "null" && it != "{feedUrl}" }

    private val _state = MutableStateFlow(PodcastDetailUiState(isLoading = true))
    val state: StateFlow<PodcastDetailUiState> = _state.asStateFlow()

    // The bridge to our background audio service
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    init {
        loadPodcastDetails()
        initializeController()
        observeQueueState()
    }

    private fun initializeController() {
        // Point the token to our specific PodcastPlaybackService
        val sessionToken = SessionToken(context, ComponentName(context, PodcastPlaybackService::class.java))

        // Build the controller asynchronously
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        // Wait for it to connect
        mediaControllerFuture?.addListener({
            mediaController = mediaControllerFuture?.get()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun observeQueueState() {
        viewModelScope.launch {
            repository.getQueueEpisodes().collectLatest { queuedEpisodes ->
                _state.update {
                    it.copy(queuedEpisodeIds = queuedEpisodes.map { ep -> ep.id }.toSet())
                }
            }
        }
    }

    private fun loadPodcastDetails() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                var targetFeedUrl = feedUrl
                var isCurrentlySubscribed = false
                var localPodcast: com.codpast.player.data.local.entity.PodcastEntity? = null

                // 1. Check local DB to see if we are already subscribed
                if (podcastId != null) {
                    localPodcast = repository.getPodcastById(podcastId).firstOrNull()
                    if (localPodcast != null) {
                        isCurrentlySubscribed = true
                        // If Subscriptions screen didn't pass a feedUrl, grab it from the saved podcast!
                        if (targetFeedUrl.isNullOrBlank()) {
                            targetFeedUrl = localPodcast.feedUrl
                        }
                    }
                }

                // 2. Fetch the absolute latest episodes from the Network (so it's always up to date)
                if (!targetFeedUrl.isNullOrBlank()) {
                    val remotePodcast = repository.parseRssUrl(targetFeedUrl!!)
                    if (remotePodcast != null) {
                        val remoteEpisodes = repository.fetchEpisodes(remotePodcast.id, targetFeedUrl!!)

                        _state.update {
                            it.copy(
                                isLoading = false,
                                podcast = remotePodcast,
                                episodes = remoteEpisodes,
                                isSubscribed = isCurrentlySubscribed
                            )
                        }

                        // Architecture Bonus: Automatically update the DB with new episodes if subscribed
                        if (isCurrentlySubscribed) {
                            repository.savePodcastAndEpisodes(remotePodcast, remoteEpisodes)
                        }
                    } else {
                        throw Exception("Failed to load podcast from feed.")
                    }
                } else if (localPodcast != null) {
                    // 3. Fallback: Load local DB episodes if we somehow have no URL
                    val localEpisodes = repository.getEpisodesByPodcastId(localPodcast.id).firstOrNull() ?: emptyList()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            podcast = localPodcast,
                            episodes = localEpisodes,
                            isSubscribed = true
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false, errorMessage = "No podcast ID or Feed URL provided.") }
                }

            } catch (e: Exception) {
                // 4. Offline Fallback: If network fails, load what we have saved in Room
                if (podcastId != null) {
                    val localPodcast = repository.getPodcastById(podcastId).firstOrNull()
                    val localEpisodes = repository.getEpisodesByPodcastId(podcastId).firstOrNull() ?: emptyList()

                    if (localPodcast != null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                podcast = localPodcast,
                                episodes = localEpisodes,
                                isSubscribed = true,
                                errorMessage = "Network failed. Viewing offline saved episodes."
                            )
                        }
                        return@launch
                    }
                }
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun onIntent(intent: PodcastDetailIntent) {
        viewModelScope.launch {
            when (intent) {
                is PodcastDetailIntent.RefreshFeed -> refreshFeed()
                is PodcastDetailIntent.ToggleSubscription -> toggleSubscription()
                is PodcastDetailIntent.PlayEpisode -> {
                    repository.playEpisode(intent.episodeId)
                }
                is PodcastDetailIntent.EnqueueEpisode -> {
                    repository.playEpisode(intent.episodeId)
                }
                is PodcastDetailIntent.DownloadEpisode -> downloadEpisode(intent.episodeId)
            }
        }
    }

    private fun refreshFeed() {
        // Trigger the loading logic again to fetch the latest episodes
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        loadPodcastDetails()
    }

    private fun toggleSubscription() {
        viewModelScope.launch {
            val currentPodcast = _state.value.podcast ?: return@launch
            val currentEpisodes = _state.value.episodes

            if (_state.value.isSubscribed) {
                // Unsubscribe: Remove from Room
                repository.deletePodcastAndEpisodes(currentPodcast.id)
                _state.update { it.copy(isSubscribed = false) }
            } else {
                // Subscribe: Save to Room
                repository.savePodcastAndEpisodes(currentPodcast, currentEpisodes)
                _state.update { it.copy(isSubscribed = true) }
            }
        }
    }

    private fun playEpisode(episodeId: String) {
        // Find the episode they clicked on
        val episode = _state.value.episodes.find { it.id == episodeId } ?: return
        val podcast = _state.value.podcast

        // Convert the Room Entity into an ExoPlayer MediaItem
        val mediaItem = episode.toMediaItem(podcast)

        // Send it to the background service and play immediately
        mediaController?.setMediaItem(mediaItem)
        mediaController?.prepare()
        mediaController?.play()
    }

    private fun enqueueEpisode(episodeId: String) {
        val episode = _state.value.episodes.find { it.id == episodeId } ?: return
        val podcast = _state.value.podcast ?: return

        viewModelScope.launch {
            try {
                // 1. Save locally so the Queue's INNER JOIN can find it
                repository.savePodcastAndEpisodes(podcast, listOf(episode))
                repository.enqueueEpisode(episodeId)

                // 2. Add to Queue
                val mediaItem = episode.toMediaItem(podcast)
                mediaController?.addMediaItem(mediaItem)

                // 3. Print success to Logcat!
                android.util.Log.d("QueueDebug", "Successfully queued: ${episode.title}")
            } catch (e: Exception) {
                // 4. Print exact failure reason to Logcat!
                android.util.Log.e("QueueDebug", "FAILED to queue episode", e)
            }
        }
    }
    private fun downloadEpisode(episodeId: String) {
        // Architecture Next Step: Trigger WorkManager
    }

    override fun onCleared() {
        super.onCleared()
        // Always release the MediaController future to prevent memory leaks when navigating away
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
    }

    fun addToQueue(episodeId: String) {
        viewModelScope.launch {
            repository.playEpisode(episodeId)
        }
    }
}