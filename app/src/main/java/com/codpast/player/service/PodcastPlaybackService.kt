// File: com/codpast/player/service/PodcastPlaybackService.kt
package com.codpast.player.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.codpast.player.data.repository.PlaybackProgressManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PodcastPlaybackService : MediaLibraryService() {

    @Inject
    lateinit var progressManager: PlaybackProgressManager

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        setupPlayerListeners()
        startMemoryTicker()

        // MediaLibrarySession Tree setup omitted for brevity
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {}).build()
    }

    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    // Trigger: Playback transitions to PAUSED
                    progressManager.triggerImmediateFlush()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    // Trigger: Playback transitions to STOPPED or ENDED
                    progressManager.triggerImmediateFlush()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    // Trigger: Manual user SeekTo actions
                    progressManager.triggerImmediateFlush()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Trigger: Track change or queue skip (Also handled securely inside the Manager)
                progressManager.triggerImmediateFlush()
            }
        })
    }

    private fun startMemoryTicker() {
        // UI Ticker: Runs continuously while service is alive, emitting updates every 500ms
        serviceScope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    player.currentMediaItem?.mediaId?.let { activeId ->
                        progressManager.updateProgress(activeId, player.currentPosition)
                    }
                }
                delay(500L)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Trigger: User swiped the app away in recent apps
        progressManager.onTeardown()
    }

    override fun onDestroy() {
        // Trigger: Service is being destroyed by the system
        progressManager.onTeardown()

        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }
}