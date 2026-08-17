package com.codpast.player.util

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity

fun EpisodeEntity.toMediaItem(podcast: PodcastEntity?): MediaItem {
    // 1. Build the metadata for the Android Lock Screen & Notification Tray
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(podcast?.title ?: "Unknown Podcast")
        .setArtworkUri((imageUrl.ifBlank { podcast?.artworkUrl ?: "" }).toUri())
        .build()

    // 2. Build the actual playable item with the audio URL
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(audioUrl)
        .setMediaMetadata(metadata)
        .build()
}