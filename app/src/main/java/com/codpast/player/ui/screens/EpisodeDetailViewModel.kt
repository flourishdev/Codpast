package com.codpast.player.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity
import com.codpast.player.data.repository.PlaybackProgressManager
import com.codpast.player.data.repository.PodcastRepository
import com.codpast.player.ui.mvi.DownloadStatus as UiDownloadStatus
import com.codpast.player.data.local.entity.DownloadStatus as DbDownloadStatus
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
import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.codpast.player.service.PodcastPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import com.codpast.player.util.toMediaItem

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EpisodeDetailViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val progressManager: PlaybackProgressManager,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val episodeId: String = checkNotNull(savedStateHandle["episodeId"])
    private val feedUrl: String? = savedStateHandle["feedUrl"]

    private val _isPlaying = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    // Memory caches for unsubscribed previews
    private val previewEpisode = MutableStateFlow<EpisodeEntity?>(null)
    private val previewPodcast = MutableStateFlow<PodcastEntity?>(null)

    // Room DB streams for subscribed data
    private val dbEpisodeFlow = repository.getEpisodeById(episodeId)
    private val dbPodcastFlow = dbEpisodeFlow.flatMapLatest { episode ->
        if (episode != null) repository.getPodcastById(episode.podcastId) else flowOf(null)
    }

    // REAL-TIME ROOM DOWNLOAD FLOW COLLECTION
    private val downloadStatusFlow = repository.getDownloadForEpisode(episodeId).map { download ->
        when (download?.status) {
            DbDownloadStatus.DOWNLOADING -> UiDownloadStatus.DOWNLOADING
            DbDownloadStatus.COMPLETED -> UiDownloadStatus.DOWNLOADED
            else -> UiDownloadStatus.NOT_DOWNLOADED
        }
    }

    // MediaController connection to PodcastPlaybackService
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // Group streams to stay under the limit
    private val dbData = combine(dbEpisodeFlow, dbPodcastFlow) { ep, pod -> Pair(ep, pod) }
    private val preData = combine(previewEpisode, previewPodcast) { ep, pod -> Pair(ep, pod) }
    private val uiData = combine(_isPlaying, downloadStatusFlow, _errorMessage) { isPlaying, status, error ->
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
        initializeMediaController()
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

    private fun initializeMediaController() {
        val sessionToken = SessionToken(context, ComponentName(context, PodcastPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun onIntent(intent: EpisodeDetailIntent) {
        when (intent) {
            is EpisodeDetailIntent.TogglePlayPause -> {
                val controller = mediaController ?: return
                val currentEp = state.value.episode ?: return
                val currentPod = state.value.podcast

                if (controller.currentMediaItem?.mediaId == currentEp.id) {
                    if (controller.isPlaying) controller.pause() else controller.play()
                } else {
                    viewModelScope.launch {
                        if (currentPod != null) {
                            repository.savePodcastAndEpisodes(currentPod, listOf(currentEp))
                        }
                        repository.enqueueEpisode(currentEp.id)
                        controller.setMediaItem(currentEp.toMediaItem(currentPod))
                        controller.prepare()
                        controller.play()
                    }
                }
            }
            is EpisodeDetailIntent.SeekTo -> {
                mediaController?.seekTo(intent.positionMs)
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
                        // Persist offline first so Room entity references are valid
                        repository.savePodcastAndEpisodes(currentPodcast, listOf(currentEpisode))
                        // Dispatch WorkManager download worker
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
    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}