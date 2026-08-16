package com.codpast.player.di

import com.codpast.player.BuildConfig
import com.codpast.player.data.network.PodcastIndexApi
import com.codpast.player.data.network.PodcastIndexInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // 1. Hilt builds the Interceptor here
    @Provides
    @Singleton
    fun providePodcastIndexInterceptor(): PodcastIndexInterceptor {
        return PodcastIndexInterceptor(
            apiKey = BuildConfig.PODCAST_API_KEY,
            apiSecret = BuildConfig.PODCAST_API_SECRET
        )
    }

    // 2. Hilt injects the Interceptor into the OkHttpClient here
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: PodcastIndexInterceptor
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun providePodcastIndexApi(okHttpClient: OkHttpClient): PodcastIndexApi {
        return Retrofit.Builder()
            .baseUrl("https://api.podcastindex.org/api/1.0/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PodcastIndexApi::class.java)
    }
}