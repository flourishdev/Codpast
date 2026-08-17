package com.codpast.player.ui.screens

import androidx.lifecycle.ViewModel
import com.codpast.player.ui.mvi.QueueIntent
import com.codpast.player.ui.mvi.QueueUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor() : ViewModel() {

    // Architecture Next Step: Observe List<QueueEntity> from Room Database
    private val _state = MutableStateFlow(QueueUiState())
    val state: StateFlow<QueueUiState> = _state.asStateFlow()

    fun onIntent(intent: QueueIntent) {
        when (intent) {
            is QueueIntent.PlayFromQueue -> {
                // Architecture Next Step: Pipe to MediaController & Remove from Queue DB
            }
            is QueueIntent.RemoveFromQueue -> {
                // Architecture Next Step: Delete from Queue DB
            }
            is QueueIntent.ClearQueue -> {
                // Architecture Next Step: Clear Queue DB table
            }
        }
    }
}