package com.codpast.player.ui.mvi

import com.codpast.player.data.local.entity.EpisodeEntity

data class QueueUiState(
    val isLoading: Boolean = false,
    val queueItems: List<EpisodeEntity> = emptyList(), // Later, join this with QueueEntity for sorting
    val errorMessage: String? = null
) : UiState

sealed class QueueIntent : UserIntent {
    data class PlayFromQueue(val episodeId: String) : QueueIntent()
    data class RemoveFromQueue(val episodeId: String) : QueueIntent()
    object ClearQueue : QueueIntent()
}