package com.codpast.player.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codpast.player.data.local.entity.QueueWithEpisode
import com.codpast.player.data.repository.PlaybackProgressManager
import com.codpast.player.data.repository.PodcastRepository
import com.codpast.player.ui.mvi.QueueContract
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val playbackProgressManager: PlaybackProgressManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueueContract.State())
    val uiState: StateFlow<QueueContract.State> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<QueueContract.Effect>()
    val effect: SharedFlow<QueueContract.Effect> = _effect.asSharedFlow()

    init {
        observeQueueAndPlayback()
    }

    private fun observeQueueAndPlayback() {
        viewModelScope.launch {
            combine(
                repository.getQueue(),
                playbackProgressManager.currentEpisodeId
            ) { queueItems: List<QueueWithEpisode>, currentId: String? ->
                Pair(queueItems, currentId)
            }.collect { (queueItems, currentId) ->
                _uiState.update { currentState ->
                    currentState.copy(
                        queueItems = queueItems,
                        currentlyPlayingEpisodeId = currentId
                    )
                }
            }
        }
    }

    fun handleIntent(intent: QueueContract.Intent) {
        viewModelScope.launch {
            when (intent) {
                is QueueContract.Intent.PlayEpisode -> {
                    repository.playEpisode(intent.episodeId)
                }
                is QueueContract.Intent.RemoveFromQueue -> {
                    repository.removeFromQueue(intent.episodeId)
                }
                is QueueContract.Intent.ReorderQueue -> {
                    repository.reorderQueue(intent.fromIndex, intent.toIndex)
                }
                QueueContract.Intent.ClearQueue -> {
                    repository.clearQueue()
                }
            }
        }
    }
}