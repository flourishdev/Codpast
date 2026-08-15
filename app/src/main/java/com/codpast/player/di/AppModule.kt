// File: com/codpast/player/di/AppModule.kt
package com.codpast.player.di

import com.codpast.player.data.local.dao.PodcastDao
import com.codpast.player.data.repository.PodcastRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        // A SupervisorJob ensures that if one background task fails, it doesn't crash the whole app scope.
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun providePodcastRepository(podcastDao: PodcastDao): PodcastRepository {
        return PodcastRepository(podcastDao)
    }
}