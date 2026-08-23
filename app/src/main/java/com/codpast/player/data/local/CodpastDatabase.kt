// File: com/codpast/player/data/local/CodpastDatabase.kt
package com.codpast.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.codpast.player.data.local.dao.PodcastDao
import com.codpast.player.data.local.entity.DownloadEntity
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.data.local.entity.PodcastEntity
import com.codpast.player.data.local.entity.QueueEntity

@Database(
    entities = [
        PodcastEntity::class,
        EpisodeEntity::class,
        QueueEntity::class,
        DownloadEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class CodpastDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
}