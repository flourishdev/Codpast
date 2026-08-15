// File: com/codpast/player/di/NetworkModule.kt
package com.codpast.player.di

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

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // TODO in production: Move these keys to BuildConfig or local.properties
        val interceptor = PodcastIndexInterceptor(
            apiKey = "YOUR_API_KEY",
            apiSecret = "YOUR_API_SECRET"
        )

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.podcastindex.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providePodcastIndexApi(retrofit: Retrofit): PodcastIndexApi {
        return retrofit.create(PodcastIndexApi::class.java)
    }
}