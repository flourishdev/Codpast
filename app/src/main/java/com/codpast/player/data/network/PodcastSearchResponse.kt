// File: com/codpast/player/data/network/PodcastSearchResponse.kt
package com.codpast.player.data.network

import com.google.gson.annotations.SerializedName

data class PodcastSearchResponse(
    @SerializedName("status") val status: String,
    @SerializedName("feeds") val feeds: List<PodcastNetworkDto>
)

data class PodcastNetworkDto(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("url") val feedUrl: String,
    @SerializedName("image") val artworkUrl: String
)