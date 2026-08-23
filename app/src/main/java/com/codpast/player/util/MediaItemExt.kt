package com.codpast.player.util

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity

/**
 * Single source of truth helper for building a fully populated Media3 MediaItem
 * containing MediaMetadata required for Android System Notification, Bluetooth AVRCP, and MiniPlayer.
 * Supports both online HTTP streams and offline file:// URIs.
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

    // Safely parse URI scheme whether online (http/https) or local file (file://)
    val parsedMediaUri = if (audioUrl.startsWith("content://") || audioUrl.startsWith("file://") || audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) {
        Uri.parse(audioUrl)
    } else {
        Uri.parse("file://$audioUrl")
    }

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(parsedMediaUri)
        .setMediaMetadata(metadata)
        .build()
}