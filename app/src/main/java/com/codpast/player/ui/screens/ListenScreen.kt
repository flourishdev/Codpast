package com.codpast.player.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.codpast.player.R
import com.codpast.player.ui.mvi.PlayerIntent
import androidx.compose.material.icons.filled.Pause

@Composable
fun ListenScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 1. Hero Artwork
        AsyncImage(
            model = state.currentEpisode?.imageUrl ?: state.currentPodcast?.artworkUrl,
            contentDescription = "Now Playing Artwork",
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_launcher_foreground),
            error = painterResource(id = R.drawable.ic_launcher_foreground),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 2. Title & Show Info
        Text(
            text = state.currentEpisode?.title ?: "No Episode Selected",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = state.currentPodcast?.title ?: "Select a podcast to start listening",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 3. Scrubber Section
        val currentSecs = state.currentPositionMs / 1000
        val durationSecs = state.durationMs / 1000

        Slider(
            value = if (durationSecs > 0) currentSecs.toFloat() / durationSecs.toFloat() else 0f,
            onValueChange = { percent ->
                val newPos = (percent * state.durationMs).toLong()
                viewModel.onIntent(PlayerIntent.SeekTo(newPos))
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = DateUtils.formatElapsedTime(currentSecs), style = MaterialTheme.typography.labelMedium)
            Text(text = "-${DateUtils.formatElapsedTime(durationSecs - currentSecs)}", style = MaterialTheme.typography.labelMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Playback Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed Selector Chip
            AssistChip(
                onClick = {
                    val nextSpeed = when(state.playbackSpeed) {
                        1.0f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 2.0f
                        2.0f -> 0.5f
                        else -> 1.0f
                    }
                    viewModel.onIntent(PlayerIntent.SetSpeed(nextSpeed))
                },
                label = { Text("${state.playbackSpeed}x") }
            )

            // Skip Back 10s
            IconButton(onClick = { viewModel.onIntent(PlayerIntent.SkipBackward()) }) {
                Icon(Icons.Default.FastRewind, contentDescription = "Skip Back 10s", modifier = Modifier.size(32.dp))
            }

            // Play/Pause (Using a large FAB for emphasis)
            FloatingActionButton(
                onClick = { viewModel.onIntent(PlayerIntent.TogglePlayPause) },
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(36.dp)
            ) {
                Icon(
                    // SWAP ICON BASED ON STATE
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp)
                )
            }

            // Skip Forward 30s
            IconButton(onClick = { viewModel.onIntent(PlayerIntent.SkipForward()) }) {
                Icon(Icons.Default.FastForward, contentDescription = "Skip Forward 30s", modifier = Modifier.size(32.dp))
            }

            // Next Track
            IconButton(
                onClick = { viewModel.onIntent(PlayerIntent.SkipToNext) },
                enabled = state.hasNextEpisode
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next Episode")
            }
        }
    }
}