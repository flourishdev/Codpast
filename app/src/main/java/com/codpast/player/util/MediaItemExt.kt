package com.codpast.player.util

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity

/**
 * Single source of truth helper for building a fully populated Media3 MediaItem
 * containing MediaMetadata required for Android System Notification, Bluetooth AVRCP, and MiniPlayer.
 */
fun EpisodeEntity.toMediaItem(podcast: PodcastEntity? = null): MediaItem {
    val podcastTitle = podcast?.title?.takeIf { it.isNotBlank() } ?: "Podcast Episode"

    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(podcastTitle)
        .setAlbumTitle(podcastTitle)
        .setArtworkUri((imageUrl ?: podcast?.artworkUrl)?.takeIf { it.isNotEmpty() }?.toUri())
        .setIsPlayable(true)
        .build()

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(audioUrl)
        .setMediaMetadata(metadata)
        .build()
}