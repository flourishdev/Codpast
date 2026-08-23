package com.codpast.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val episodeId: String,
    val podcastId: String,
    val localPath: String,
    val progress: Int = 0,
    val status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val downloadedAt: Long = System.currentTimeMillis()
)