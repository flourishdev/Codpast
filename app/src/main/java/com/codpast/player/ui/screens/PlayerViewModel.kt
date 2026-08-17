package com.codpast.player.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codpast.player.ui.mvi.PlayerIntent
import com.codpast.player.ui.mvi.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.TogglePlayPause -> {
                _state.update { it.copy(isPlaying = !it.isPlaying) }
                // Architecture Next Step: Pipe to MediaController
            }
            is PlayerIntent.SeekTo -> {
                _state.update { it.copy(currentPositionMs = intent.positionMs) }
                // Architecture Next Step: Pipe to MediaController
            }
            is PlayerIntent.SkipForward -> {
                val newPos = (_state.value.currentPositionMs + intent.ms).coerceAtMost(_state.value.durationMs)
                _state.update { it.copy(currentPositionMs = newPos) }
            }
            is PlayerIntent.SkipBackward -> {
                val newPos = (_state.value.currentPositionMs - intent.ms).coerceAtLeast(0L)
                _state.update { it.copy(currentPositionMs = newPos) }
            }
            is PlayerIntent.SetSpeed -> {
                _state.update { it.copy(playbackSpeed = intent.speed) }
                // Architecture Next Step: Pipe to MediaController
            }
            is PlayerIntent.SkipToNext -> {
                // Architecture Next Step: Pipe to MediaController
            }
        }
    }
}