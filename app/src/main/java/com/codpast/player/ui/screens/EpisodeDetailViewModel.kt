package com.codpast.player.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity
import com.codpast.player.data.repository.PlaybackProgressManager
import com.codpast.player.data.repository.PodcastRepository
import com.codpast.player.ui.mvi.DownloadStatus
import com.codpast.player.ui.mvi.EpisodeDetailIntent
import com.codpast.player.ui.mvi.EpisodeDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EpisodeDetailViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val progressManager: PlaybackProgressManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val episodeId: String = checkNotNull(savedStateHandle["episodeId"])
    private val feedUrl: String? = savedStateHandle["feedUrl"] // Grab the feedUrl

    private val _isPlaying = MutableStateFlow(false)
    private val _downloadStatus = MutableStateFlow(DownloadStatus.NOT_DOWNLOADED)
    private val _errorMessage = MutableStateFlow<String?>(null)

    // Memory caches for unsubscribed previews
    private val previewEpisode = MutableStateFlow<EpisodeEntity?>(null)
    private val previewPodcast = MutableStateFlow<PodcastEntity?>(null)

    // Room DB streams for subscribed data
    private val dbEpisodeFlow = repository.getEpisodeById(episodeId)
    private val dbPodcastFlow = dbEpisodeFlow.flatMapLatest { episode ->
        if (episode != null) repository.getPodcastById(episode.podcastId) else flowOf(null)
    }

    // Group streams to stay under the limit
    private val dbData = combine(dbEpisodeFlow, dbPodcastFlow) { ep, pod -> Pair(ep, pod) }
    private val preData = combine(previewEpisode, previewPodcast) { ep, pod -> Pair(ep, pod) }
    private val uiData = combine(_isPlaying, _downloadStatus, _errorMessage) { isPlaying, status, error ->
        Triple(isPlaying, status, error)
    }

    val state: StateFlow<EpisodeDetailUiState> = combine(
        dbData,
        preData,
        uiData,
        progressManager.uiPosition
    ) { db, pre, ui, position ->

        val currentEpisode = db.first ?: pre.first
        val currentPodcast = db.second ?: pre.second

        EpisodeDetailUiState(
            isLoading = currentEpisode == null,
            episode = currentEpisode,
            podcast = currentPodcast,
            isPlaying = ui.first,
            playbackPositionMs = position,
            downloadStatus = ui.second,
            errorMessage = ui.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EpisodeDetailUiState(isLoading = true)
    )

    init {
        // If we have a feedUrl, fetch the preview!
        if (feedUrl != null) {
            viewModelScope.launch {
                try {
                    val parsedPodcast = repository.parseRssUrl(feedUrl)
                    previewPodcast.value = parsedPodcast
                    if (parsedPodcast != null) {
                        val episodes = repository.fetchEpisodes(parsedPodcast.id, feedUrl)
                        previewEpisode.value = episodes.find { it.id == episodeId }
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to load preview."
                }
            }
        }
    }

    fun onIntent(intent: EpisodeDetailIntent) {
        when (intent) {
            is EpisodeDetailIntent.TogglePlayPause -> {
                _isPlaying.value = !_isPlaying.value
            }
            is EpisodeDetailIntent.SeekTo -> {
                progressManager.updateProgress(episodeId, intent.positionMs)
            }
            is EpisodeDetailIntent.Enqueue -> {
                val currentEpisode = state.value.episode ?: return
                val currentPodcast = state.value.podcast ?: return

                viewModelScope.launch {
                    try {
                        // Save offline first, then enqueue
                        repository.savePodcastAndEpisodes(currentPodcast, listOf(currentEpisode))
                        repository.enqueueEpisode(currentEpisode.id)
                        android.util.Log.d("QueueDebug", "Enqueued from EpisodeDetail: ${currentEpisode.title}")
                    } catch (e: Exception) {
                        android.util.Log.e("QueueDebug", "Failed to enqueue", e)
                    }
                }
            }
            is EpisodeDetailIntent.DownloadEpisode -> {
                _downloadStatus.value = DownloadStatus.QUEUED
            }
            is EpisodeDetailIntent.DeleteDownload -> {
                _downloadStatus.value = DownloadStatus.NOT_DOWNLOADED
            }
        }
    }
}