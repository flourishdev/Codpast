package com.codpast.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codpast.player.ui.mvi.QueueIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Up Next") },
                actions = {
                    if (state.queueItems.isNotEmpty()) {
                        TextButton(onClick = { viewModel.onIntent(QueueIntent.ClearQueue) }) {
                            Text("Clear")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.queueItems.isEmpty()) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Your queue is empty.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize()
            ) {
                items(state.queueItems, key = { it.id }) { episode ->
                    ListItem(
                        // Add the clickable modifier to jump playback!
                        modifier = Modifier.clickable {
                            viewModel.onIntent(QueueIntent.PlayFromQueue(episode.id))
                        },
                        headlineContent = { Text(episode.title, maxLines = 1) },
                        supportingContent = { Text("Duration: ${episode.duration / 60000} min") },
                        trailingContent = {
                            IconButton(onClick = { viewModel.onIntent(QueueIntent.RemoveFromQueue(episode.id)) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Remove")
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}