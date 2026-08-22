package com.codpast.player.di
import com.codpast.player.data.local.dao.PodcastDao

import android.content.Context
import com.codpast.player.data.repository.PlaybackProgressManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier


@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun providePlaybackProgressManager(
        podcastDao: PodcastDao,
        @ApplicationScope applicationScope: CoroutineScope
    ): PlaybackProgressManager {
        return PlaybackProgressManager(podcastDao, applicationScope)
    }
}