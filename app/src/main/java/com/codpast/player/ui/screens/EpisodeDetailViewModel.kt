package com.codpast.player.ui.screens
import com.codpast.player.data.local.entity.DownloadStatus as DbDownloadStatus

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
import kotlinx.coroutines.flow.map
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
    private val feedUrl: String? = savedStateHandle["feedUrl"]

    private val _errorMessage = MutableStateFlow<String?>(null)

    // Caches for previewing unsubscribed episodes/podcasts from RSS
    private val previewEpisode = MutableStateFlow<EpisodeEntity?>(null)
    private val previewPodcast = MutableStateFlow<PodcastEntity?>(null)

    // Reactive DB streams
    private val dbEpisodeFlow = repository.getEpisodeById(episodeId)
    private val dbPodcastFlow = dbEpisodeFlow.flatMapLatest { episode ->
        if (episode != null) repository.getPodcastById(episode.podcastId) else flowOf(null)
    }

    // Reactive playback and download state streams
    private val isPlayingFlow = progressManager.currentEpisodeId.map { activeId ->
        activeId == episodeId
    }

    private val downloadStatusFlow = repository.getDownloadForEpisode(episodeId).map { download ->
        when (download?.status) {
            DbDownloadStatus.DOWNLOADING -> DownloadStatus.DOWNLOADING
            DbDownloadStatus.COMPLETED -> DownloadStatus.DOWNLOADED
            else -> DownloadStatus.NOT_DOWNLOADED
        }
    }

    // Group flows to satisfy combine parameter limits
    private val dbData = combine(dbEpisodeFlow, dbPodcastFlow) { ep, pod -> Pair(ep, pod) }
    private val preData = combine(previewEpisode, previewPodcast) { ep, pod -> Pair(ep, pod) }
    private val uiData = combine(isPlayingFlow, downloadStatusFlow, _errorMessage) { isPlaying, status, error ->
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
            playbackPositionMs = if (ui.first) position else (currentEpisode?.playbackPosition ?: 0L),
            downloadStatus = ui.second,
            errorMessage = ui.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EpisodeDetailUiState(isLoading = true)
    )

    init {
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
                val currentEpisode = state.value.episode ?: return
                val currentPodcast = state.value.podcast ?: return

                viewModelScope.launch {
                    try {
                        // Persist to Room SQLite first so SSOT contains episode and podcast entities
                        repository.savePodcastAndEpisodes(currentPodcast, listOf(currentEpisode))
                        // Triggers queue insertion and notifies PlaybackProgressManager / PodcastPlaybackService
                        repository.playEpisode(currentEpisode.id)
                    } catch (e: Exception) {
                        _errorMessage.value = "Failed to play episode."
                    }
                }
            }
            is EpisodeDetailIntent.SeekTo -> {
                progressManager.updateProgress(episodeId, intent.positionMs)
            }
            is EpisodeDetailIntent.Enqueue -> {
                val currentEpisode = state.value.episode ?: return
                val currentPodcast = state.value.podcast ?: return

                viewModelScope.launch {
                    try {
                        repository.savePodcastAndEpisodes(currentPodcast, listOf(currentEpisode))
                        repository.enqueueEpisode(currentEpisode.id)
                    } catch (e: Exception) {
                        _errorMessage.value = "Failed to enqueue episode."
                    }
                }
            }
            is EpisodeDetailIntent.DownloadEpisode -> {
                val currentEpisode = state.value.episode ?: return
                val currentPodcast = state.value.podcast ?: return

                viewModelScope.launch {
                    try {
                        repository.savePodcastAndEpisodes(currentPodcast, listOf(currentEpisode))
                        repository.downloadEpisode(currentEpisode)
                    } catch (e: Exception) {
                        _errorMessage.value = "Failed to start download."
                    }
                }
            }
            is EpisodeDetailIntent.DeleteDownload -> {
                viewModelScope.launch {
                    try {
                        repository.deleteDownload(episodeId)
                    } catch (e: Exception) {
                        _errorMessage.value = "Failed to delete download."
                    }
                }
            }
        }
    }
}