package com.codpast.player.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.codpast.player.R
import com.codpast.player.ui.mvi.PlayerIntent
import android.content.res.Configuration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import com.codpast.player.ui.components.PlayPauseLoadingButton

@Composable
fun ListenScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCompactHeight = configuration.screenHeightDp < 360

    val scrollState = rememberScrollState()
    val scrollModifier = if (isCompactHeight || !isLandscape) {
        Modifier.verticalScroll(scrollState)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(scrollModifier)
    ) {
        if (isLandscape) {
            // Adaptive Landscape 2-Column Split Layout (50% / 50%)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column (50% width): Hero Artwork, Episode Title & Podcast Name
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AsyncImage(
                        model = state.currentEpisode?.imageUrl ?: state.currentPodcast?.artworkUrl,
                        contentDescription = "Now Playing Artwork",
                        contentScale = ContentScale.Crop,
                        placeholder = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                        error = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = state.currentEpisode?.title ?: "No Episode Selected",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.currentPodcast?.title ?: "Select a podcast to start listening",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Right Column (50% width): Scrubber, Speed Selector & Playback Controls
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
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
                        Text(text = "-${DateUtils.formatElapsedTime((durationSecs - currentSecs).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {
                                val nextSpeed = when (state.playbackSpeed) {
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

                        IconButton(onClick = { viewModel.onIntent(PlayerIntent.SkipBackward()) }) {
                            Icon(Icons.Default.FastRewind, contentDescription = "Skip Back 10s", modifier = Modifier.size(32.dp))
                        }

                        PlayPauseLoadingButton(
                            isPlaying = state.isPlaying,
                            isBuffering = state.isBuffering,
                            onClick = { viewModel.onIntent(PlayerIntent.TogglePlayPause) },
                            modifier = Modifier.size(64.dp)
                        )

                        IconButton(onClick = { viewModel.onIntent(PlayerIntent.SkipForward()) }) {
                            Icon(Icons.Default.FastForward, contentDescription = "Skip Forward 30s", modifier = Modifier.size(32.dp))
                        }

                        IconButton(
                            onClick = { viewModel.onIntent(PlayerIntent.SkipToNext) },
                            enabled = state.hasNextEpisode
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next Episode")
                        }
                    }
                }
            }
        } else {
            // Standard Portrait Vertical Layout
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
                    placeholder = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                    error = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
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
                    Text(text = "-${DateUtils.formatElapsedTime((durationSecs - currentSecs).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {
                            val nextSpeed = when (state.playbackSpeed) {
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

                    IconButton(onClick = { viewModel.onIntent(PlayerIntent.SkipBackward()) }) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Skip Back 10s", modifier = Modifier.size(32.dp))
                    }

                    PlayPauseLoadingButton(
                        isPlaying = state.isPlaying,
                        isBuffering = state.isBuffering,
                        onClick = { viewModel.onIntent(PlayerIntent.TogglePlayPause) },
                        modifier = Modifier.size(64.dp)
                    )

                    IconButton(onClick = { viewModel.onIntent(PlayerIntent.SkipForward()) }) {
                        Icon(Icons.Default.FastForward, contentDescription = "Skip Forward 30s", modifier = Modifier.size(32.dp))
                    }

                    IconButton(
                        onClick = { viewModel.onIntent(PlayerIntent.SkipToNext) },
                        enabled = state.hasNextEpisode
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next Episode")
                    }
                }
            }
        }
    }
}