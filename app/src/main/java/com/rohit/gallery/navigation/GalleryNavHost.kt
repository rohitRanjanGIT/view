package com.rohit.gallery.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import com.rohit.gallery.core.ui.theme.GalleryMotion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rohit.gallery.core.ui.components.LocalAnimatedVisibilityScope
import com.rohit.gallery.core.ui.components.LocalSharedMediaId
import com.rohit.gallery.core.ui.components.LocalSharedTransitionScope
import com.rohit.gallery.core.ui.components.LocalViewerReturnTarget
import com.rohit.gallery.core.ui.components.ViewerReturnTarget
import com.rohit.gallery.feature.albums.AlbumDetailScreen
import com.rohit.gallery.feature.albums.AlbumsScreen
import com.rohit.gallery.feature.albums.EditAlbumsScreen
import com.rohit.gallery.feature.favorites.FavoritesScreen
import com.rohit.gallery.feature.settings.MenuScreen
import com.rohit.gallery.feature.trash.TrashScreen
import com.rohit.gallery.feature.timeline.TimelineScreen
import com.rohit.gallery.feature.viewer.ViewerScreen
import com.rohit.gallery.feature.viewer.prefetchViewerImage
import com.rohit.gallery.core.model.MediaItem
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import com.rohit.gallery.feature.videos.VideosScreen
import kotlinx.coroutines.launch

/** Height of the M3 navigation bar, without the system navigation-bar inset. */
private val NavigationBarHeight = 80.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryNavHost(
    externalStart: ViewerRoute? = null,
    onExitExternal: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var selectionActive by remember { mutableStateOf(false) }
    val showBottomBar = !selectionActive &&
        topLevelDestinations.any { currentDestination?.hasRoute(it.route::class) == true }

    // Content of top-level screens gets a constant bottom inset instead of Scaffold's, so the
    // grid does not resize when the bar disappears while the viewer opens.
    val contentBottomPadding = NavigationBarHeight +
        with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

    var sharedMediaId by remember { mutableStateOf<Long?>(null) }
    val returnTarget = remember { ViewerReturnTarget() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val windowSize = LocalWindowInfo.current.containerSize

    /** Opens the viewer at [item]: starts its decode right away, then tags the cell and navigates. */
    fun openViewer(item: MediaItem, route: ViewerRoute) {
        prefetchViewerImage(context, item, windowSize.width, windowSize.height)
        // Tag the grid cell first so the shared element exists on both sides.
        sharedMediaId = item.id
        scope.launch {
            androidx.compose.runtime.withFrameNanos { }
            navController.navigate(route)
        }
    }

    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalSharedMediaId provides sharedMediaId,
            LocalViewerReturnTarget provides returnTarget,
        ) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    // Opened by another app: only the viewer, no permission gate or timeline underneath.
                    startDestination = externalStart ?: PicturesRoute,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    topLevel<PicturesRoute> {
                        TimelineScreen(
                            onOpenMedia = { item -> openViewer(item, ViewerRoute(item.id)) },
                            onSelectionModeChange = { selectionActive = it },
                            contentBottomPadding = contentBottomPadding,
                        )
                    }
                    topLevel<AlbumsRoute> {
                        AlbumsScreen(
                            onOpenAlbum = { navController.navigate(AlbumDetailRoute(it.bucketId)) },
                            onEditAlbums = { navController.navigate(EditAlbumsRoute) },
                            onSelectionModeChange = { selectionActive = it },
                            contentBottomPadding = contentBottomPadding,
                        )
                    }
                    topLevel<EditAlbumsRoute> {
                        EditAlbumsScreen(onBack = { navController.popBackStack() })
                    }
                    topLevel<AlbumDetailRoute> {
                        AlbumDetailScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMedia = { item, bucketId, oldestFirst ->
                                openViewer(item, ViewerRoute(item.id, bucketId = bucketId, oldestFirst = oldestFirst))
                            },
                        )
                    }
                    topLevel<MenuRoute> {
                        MenuScreen(
                            onOpenFavorites = { navController.navigate(FavoritesRoute) },
                            onOpenVideos = { navController.navigate(VideosRoute) },
                            onOpenTrash = { navController.navigate(TrashRoute) },
                            modifier = Modifier.padding(bottom = contentBottomPadding),
                        )
                    }
                    topLevel<FavoritesRoute> {
                        FavoritesScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMedia = { item -> openViewer(item, ViewerRoute(item.id, favoritesOnly = true)) },
                        )
                    }
                    topLevel<VideosRoute> {
                        VideosScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMedia = { item -> openViewer(item, ViewerRoute(item.id, videosOnly = true)) },
                        )
                    }
                    topLevel<TrashRoute> {
                        TrashScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMedia = { item -> openViewer(item, ViewerRoute(item.id, trashed = true)) },
                        )
                    }
                    composable<ViewerRoute>(
                        enterTransition = { fadeIn(tween(GalleryMotion.FADE_MS)) },
                        exitTransition = { fadeOut(tween(GalleryMotion.FADE_MS)) },
                        popEnterTransition = { fadeIn(tween(GalleryMotion.FADE_MS)) },
                        popExitTransition = { fadeOut(tween(GalleryMotion.FADE_MS)) },
                    ) { entry ->
                        val external = entry.toRoute<ViewerRoute>().externalUri != null
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            ViewerScreen(
                                // The external viewer is the whole back stack: closing it returns to the caller.
                                onBack = { if (external) onExitExternal() else navController.popBackStack() },
                                onCurrentItemChanged = {
                                    sharedMediaId = it
                                    if (it != null) returnTarget.mediaId = it
                                },
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showBottomBar,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(tween(GalleryMotion.FADE_MS)),
                    exit = fadeOut(tween(GalleryMotion.FADE_MS)),
                ) {
                    NavigationBar {
                        topLevelDestinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentDestination?.hasRoute(dest.route::class) == true,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = null) },
                                label = { Text(dest.label) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A bottom-bar destination. The outgoing/incoming screen stays put (no fade) while the viewer
 * opens or closes, so the grid remains visible behind the shared-element flight.
 */
private inline fun <reified T : Any> androidx.navigation.NavGraphBuilder.topLevel(
    crossinline content: @Composable () -> Unit,
) {
    composable<T>(
        exitTransition = { if (targetsViewer()) ExitTransition.None else fadeOut(tween(GalleryMotion.FADE_MS)) },
        popEnterTransition = { if (initialIsViewer()) EnterTransition.None else fadeIn(tween(GalleryMotion.FADE_MS)) },
    ) {
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) { content() }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.targetsViewer() =
    targetState.destination.hasRoute<ViewerRoute>()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.initialIsViewer() =
    initialState.destination.hasRoute<ViewerRoute>()
