package com.codpast.player.util

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity

/**
 * Converts local Room EpisodeEntity and optional PodcastEntity to a pure stable Media3 MediaItem.
 * Uses 100% stable AndroidX Media3 APIs (no @UnstableApi required).
 */
fun EpisodeEntity.toMediaItem(podcast: PodcastEntity?): MediaItem {
    // Resolve artwork with fallback for blank strings
    val artworkUrl = imageUrl.takeIf { it.isNotBlank() }
        ?: podcast?.artworkUrl?.takeIf { it.isNotBlank() }

    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(podcast?.title ?: "Podcast")
        .setAlbumTitle(podcast?.title ?: "Podcast")
        .setDescription(description)
        .setArtworkUri(artworkUrl?.let { it.toUri() })
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .build()

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(audioUrl)
        .setMediaMetadata(metadata)
        .build()
}