// File: com/codpast/player/di/DatabaseModule.kt
package com.codpast.player.di

import android.content.Context
import androidx.room.Room
import com.codpast.player.data.local.CodpastDatabase
import com.codpast.player.data.local.dao.PodcastDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideCodpastDatabase(@ApplicationContext context: Context): CodpastDatabase {
        return Room.databaseBuilder(
            context,
            CodpastDatabase::class.java,
            "codpast_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun providePodcastDao(database: CodpastDatabase): PodcastDao {
        return database.podcastDao()
    }
}