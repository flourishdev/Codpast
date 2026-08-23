package com.codpast.player.ui.mvi

import com.codpast.player.data.local.entity.QueueWithEpisode

object QueueContract {

    data class State(
        val queueItems: List<QueueWithEpisode> = emptyList(),
        val currentlyPlayingEpisodeId: String? = null,
        val isLoading: Boolean = false,
        val errorMessage: String? = null
    ) : UiState

    sealed class Intent : UserIntent {
        data class PlayEpisode(val episodeId: String) : Intent()
        data class RemoveFromQueue(val episodeId: String) : Intent()
        data class ReorderQueue(val fromIndex: Int, val toIndex: Int) : Intent()
        object ClearQueue : Intent()
    }

    sealed class Effect : UiEffect {
        data class ShowToast(val message: String) : Effect()
    }
}