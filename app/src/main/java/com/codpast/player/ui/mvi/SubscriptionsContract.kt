package com.codpast.player.ui.mvi

import com.codpast.player.data.local.entity.PodcastEntity

data class SubscriptionsUiState(
    val isLoading: Boolean = true,
    val subscribedPodcasts: List<PodcastEntity> = emptyList()
) : UiState

sealed class SubscriptionsIntent : UserIntent {
    object LoadSubscriptions : SubscriptionsIntent()
    data class Unsubscribe(val podcastId: String) : SubscriptionsIntent()
    data class SelectPodcast(val podcastId: String) : SubscriptionsIntent()
}

sealed class SubscriptionsEffect : UiEffect {
    data class NavigateToDetail(val podcastId: String) : SubscriptionsEffect()
}