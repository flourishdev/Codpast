package com.codpast.player.ui.mvi

import com.codpast.player.data.local.entity.PodcastEntity

// UI representation of a search result
data class PodcastSearchResult(
    val id: String,
    val title: String,
    val author: String,
    val feedUrl: String,
    val artworkUrl: String,
    val isSubscribed: Boolean = false
)

// State snapshot
data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<PodcastSearchResult> = emptyList(),
    val errorMessage: String? = null
) : UiState

// User Actions
sealed class SearchIntent : UserIntent {
    data class QueryChanged(val query: String) : SearchIntent()
    object ClearSearch : SearchIntent()
    data class Subscribe(val podcast: PodcastSearchResult) : SearchIntent()
    data class Unsubscribe(val podcast: PodcastSearchResult) : SearchIntent()
}