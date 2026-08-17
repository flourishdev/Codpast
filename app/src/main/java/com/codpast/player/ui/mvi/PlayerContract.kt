package com.codpast.player.ui.mvi

import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity

data class PlayerUiState(
    val currentEpisode: EpisodeEntity? = null,
    val currentPodcast: PodcastEntity? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val hasNextEpisode: Boolean = false
) : UiState

sealed class PlayerIntent : UserIntent {
    object TogglePlayPause : PlayerIntent()
    data class SeekTo(val positionMs: Long) : PlayerIntent()
    data class SkipForward(val ms: Long = 30000L) : PlayerIntent()
    data class SkipBackward(val ms: Long = 10000L) : PlayerIntent()
    data class SetSpeed(val speed: Float) : PlayerIntent()
    object SkipToNext : PlayerIntent()
}