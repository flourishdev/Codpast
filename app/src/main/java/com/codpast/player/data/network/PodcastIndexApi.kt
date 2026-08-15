// File: com/codpast/player/data/network/PodcastIndexApi.kt
package com.codpast.player.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface PodcastIndexApi {

    @GET("api/1.0/search/byterm")
    suspend fun searchPodcasts(
        @Query("q") query: String
    ): PodcastSearchResponse

}