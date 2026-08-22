package com.codpast.player.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.codpast.player.ui.screens.EpisodeDetailScreen
import com.codpast.player.ui.screens.ListenScreen
import com.codpast.player.ui.screens.PodcastDetailScreen
import com.codpast.player.ui.screens.QueueScreen
import com.codpast.player.ui.screens.QueueViewModel
import com.codpast.player.ui.screens.SearchScreen
import com.codpast.player.ui.screens.SubscriptionsScreen
import com.codpast.player.ui.components.MiniPlayerBar

sealed class BottomRoute(val route: String, val title: String, val icon: ImageVector) {
    object Listen : BottomRoute("listen", "Listen", Icons.Default.PlayArrow)
    object Queue : BottomRoute("queue", "Queue", Icons.Default.List)
    object Subscriptions : BottomRoute("subscriptions", "Follows", Icons.Default.Star)
    object Search : BottomRoute("search", "Search", Icons.Default.Search)
}

@Composable
fun AppNavigationHost() {
    val navController = rememberNavController()
    val bottomTabs = listOf(
        BottomRoute.Listen,
        BottomRoute.Queue,
        BottomRoute.Subscriptions,
        BottomRoute.Search
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                bottomTabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // 1. The Column wraps everything!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 2. NavHost is INSIDE the Column, so weight(1f) works perfectly.
            NavHost(
                navController = navController,
                startDestination = BottomRoute.Listen.route,
                modifier = Modifier.weight(1f)
            ) {
                composable(BottomRoute.Listen.route) { ListenScreen() }
                composable(BottomRoute.Queue.route) {
                    val queueViewModel: QueueViewModel = hiltViewModel()
                    QueueScreen(viewModel = queueViewModel)
                }

                composable(BottomRoute.Search.route) {
                    SearchScreen(
                        onNavigateToDetail = { feedUrl ->
                            val encodedUrl = java.net.URLEncoder.encode(
                                feedUrl,
                                java.nio.charset.StandardCharsets.UTF_8.toString()
                            )
                            navController.navigate("podcast_detail?feedUrl=$encodedUrl")
                        }
                    )
                }

                composable(BottomRoute.Subscriptions.route) {
                    SubscriptionsScreen(
                        onNavigateToDetail = { podcastId ->
                            // URL-Encode the ID so Compose Navigation doesn't mangle it!
                            val encodedId = java.net.URLEncoder.encode(
                                podcastId,
                                java.nio.charset.StandardCharsets.UTF_8.toString()
                            )
                            navController.navigate("podcast_detail?podcastId=$encodedId")
                        }
                    )
                }

                composable(
                    route = "podcast_detail?podcastId={podcastId}&feedUrl={feedUrl}",
                    arguments = listOf(
                        androidx.navigation.navArgument("podcastId") {
                            type = androidx.navigation.NavType.StringType; nullable = true
                        },
                        androidx.navigation.navArgument("feedUrl") {
                            type = androidx.navigation.NavType.StringType; nullable = true
                        }
                    )
                ) {
                    PodcastDetailScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToEpisode = { episodeId, feedUrl ->
                            val encodedId = java.net.URLEncoder.encode(
                                episodeId,
                                java.nio.charset.StandardCharsets.UTF_8.toString()
                            )
                            if (feedUrl != null) {
                                val encodedFeed = java.net.URLEncoder.encode(
                                    feedUrl,
                                    java.nio.charset.StandardCharsets.UTF_8.toString()
                                )
                                navController.navigate("episode_detail/$encodedId?feedUrl=$encodedFeed")
                            } else {
                                navController.navigate("episode_detail/$encodedId")
                            }
                        }
                    )
                }

                composable(
                    route = "episode_detail/{episodeId}?feedUrl={feedUrl}",
                    arguments = listOf(
                        androidx.navigation.navArgument("episodeId") {
                            type = androidx.navigation.NavType.StringType
                        },
                        androidx.navigation.navArgument("feedUrl") {
                            type = androidx.navigation.NavType.StringType; nullable = true
                        }
                    )
                ) {
                    EpisodeDetailScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            // 3. MiniPlayerBar sits directly below the NavHost, still inside the Column!
            MiniPlayerBar(
                onNavigateToListen = {
                    navController.navigate(BottomRoute.Listen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}