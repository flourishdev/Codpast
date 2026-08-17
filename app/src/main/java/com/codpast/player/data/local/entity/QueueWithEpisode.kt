package com.codpast.player.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class QueueWithEpisode(
    @Embedded val queueItem: QueueEntity,

    // This tells Room exactly how to link the Queue table to the Episodes table!
    @Relation(
        parentColumn = "episodeId",
        entityColumn = "id"
    )
    val episode: EpisodeEntity?
)