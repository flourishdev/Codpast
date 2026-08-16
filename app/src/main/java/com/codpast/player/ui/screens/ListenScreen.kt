package com.codpast.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codpast.player.ui.mvi.UiEffect
import com.codpast.player.ui.mvi.UiState
import com.codpast.player.ui.mvi.UserIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// --- MVI Contracts for Listen Screen ---
data class ListenState(val isPlaying: Boolean = false, val currentTitle: String = "No Podcast Loaded") : UiState
sealed class ListenIntent : UserIntent {
    object PlayPauseClicked : ListenIntent()
}
sealed class ListenEffect : UiEffect

// --- Hilt ViewModel ---
@HiltViewModel
class ListenViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = _state.asStateFlow()

    fun onIntent(intent: ListenIntent) {
        when (intent) {
            is ListenIntent.PlayPauseClicked -> {
                _state.value = _state.value.copy(isPlaying = !_state.value.isPlaying)
            }
        }
    }
}

// --- Compose Screen ---
@Composable
fun ListenScreen(viewModel: ListenViewModel = hiltViewModel()) {
    // Safely collect state lifecycle-aware
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Listen Screen - ${state.currentTitle} (Playing: ${state.isPlaying})")
    }
}