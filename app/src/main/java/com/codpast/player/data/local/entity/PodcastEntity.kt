// File: com/codpast/player/data/local/entity/PodcastEntity.kt
package com.codpast.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val feedUrl: String,
    val artworkUrl: String,
    val isSubscribed: Boolean
)