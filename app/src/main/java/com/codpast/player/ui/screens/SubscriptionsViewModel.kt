package com.codpast.player.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codpast.player.data.repository.PodcastRepository
import com.codpast.player.ui.mvi.SubscriptionsEffect
import com.codpast.player.ui.mvi.SubscriptionsIntent
import com.codpast.player.ui.mvi.SubscriptionsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val repository: PodcastRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)

    // Automatically observe Room; any DB change updates the UI instantly
    val state: StateFlow<SubscriptionsUiState> = combine(
        _isLoading,
        repository.getSubscribedPodcasts()
    ) { loading, podcasts ->
        SubscriptionsUiState(
            isLoading = loading.also { if (it) _isLoading.value = false }, // Clear loading after first emit
            subscribedPodcasts = podcasts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SubscriptionsUiState()
    )

    private val _effect = MutableSharedFlow<SubscriptionsEffect>()
    val effect: SharedFlow<SubscriptionsEffect> = _effect.asSharedFlow()

    fun onIntent(intent: SubscriptionsIntent) {
        when (intent) {
            is SubscriptionsIntent.LoadSubscriptions -> { /* Handled automatically by combine flow */ }
            is SubscriptionsIntent.Unsubscribe -> unsubscribeFromPodcast(intent.podcastId)
            is SubscriptionsIntent.SelectPodcast -> navigateToDetail(intent.podcastId)
        }
    }

    private fun unsubscribeFromPodcast(podcastId: String) {
        viewModelScope.launch {
            // Provide a real implementation based on your repository
            // repository.deletePodcast(podcastId)
        }
    }

    private fun navigateToDetail(podcastId: String) {
        viewModelScope.launch {
            _effect.emit(SubscriptionsEffect.NavigateToDetail(podcastId))
        }
    }
}