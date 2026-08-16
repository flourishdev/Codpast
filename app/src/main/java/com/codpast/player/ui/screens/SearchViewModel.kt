package com.codpast.player.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codpast.player.data.local.entity.PodcastEntity
import com.codpast.player.data.network.PodcastIndexApi
import com.codpast.player.data.repository.PodcastRepository
import com.codpast.player.ui.mvi.PodcastSearchResult
import com.codpast.player.ui.mvi.SearchIntent
import com.codpast.player.ui.mvi.SearchUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val api: PodcastIndexApi
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())

    // Keystroke flow for debouncing
    private val searchQueryFlow = MutableStateFlow("")

    // Internal state holding raw API results
    private val _searchResults = MutableStateFlow<List<PodcastSearchResult>>(emptyList())

    // Combine raw API results with live Room database subscriptions to compute final UI State
    val state: StateFlow<SearchUiState> = combine(
        _state,
        _searchResults,
        repository.getSubscribedPodcasts()
    ) { currentState, apiResults, subscribedEntities ->
        val subscribedIds = subscribedEntities.map { it.id }.toSet()

        // Map the boolean flag on our UI models
        val mappedResults = apiResults.map { result ->
            result.copy(isSubscribed = subscribedIds.contains(result.id))
        }

        currentState.copy(results = mappedResults)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchUiState()
    )

    init {
        viewModelScope.launch {
            searchQueryFlow
                .debounce(500L)
                .distinctUntilChanged()
                .collect { query ->
                    executeSearch(query)
                }
        }
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> {
                _state.update { it.copy(query = intent.query, errorMessage = null) }
                searchQueryFlow.value = intent.query
            }
            is SearchIntent.ClearSearch -> {
                _state.update { it.copy(query = "", errorMessage = null) }
                searchQueryFlow.value = ""
                _searchResults.value = emptyList()
            }
            is SearchIntent.Subscribe -> handleSubscription(intent.podcast, isSubscribed = true)
            is SearchIntent.Unsubscribe -> handleSubscription(intent.podcast, isSubscribed = false)
        }
    }

    private suspend fun executeSearch(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        try {
            if (query.startsWith("http://") || query.startsWith("https://")) {
                // Route to RSS Fallback
                val parsedPodcast = repository.parseRssUrl(query)

                if (parsedPodcast != null) {
                    // Map the parsed entity to our UI Search Result model
                    _searchResults.value = listOf(
                        PodcastSearchResult(
                            id = parsedPodcast.id,
                            title = parsedPodcast.title,
                            author = parsedPodcast.description,
                            feedUrl = parsedPodcast.feedUrl,
                            artworkUrl = parsedPodcast.artworkUrl
                        )
                    )
                } else {
                    _searchResults.value = emptyList()
                    _state.update { it.copy(errorMessage = "Could not parse RSS feed") }
                }
                _state.update { it.copy(isLoading = false) }
            } else {
                // Standard API Search
                val response = api.searchPodcasts(query)

                // Map network response to our UI model
                val mapped = response.feeds.map { feed ->
                    PodcastSearchResult(
                        id = feed.id?.toString() ?: "",
                        title = feed.title ?: "Unknown Title",
                        author = feed.author ?: "Unknown Author",
                        feedUrl = feed.feedUrl ?: "",
                        artworkUrl = feed.artworkUrl ?: ""
                    )
                }
                _searchResults.value = mapped
                _state.update { it.copy(isLoading = false) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, errorMessage = "Failed to search: ${e.message}") }
        }
    }

    private fun handleSubscription(podcast: PodcastSearchResult, isSubscribed: Boolean) {
        viewModelScope.launch {
            val entity = PodcastEntity(
                id = podcast.id,
                title = podcast.title,
                description = podcast.author,
                feedUrl = podcast.feedUrl,
                artworkUrl = podcast.artworkUrl,
                isSubscribed = isSubscribed
            )
            // Save updates Room; the `combine` flow automatically reacts and updates the UI
            repository.savePodcast(entity)
        }
    }
}