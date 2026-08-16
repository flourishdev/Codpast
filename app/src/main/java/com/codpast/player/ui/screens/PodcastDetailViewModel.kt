package com.codpast.player.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity
import com.codpast.player.data.repository.PodcastRepository
import com.codpast.player.ui.mvi.PodcastDetailIntent
import com.codpast.player.ui.mvi.PodcastDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.codpast.player.util.combine

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    private val repository: PodcastRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val podcastId: String? = savedStateHandle["podcastId"]
    private val feedUrl: String? = savedStateHandle["feedUrl"]

    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    // Memory caches used strictly for previewing unsubscribed podcasts
    private val previewPodcast = MutableStateFlow<PodcastEntity?>(null)
    private val previewEpisodes = MutableStateFlow<List<EpisodeEntity>>(emptyList())

    // Intelligently combine Room DB streams with in-memory Preview streams
    val state: StateFlow<PodcastDetailUiState> = combine(
        repository.getPodcastById(podcastId ?: ""),
        repository.getEpisodesForPodcast(podcastId ?: ""),
        previewPodcast,
        previewEpisodes,
        _isRefreshing,
        _errorMessage
    ) { dbPodcast, dbEpisodes, prePodcast, preEpisodes, isRefreshing, error ->

        val currentPodcast = dbPodcast ?: prePodcast
        val currentEpisodes = if (dbPodcast != null) dbEpisodes else preEpisodes

        PodcastDetailUiState(
            isLoading = currentPodcast == null && !isRefreshing,
            isRefreshing = isRefreshing,
            podcast = currentPodcast,
            episodes = currentEpisodes,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PodcastDetailUiState(isLoading = true)
    )

    init {
        if (podcastId != null) {
            refreshFeed()
        } else if (feedUrl != null) {
            loadPreview(feedUrl)
        } else {
            _errorMessage.value = "No podcast loaded."
        }
    }

    fun onIntent(intent: PodcastDetailIntent) {
        when (intent) {
            is PodcastDetailIntent.RefreshFeed -> {
                if (podcastId != null) refreshFeed() else loadPreview(feedUrl ?: "")
            }
            is PodcastDetailIntent.ToggleSubscription -> toggleSubscription()
            is PodcastDetailIntent.PlayEpisode -> playEpisode(intent.episodeId)
            is PodcastDetailIntent.EnqueueEpisode -> enqueueEpisode(intent.episodeId)
            is PodcastDetailIntent.DownloadEpisode -> downloadEpisode(intent.episodeId)
        }
    }

    private fun loadPreview(url: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val parsed = repository.parseRssUrl(url)
                if (parsed != null) {
                    previewPodcast.value = parsed
                    previewEpisodes.value = repository.fetchEpisodes(parsed.id, url)
                } else {
                    _errorMessage.value = "Failed to load podcast preview."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
            _isRefreshing.value = false
        }
    }

    private fun refreshFeed() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val currentId = podcastId ?: return@launch
            val podcast = repository.getPodcastById(currentId).firstOrNull()

            if (podcast != null) {
                try {
                    repository.syncEpisodes(podcast.id, podcast.feedUrl)
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to refresh feed."
                }
            }
            _isRefreshing.value = false
        }
    }

    private fun toggleSubscription() {
        viewModelScope.launch {
            val currentPodcast = state.value.podcast ?: return@launch
            val isCurrentlySubscribed = currentPodcast.isSubscribed
            val updatedPodcast = currentPodcast.copy(isSubscribed = !isCurrentlySubscribed)

            // Push memory into Room DB
            repository.savePodcast(updatedPodcast)

            // Dump the preview episodes into Room so they are permanently saved
            if (!isCurrentlySubscribed && state.value.episodes.isNotEmpty()) {
                repository.saveEpisodes(state.value.episodes)
            }
        }
    }

    private fun playEpisode(episodeId: String) {}
    private fun enqueueEpisode(episodeId: String) {}
    private fun downloadEpisode(episodeId: String) {}
}