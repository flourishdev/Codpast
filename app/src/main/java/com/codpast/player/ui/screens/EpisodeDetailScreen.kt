package com.codpast.player.ui.screens
import com.codpast.player.R

import android.text.util.Linkify
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.codpast.player.ui.mvi.DownloadStatus
import com.codpast.player.ui.mvi.EpisodeDetailIntent
import com.codpast.player.ui.mvi.QueuePosition
import android.text.format.DateUtils
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailScreen(
    viewModel: EpisodeDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val textColor = LocalContentColor.current.toArgb()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.podcast?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Open overflow menu */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.episode != null) {
                val episode = state.episode!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // 1. Header Section
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = episode.imageUrl.ifBlank { state.podcast?.artworkUrl ?: ""},
                            contentDescription = "Episode Artwork",
                            contentScale = ContentScale.Crop,
                            placeholder = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                            error = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                            modifier = Modifier.size(100.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            val dateString = DateUtils.getRelativeTimeSpanString(episode.publishedAt).toString()
                            Text(text = dateString, style = MaterialTheme.typography.labelMedium)
                            Text(text = episode.title, style = MaterialTheme.typography.titleLarge)
                            if (episode.duration > 0L) {
                                val minutes = episode.duration / 60000
                                Text(text = "$minutes min", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 2. Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.onIntent(EpisodeDetailIntent.TogglePlayPause) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.isPlaying) "Pause" else "Play")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalIconButton(onClick = { viewModel.onIntent(EpisodeDetailIntent.Enqueue(QueuePosition.LAST)) }) {
                            Icon(Icons.Default.Add, contentDescription = "Enqueue")
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        // Adaptive Download Button with Progress Indicator
                        FilledTonalIconButton(
                            onClick = {
                                when (state.downloadStatus) {
                                    DownloadStatus.NOT_DOWNLOADED -> {
                                        viewModel.onIntent(EpisodeDetailIntent.DownloadEpisode)
                                    }
                                    DownloadStatus.DOWNLOADED -> {
                                        viewModel.onIntent(EpisodeDetailIntent.DeleteDownload)
                                    }
                                    else -> {
                                        // Queued or Downloading: Do nothing on tap or handle cancellation
                                    }
                                }
                            }
                        ) {
                            when (state.downloadStatus) {
                                DownloadStatus.NOT_DOWNLOADED -> {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download Episode"
                                    )
                                }
                                DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                DownloadStatus.DOWNLOADED -> {
                                    Icon(
                                        imageVector = Icons.Default.DownloadDone,
                                        contentDescription = "Downloaded",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download Episode"
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3. Show Notes Body using AndroidView to parse HTML safely
                    Text(text = "Show Notes", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { context ->
                            TextView(context).apply {
                                autoLinkMask = Linkify.WEB_URLS
                                linksClickable = true
                                setTextColor(textColor)
                                textSize = 16f
                            }
                        },
                        update = { textView ->
                            textView.text = HtmlCompat.fromHtml(
                                episode.description.ifBlank { "No show notes provided." },
                                HtmlCompat.FROM_HTML_MODE_COMPACT
                            )
                        }
                    )
                }
            }
        }
    }
}