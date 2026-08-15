// File: com/codpast/player/data/local/entity/DownloadEntity.kt
package com.codpast.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val episodeId: String,
    val localFilePath: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isComplete: Boolean
)