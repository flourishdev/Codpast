package com.codpast.player.ui.mvi

import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.QueueWithEpisode

object QueueContract {
    data class State(
        val queueItems: List<QueueWithEpisode> = emptyList(),
        val currentlyPlayingEpisodeId: String? = null,
        val isLoading: Boolean = false
    ) : MviState

    sealed interface Intent : MviIntent {
        data class PlayEpisode(val episodeId: String) : Intent
        data class RemoveFromQueue(val episodeId: String) : Intent
        data class ReorderQueue(val fromIndex: Int, val toIndex: Int) : Intent
        data object ClearQueue : Intent
    }

    sealed interface Effect : MviEffect {
        data class ShowToast(val message: String) : Effect
    }
}