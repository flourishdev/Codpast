package com.codpast.player.util

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity

/**
 * Single source of truth helper for building a fully populated Media3 MediaItem
 * containing MediaMetadata required for Android System Notification, MiniPlayer, and ListenScreen.
 */
fun EpisodeEntity.toMediaItem(podcast: PodcastEntity? = null): MediaItem {
    val podcastName = podcast?.title?.takeIf { it.isNotBlank() } ?: "Podcast Episode"

    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(podcastName)
        .setAlbumTitle(podcastName)
        .setArtworkUri(
            (imageUrl ?: podcast?.artworkUrl)?.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
        )
        .build()

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.parse(audioUrl))
        .setMediaMetadata(metadata)
        .build()
}