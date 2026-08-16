package com.codpast.player.ui.mvi

import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity

enum class DownloadStatus {
    NOT_DOWNLOADED, QUEUED, DOWNLOADING, DOWNLOADED
}

enum class QueuePosition {
    NEXT, LAST
}

data class EpisodeDetailUiState(
    val isLoading: Boolean = true,
    val episode: EpisodeEntity? = null,
    val podcast: PodcastEntity? = null,
    val isPlaying: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val errorMessage: String? = null
) : UiState

sealed class EpisodeDetailIntent : UserIntent {
    object TogglePlayPause : EpisodeDetailIntent()
    data class SeekTo(val positionMs: Long) : EpisodeDetailIntent()
    data class Enqueue(val position: QueuePosition) : EpisodeDetailIntent()
    object DownloadEpisode : EpisodeDetailIntent()
    object DeleteDownload : EpisodeDetailIntent()
}