// File: com/codpast/player/data/local/entity/EpisodeEntity.kt
package com.codpast.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.MessageDigest

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val podcastId: String,
    val title: String,
    val description: String = "",
    val audioUrl: String,
    val imageUrl: String,
    val duration: Long,
    val playbackPosition: Long,
    val isCompleted: Boolean,
    val publishedAt: Long
) {
    companion object {
        fun generateId(guid: String?, podcastId: String, audioUrl: String): String {
            return if (!guid.isNullOrBlank()) {
                guid
            } else {
                val fallbackString = podcastId + audioUrl
                val digest = MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(fallbackString.toByteArray(Charsets.UTF_8))
                hashBytes.joinToString("") { "%02x".format(it) }
            }
        }
    }
}