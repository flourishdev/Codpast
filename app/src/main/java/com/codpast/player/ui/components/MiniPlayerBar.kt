package com.codpast.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.codpast.player.R
import com.codpast.player.ui.mvi.PlayerIntent
import com.codpast.player.ui.screens.PlayerViewModel

@Composable
fun MiniPlayerBar(
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateToListen: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Only show the mini-player if an episode is actually loaded
    val currentEpisode = state.currentEpisode ?: return
    val currentPodcast = state.currentPodcast

    // Resolve artwork URL with fallback for blank episode image strings
    val artworkModel = currentEpisode.imageUrl.takeIf { it.isNotBlank() }
        ?: currentPodcast?.artworkUrl?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onNavigateToListen() }
    ) {
        // Thin top-edge progress indicator for active playback position
        val progress = if (state.durationMs > 0L) {
            (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = artworkModel,
                contentDescription = "Artwork",
                contentScale = ContentScale.Crop,
                placeholder = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                error = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentEpisode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentPodcast?.title ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            PlayPauseLoadingButton(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                onClick = { viewModel.onIntent(PlayerIntent.TogglePlayPause) }
            )
        }
    }
}