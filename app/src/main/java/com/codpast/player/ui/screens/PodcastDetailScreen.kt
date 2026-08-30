package com.codpast.player.ui.screens
import com.codpast.player.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.codpast.player.data.local.entity.EpisodeEntity
import com.codpast.player.ui.mvi.PodcastDetailIntent
import com.codpast.player.data.local.entity.DownloadEntity
import com.codpast.player.data.local.entity.DownloadStatus
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.DownloadDone


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    viewModel: PodcastDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEpisode: (String, String?) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.podcast?.title ?: "Loading...") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.errorMessage != null -> {
                    Text(text = state.errorMessage!!, modifier = Modifier.align(Alignment.Center))
                }
                state.podcast != null -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {

                        // Header
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = state.podcast?.artworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    placeholder = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                                    error = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                                    modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                val isSubscribed = state.podcast?.isSubscribed == true

                                if (isSubscribed) {
                                    FilledTonalButton(
                                        onClick = { viewModel.onIntent(PodcastDetailIntent.ToggleSubscription) },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Unsubscribe")
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.onIntent(PodcastDetailIntent.ToggleSubscription) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Subscribe")
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = state.podcast?.description ?: "",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // Episode List
                        items(state.episodes) { episode ->
                            val download = state.downloadsMap[episode.id]
                            EpisodeRowItem(
                                episode = episode,
                                isQueued = state.queuedEpisodeIds.contains(episode.id),
                                download = download,
                                onClick = { onNavigateToEpisode(episode.id, state.podcast?.feedUrl) },
                                onPlay = { viewModel.onIntent(PodcastDetailIntent.PlayEpisode(episode.id)) },
                                onEnqueue = { viewModel.onIntent(PodcastDetailIntent.EnqueueEpisode(episode.id)) },
                                onDownload = { viewModel.onIntent(PodcastDetailIntent.DownloadEpisode(episode.id)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeRowItem(
    episode: EpisodeEntity,
    isQueued: Boolean,
    download: DownloadEntity?,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 1. Format your Long timestamp into a readable date string
        val dateString = DateUtils.getRelativeTimeSpanString(episode.publishedAt).toString()
        Text(text = dateString, style = MaterialTheme.typography.labelSmall)

        Text(
            text = episode.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 2. Calculate progress float from your Long position/duration
        if (episode.playbackPosition > 0L && episode.duration > 0L) {
            val progress = episode.playbackPosition.toFloat() / episode.duration.toFloat()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                Text("Play", modifier = Modifier.padding(start = 4.dp))
            }
            Row {
                IconButton(onClick = onEnqueue) {
                    Icon(
                        imageVector = if (isQueued) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = if (isQueued) "In Queue" else "Enqueue",
                        tint = if (isQueued) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }

                // Dynamic Download State Icon
                when (download?.status) {
                    DownloadStatus.DOWNLOADING -> {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { (download.progress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        IconButton(onClick = onDownload) {
                            Icon(
                                imageVector = Icons.Default.DownloadDone,
                                contentDescription = "Delete Download",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    else -> {
                        IconButton(onClick = onDownload) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download Episode"
                            )
                        }
                    }
                }
            }
        }
    }
}