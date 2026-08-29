package com.codpast.player.ui.navigation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codpast.player.ui.components.MiniPlayerBar
import com.codpast.player.ui.screens.EpisodeDetailScreen
import com.codpast.player.ui.screens.ListenScreen
import com.codpast.player.ui.screens.PodcastDetailScreen
import com.codpast.player.ui.screens.QueueScreen
import com.codpast.player.ui.screens.QueueViewModel
import com.codpast.player.ui.screens.SearchScreen
import com.codpast.player.ui.screens.SubscriptionsScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    if (isLandscape) {
        // Adaptive Landscape Layout: Left-Docked NavigationRail + Content Area
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Spacer(modifier = Modifier.weight(1f))
                bottomTabs.forEach { tab ->
                    NavigationRailItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
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
                                val encodedUrl = URLEncoder.encode(
                                    feedUrl,
                                    StandardCharsets.UTF_8.toString()
                                )
                                navController.navigate("podcast_detail?feedUrl=$encodedUrl")
                            }
                        )
                    }

                    composable(BottomRoute.Subscriptions.route) {
                        SubscriptionsScreen(
                            onNavigateToDetail = { podcastId ->
                                val encodedId = URLEncoder.encode(
                                    podcastId,
                                    StandardCharsets.UTF_8.toString()
                                )
                                navController.navigate("podcast_detail?podcastId=$encodedId")
                            }
                        )
                    }

                    composable(
                        route = "podcast_detail?podcastId={podcastId}&feedUrl={feedUrl}",
                        arguments = listOf(
                            navArgument("podcastId") {
                                type = NavType.StringType; nullable = true
                            },
                            navArgument("feedUrl") {
                                type = NavType.StringType; nullable = true
                            }
                        )
                    ) {
                        PodcastDetailScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEpisode = { episodeId, feedUrl ->
                                val encodedId = URLEncoder.encode(
                                    episodeId,
                                    StandardCharsets.UTF_8.toString()
                                )
                                if (feedUrl != null) {
                                    val encodedFeed = URLEncoder.encode(
                                        feedUrl,
                                        StandardCharsets.UTF_8.toString()
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
                            navArgument("episodeId") {
                                type = NavType.StringType
                            },
                            navArgument("feedUrl") {
                                type = NavType.StringType; nullable = true
                            }
                        )
                    ) {
                        EpisodeDetailScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }

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
    } else {
        // Standard Portrait Layout with Bottom NavigationBar
        Scaffold(
            bottomBar = {
                NavigationBar {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
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
                                val encodedUrl = URLEncoder.encode(
                                    feedUrl,
                                    StandardCharsets.UTF_8.toString()
                                )
                                navController.navigate("podcast_detail?feedUrl=$encodedUrl")
                            }
                        )
                    }

                    composable(BottomRoute.Subscriptions.route) {
                        SubscriptionsScreen(
                            onNavigateToDetail = { podcastId ->
                                val encodedId = URLEncoder.encode(
                                    podcastId,
                                    StandardCharsets.UTF_8.toString()
                                )
                                navController.navigate("podcast_detail?podcastId=$encodedId")
                            }
                        )
                    }

                    composable(
                        route = "podcast_detail?podcastId={podcastId}&feedUrl={feedUrl}",
                        arguments = listOf(
                            navArgument("podcastId") {
                                type = NavType.StringType; nullable = true
                            },
                            navArgument("feedUrl") {
                                type = NavType.StringType; nullable = true
                            }
                        )
                    ) {
                        PodcastDetailScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEpisode = { episodeId, feedUrl ->
                                val encodedId = URLEncoder.encode(
                                    episodeId,
                                    StandardCharsets.UTF_8.toString()
                                )
                                if (feedUrl != null) {
                                    val encodedFeed = URLEncoder.encode(
                                        feedUrl,
                                        StandardCharsets.UTF_8.toString()
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
                            navArgument("episodeId") {
                                type = NavType.StringType
                            },
                            navArgument("feedUrl") {
                                type = NavType.StringType; nullable = true
                            }
                        )
                    ) {
                        EpisodeDetailScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }

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
}