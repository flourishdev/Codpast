package com.codpast.player.ui.mvi

import com.codpast.player.data.local.entity.DownloadEntity
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity

data class PodcastDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val podcast: PodcastEntity? = null,
    val episodes: List<EpisodeEntity> = emptyList(),
    val isSubscribed: Boolean = false,
    val queuedEpisodeIds: Set<String> = emptySet(),
    val downloadsMap: Map<String, DownloadEntity> = emptyMap(),
    val errorMessage: String? = null
) : UiState

sealed class PodcastDetailIntent : UserIntent {
    object RefreshFeed : PodcastDetailIntent()
    object ToggleSubscription : PodcastDetailIntent()
    data class PlayEpisode(val episodeId: String) : PodcastDetailIntent()
    data class EnqueueEpisode(val episodeId: String) : PodcastDetailIntent()
    data class DownloadEpisode(val episodeId: String) : PodcastDetailIntent()
}