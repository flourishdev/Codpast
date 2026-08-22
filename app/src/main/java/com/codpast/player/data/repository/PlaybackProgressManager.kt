// File: com/codpast/player/data/repository/PlaybackProgressManager.kt
package com.codpast.player.data.repository

import com.codpast.player.data.local.dao.PodcastDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackProgressManager @Inject constructor(
    private val podcastDao: PodcastDao,
    private val applicationScope: CoroutineScope
) {
    // UI Memory StateFlow (Collected by ViewModels for 500ms scrubber updates)
    private val _uiPosition = MutableStateFlow(0L)
    val uiPosition: StateFlow<Long> = _uiPosition.asStateFlow()

    // Thread-safe memory tracking
    private val activeEpisodeId = AtomicReference<String?>(null)
    private val memoryPosition = AtomicLong(0L)
    private val lastFlushedPosition = AtomicLong(-1L)

    private var periodicSaveJob: Job? = null

    private val _currentEpisodeId = MutableStateFlow<String?>(null)
    val currentEpisodeId: StateFlow<String?> = _currentEpisodeId.asStateFlow()

    fun setCurrentEpisode(episodeId: String?) {
        _currentEpisodeId.value = episodeId
    }

    @Synchronized
    fun updateProgress(episodeId: String, positionMs: Long) {
        val currentActive = activeEpisodeId.get()

        // Trigger: Track change immediate flush
        if (currentActive != null && currentActive != episodeId) {
            flushImmediateSync()
        }

        activeEpisodeId.set(episodeId)
        memoryPosition.set(positionMs)
        _uiPosition.value = positionMs

        // Start the 5000ms disk throttle timer if it isn't running
        if (periodicSaveJob == null || periodicSaveJob?.isActive != true) {
            periodicSaveJob = applicationScope.launch(Dispatchers.IO) {
                while (isActive) {
                    delay(5000L)
                    flushInternal()
                }
            }
        }
    }

    /**
     * Bypasses the 5000ms throttle and executes an async database write immediately.
     * Used for Pause, Seek, and Stop triggers.
     */
    fun triggerImmediateFlush() {
        applicationScope.launch(Dispatchers.IO) {
            flushInternal()
        }
    }

    /**
     * Halts the background loop and executes a strictly synchronous blocking database write.
     * Used exclusively for Service teardown to guarantee data is saved before process death.
     */
    fun onTeardown() {
        periodicSaveJob?.cancel()
        flushImmediateSync()
    }

    private suspend fun flushInternal() {
        val episodeId = activeEpisodeId.get() ?: return
        val position = memoryPosition.get()
        val lastSaved = lastFlushedPosition.get()

        if (position != lastSaved) {
            podcastDao.updatePlaybackPosition(episodeId, position)
            lastFlushedPosition.set(position)
        }
    }

    private fun flushImmediateSync() {
        val episodeId = activeEpisodeId.get() ?: return
        val position = memoryPosition.get()
        val lastSaved = lastFlushedPosition.get()

        if (position != lastSaved) {
            runBlocking(Dispatchers.IO) {
                podcastDao.updatePlaybackPosition(episodeId, position)
            }
            lastFlushedPosition.set(position)
        }
    }
}