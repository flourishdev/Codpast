// File: com/codpast/player/data/local/entity/QueueEntity.kt
package com.codpast.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey val episodeId: String,
    val position: Int
)