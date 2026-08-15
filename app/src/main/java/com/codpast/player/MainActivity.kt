package com.codpast.player

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.codpast.player.data.local.entity.PodcastEntity
import com.codpast.player.data.network.PodcastIndexApi
import com.codpast.player.data.repository.PodcastRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

// 1. This tells Hilt to inject dependencies into this Activity
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // 2. Hilt hands us our Network API and Database Repository automatically!
    @Inject
    lateinit var api: PodcastIndexApi

    @Inject
    lateinit var repository: PodcastRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("CODPAST_TEST", "🚀 App started! Running integration test...")

        // 3. Launch a background thread so we don't freeze the app
        CoroutineScope(Dispatchers.IO).launch {

            // --- TEST 1: THE NETWORK LAYER ---
            try {
                Log.d("CODPAST_TEST", "🌐 1. Testing Network (Calling Podcast Index API)...")
                val response = api.searchPodcasts("Android")
                Log.d("CODPAST_TEST", "✅ API Success! Found ${response.feeds.size} podcasts.")
            } catch (e: Exception) {
                // NOTE: Because our API Keys are "YOUR_API_KEY", this WILL throw an HTTP 401 Unauthorized error!
                // This actually proves Retrofit and OkHttp are working perfectly and talking to the server.
                Log.d("CODPAST_TEST", "⚠️ API Responded (Expected error due to dummy keys): ${e.message}")
            }

            // --- TEST 2: THE DATABASE LAYER ---
            try {
                Log.d("CODPAST_TEST", "💾 2. Testing Room Database...")
                val testPodcast = PodcastEntity(
                    id = "test_123",
                    title = "The Codpast Test Show",
                    description = "Testing our Room Database",
                    feedUrl = "https://test.com/feed.xml",
                    artworkUrl = "https://test.com/image.png",
                    isSubscribed = true
                )

                // Save it to the database
                repository.savePodcast(testPodcast)
                Log.d("CODPAST_TEST", "✅ Saved podcast to database!")

                // Read it back out using Kotlin Flow
                repository.getSubscribedPodcasts().collect { listOfPodcasts ->
                    Log.d("CODPAST_TEST", "✅ Read from database! Found ${listOfPodcasts.size} subscribed podcast.")
                    Log.d("CODPAST_TEST", "🎉 Podcast Title: ${listOfPodcasts.firstOrNull()?.title}")
                }
            } catch (e: Exception) {
                Log.e("CODPAST_TEST", "❌ Database Test failed!", e)
            }
        }
    }
}