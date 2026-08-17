package com.codpast.player.ui.screens

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.codpast.player.data.repository.PodcastRepository
import com.codpast.player.service.PodcastPlaybackService
import com.codpast.player.ui.mvi.PodcastDetailIntent
import com.codpast.player.ui.mvi.PodcastDetailUiState
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

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    private val repository: PodcastRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val podcastId: String? = savedStateHandle["podcastId"]
    private val feedUrl: String? = savedStateHandle["feedUrl"]

    private val _state = MutableStateFlow(PodcastDetailUiState(isLoading = true))
    val state: StateFlow<PodcastDetailUiState> = _state.asStateFlow()

    // The bridge to our background audio service
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    init {
        loadPodcastDetails()
        initializeController()
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

    private fun loadPodcastDetails() {
        viewModelScope.launch {
            if (feedUrl != null) {
                // 1. Unsubscribed Preview: Fetch fresh from the RSS Network Feed
                try {
                    val podcast = repository.parseRssUrl(feedUrl)
                    if (podcast != null) {
                        val episodes = repository.fetchEpisodes(podcast.id, feedUrl)
                        _state.update {
                            it.copy(
                                isLoading = false,
                                podcast = podcast,
                                episodes = episodes,
                                isSubscribed = false
                            )
                        }
                    } else {
                        _state.update { it.copy(isLoading = false, errorMessage = "Failed to parse podcast.") }
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
            } else if (podcastId != null) {
                // 2. Subscribed: Observe continuous updates directly from Room Database
                repository.getPodcastById(podcastId).combine(repository.getEpisodesByPodcastId(podcastId)) { podcast, episodes ->
                    Pair(podcast, episodes)
                }.collectLatest { (podcast, episodes) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            podcast = podcast,
                            episodes = episodes,
                            isSubscribed = true
                        )
                    }
                }
            }
        }
    }

    fun onIntent(intent: PodcastDetailIntent) {
        when (intent) {
            is PodcastDetailIntent.ToggleSubscription -> toggleSubscription()
            is PodcastDetailIntent.PlayEpisode -> playEpisode(intent.episodeId)
            is PodcastDetailIntent.EnqueueEpisode -> enqueueEpisode(intent.episodeId)
            is PodcastDetailIntent.DownloadEpisode -> downloadEpisode(intent.episodeId)
            is PodcastDetailIntent.RefreshFeed -> refreshFeed()
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
        viewModelScope.launch {
            repository.enqueueEpisode(episodeId)
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
}