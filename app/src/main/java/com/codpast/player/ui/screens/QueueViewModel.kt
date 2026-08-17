package com.codpast.player.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codpast.player.data.repository.PodcastRepository
import com.codpast.player.ui.mvi.QueueIntent
import com.codpast.player.ui.mvi.QueueUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: PodcastRepository
) : ViewModel() {

    // Architecture Next Step: Observe List<QueueEntity> from Room Database
    private val _state = MutableStateFlow(QueueUiState())
    val state: StateFlow<QueueUiState> = _state.asStateFlow()

    init {
        observeQueue()
    }

    private fun observeQueue() {
        viewModelScope.launch {
            // Continuously observe the queue table for any changes
            repository.getQueueEpisodes().collectLatest { queuedEpisodes ->
                _state.update {
                    it.copy(
                        // Make sure this matches the property name in your QueueUiState (e.g., queue, episodes, or queueEpisodes)
                        queueItems = queuedEpisodes
                    )
                }
            }
        }
    }

    fun onIntent(intent: QueueIntent) {
        when (intent) {
            is QueueIntent.PlayFromQueue -> {
                // Architecture Next Step: Pipe to MediaController
            }
            is QueueIntent.RemoveFromQueue -> {
                viewModelScope.launch { repository.removeFromQueue(intent.episodeId) }
            }
            is QueueIntent.ClearQueue -> {
                viewModelScope.launch { repository.clearQueue() }
            }
        }
    }
}