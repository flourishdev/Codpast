// File: com/codpast/player/data/network/PodcastIndexInterceptor.kt
package com.codpast.player.data.network

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import java.util.Locale

class PodcastIndexInterceptor(
    private val apiKey: String,
    private val apiSecret: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // 1. Get the current UNIX time in seconds
        val unixTime = (System.currentTimeMillis() / 1000L).toString()

        // 2. Combine our keys and the time
        val dataToHash = apiKey + apiSecret + unixTime

        // 3. Generate the SHA-1 hash
        val digest = MessageDigest.getInstance("SHA-1")
        val hashBytes = digest.digest(dataToHash.toByteArray(Charsets.UTF_8))
        val authorizationHash = hashBytes.joinToString("") { "%02x".format(it) }.lowercase(Locale.ROOT)

        // 4. Attach them to the headers
        val request = chain.request().newBuilder()
            .addHeader("User-Agent", "CodpastPlayer/1.0.0")
            .addHeader("X-Auth-Date", unixTime)
            .addHeader("X-Auth-Key", apiKey)
            .addHeader("Authorization", authorizationHash)
            .build()

        return chain.proceed(request)
    }
}