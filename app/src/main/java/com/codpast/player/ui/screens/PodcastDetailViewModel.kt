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
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import com.codpast.player.data.local.entity.DownloadStatus as DbDownloadStatus


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
        observeSubscriptionStatus()
        observeDownloads()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            repository.getAllDownloads().collect { downloads ->
                val downloadMap = downloads.associateBy { it.episodeId }
                _state.update { it.copy(downloadsMap = downloadMap) }
            }
        }
    }
    private fun observeSubscriptionStatus() {
        val targetId = podcastId ?: feedUrl
        if (!targetId.isNullOrBlank()) {
            viewModelScope.launch {
                repository.getPodcastById(targetId).collect { dbPodcast ->
                    if (dbPodcast != null) {
                        _state.update { currentState ->
                            currentState.copy(
                                podcast = dbPodcast,
                                isSubscribed = dbPodcast.isSubscribed
                            )
                        }
                    }
                }
            }
        }
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

                // 1. Check local DB snapshot to see if we are already subscribed
                val targetId = podcastId ?: feedUrl
                var localPodcast: com.codpast.player.data.local.entity.PodcastEntity? = null

                if (!targetId.isNullOrBlank()) {
                    localPodcast = repository.getPodcastByIdSnapshot(targetId)
                    if (localPodcast != null && localPodcast.isSubscribed) {
                        isCurrentlySubscribed = true
                        if (targetFeedUrl.isNullOrBlank()) {
                            targetFeedUrl = localPodcast.feedUrl
                        }
                    }
                }

                // 2. Fetch the absolute latest episodes from the Network
                if (!targetFeedUrl.isNullOrBlank()) {
                    val remotePodcast = repository.parseRssUrl(targetFeedUrl)
                    if (remotePodcast != null) {
                        val remoteEpisodes = repository.fetchEpisodes(remotePodcast.id, targetFeedUrl)

                        // Preserve subscription state on the remote podcast object
                        val finalPodcast = remotePodcast.copy(
                            id = targetId ?: remotePodcast.id,
                            isSubscribed = isCurrentlySubscribed
                        )

                        _state.update {
                            it.copy(
                                isLoading = false,
                                podcast = finalPodcast,
                                episodes = remoteEpisodes,
                                isSubscribed = isCurrentlySubscribed
                            )
                        }

                        // Automatically update DB if already subscribed
                        if (isCurrentlySubscribed) {
                            repository.savePodcastAndEpisodes(finalPodcast, remoteEpisodes)
                        }
                    } else {
                        throw Exception("Failed to load podcast from feed.")
                    }
                } else if (localPodcast != null) {
                    // 3. Offline Fallback: Load local DB episodes
                    val localEpisodes = repository.getEpisodesByPodcastId(localPodcast.id).firstOrNull() ?: emptyList()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            podcast = localPodcast,
                            episodes = localEpisodes,
                            isSubscribed = localPodcast.isSubscribed
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false, errorMessage = "No podcast ID or Feed URL provided.") }
                }

            } catch (e: Exception) {
                // 4. Error Fallback: Load local DB if available
                val targetId = podcastId ?: feedUrl
                if (!targetId.isNullOrBlank()) {
                    val localPodcast = repository.getPodcastByIdSnapshot(targetId)
                    val localEpisodes = repository.getEpisodesByPodcastId(targetId).firstOrNull() ?: emptyList()

                    if (localPodcast != null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                podcast = localPodcast,
                                episodes = localEpisodes,
                                isSubscribed = localPodcast.isSubscribed,
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
                is PodcastDetailIntent.ToggleSubscription -> {
                    val currentPodcast = _state.value.podcast
                    if (currentPodcast != null) {
                        repository.toggleSubscription(currentPodcast)

                        // Toggle local memory state so Compose recomposes instantly
                        val newSubStatus = !_state.value.isSubscribed
                        _state.update {
                            it.copy(
                                isSubscribed = newSubStatus,
                                podcast = currentPodcast.copy(isSubscribed = newSubStatus)
                            )
                        }
                    }
                }
                is PodcastDetailIntent.PlayEpisode -> {
                    repository.playEpisode(intent.episodeId)
                }
                is PodcastDetailIntent.EnqueueEpisode -> {
                    repository.enqueueEpisode(intent.episodeId)
                }
                is PodcastDetailIntent.DownloadEpisode -> {
                    val episode = _state.value.episodes.find { it.id == intent.episodeId }
                    val podcast = _state.value.podcast
                    if (episode != null && podcast != null) {
                        val currentDownload = _state.value.downloadsMap[intent.episodeId]
                        if (currentDownload?.status == DbDownloadStatus.COMPLETED) {
                            repository.deleteDownload(intent.episodeId)
                        } else if (currentDownload?.status != DbDownloadStatus.DOWNLOADING) {
                            // Save offline first to satisfy SSOT entity references
                            repository.savePodcastAndEpisodes(podcast, listOf(episode))
                            // Dispatch immediate download write + WorkManager
                            repository.downloadEpisode(episode)
                        }
                    }
                }
            }
        }
    }

    private fun refreshFeed() {
        // Trigger the loading logic again to fetch the latest episodes
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        loadPodcastDetails()
    }

    fun toggleSubscription() {
        val currentPodcast = _state.value.podcast ?: return
        val currentEpisodes = _state.value.episodes

        viewModelScope.launch {
            if (_state.value.isSubscribed) {
                repository.deletePodcastAndEpisodes(currentPodcast.id)
                _state.update { it.copy(isSubscribed = false) }
            } else {
                val subscribedPodcast = currentPodcast.copy(isSubscribed = true)
                repository.savePodcastAndEpisodes(subscribedPodcast, currentEpisodes)
                _state.update { it.copy(isSubscribed = true, podcast = subscribedPodcast) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Always release the MediaController future to prevent memory leaks when navigating away
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
    }
}