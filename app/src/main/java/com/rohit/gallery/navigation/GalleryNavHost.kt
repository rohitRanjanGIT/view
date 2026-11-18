package com.rohit.gallery.navigation

import kotlin.math.abs
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import com.rohit.gallery.core.ui.theme.GalleryMotion
import androidx.activity.BackEventCompat
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import com.rohit.gallery.core.ui.components.ViewerBackdrop
import com.rohit.gallery.core.ui.components.LocalViewerBackdrop
import com.rohit.gallery.feature.albums.AlbumDetailScreen
import com.rohit.gallery.feature.albums.AlbumsScreen
import com.rohit.gallery.feature.albums.EditAlbumsScreen
import com.rohit.gallery.feature.favorites.FavoritesScreen
import com.rohit.gallery.feature.search.SearchScreen
import com.rohit.gallery.feature.settings.MenuScreen
import com.rohit.gallery.feature.settings.SettingsScreen
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
    val backdrop = remember { ViewerBackdrop() }
    // Everything below the shared-element overlay, recorded as it draws, so opening the viewer can keep a
    // picture of the grid to show behind the photo during back and drag-down gestures.
    val screenLayer = rememberGraphicsLayer()
    LaunchedEffect(currentDestination) {
        // A full-screen bitmap: drop it as soon as the viewer is not showing.
        if (currentDestination?.hasRoute<ViewerRoute>() != true) backdrop.image = null
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val windowSize = LocalWindowInfo.current.containerSize

    /** Opens the viewer at [item]: starts its decode right away, then tags the cell and navigates. */
    fun openViewer(item: MediaItem, route: ViewerRoute) {
        prefetchViewerImage(context, item, windowSize.width, windowSize.height)
        // Tag the grid cell first so the shared element exists on both sides.
        sharedMediaId = item.id
        backdrop.takeCells()
        scope.launch { backdrop.image = runCatching { screenLayer.toImageBitmap() }.getOrNull() }
        scope.launch {
            androidx.compose.runtime.withFrameNanos { }
            navController.navigate(route)
        }
    }

    // Swiping sideways on a tab screen or the bottom bar moves one tab: left goes to the next, right to the previous.
    val currentTab = topLevelDestinations.indexOfFirst { currentDestination?.hasRoute(it.route::class) == true }
    val latestTab by rememberUpdatedState(currentTab)
    val latestShowBar by rememberUpdatedState(showBottomBar)
    fun swipeTab(direction: Int) {
        val from = latestTab
        if (from < 0) return
        val dest = topLevelDestinations.getOrNull(from + direction) ?: return
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val swipePx = with(LocalDensity.current) { TabSwipeDistance.toPx() }

    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalSharedMediaId provides sharedMediaId,
            LocalViewerReturnTarget provides returnTarget,
            LocalViewerBackdrop provides backdrop,
        ) {
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    screenLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(screenLayer)
                },
            ) {
                NavHost(
                    navController = navController,
                    // Opened by another app: only the viewer, no permission gate or timeline underneath.
                    startDestination = externalStart ?: PicturesRoute,
                    modifier = Modifier.fillMaxSize()
                        // Only on the three tab screens, and not while selecting.
                        .tabSwipe(swipePx, enabled = { latestShowBar }, onSwipe = ::swipeTab),
                    // Back gesture: the screen slides away with the finger (towards the side it was swiped from)
                    // while the one underneath stays still. The viewer handles its own back gesture (see ViewerScreen).
                    predictivePopEnterTransition = { EnterTransition.None },
                    predictivePopExitTransition = { swipeEdge ->
                        slideOutHorizontally { width -> if (swipeEdge == BackEventCompat.EDGE_LEFT) width else -width }
                    },
                ) {
                    topLevel<PicturesRoute> {
                        TimelineScreen(
                            onOpenMedia = { item -> openViewer(item, ViewerRoute(item.id)) },
                            onOpenSearch = { navController.navigate(SearchRoute) },
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
                    topLevel<SearchRoute> {
                        SearchScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMedia = { item, query -> openViewer(item, ViewerRoute(item.id, searchQuery = query)) },
                        )
                    }
                    topLevel<SettingsRoute> {
                        SettingsScreen(onBack = { navController.popBackStack() })
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
                            onOpenSettings = { navController.navigate(SettingsRoute) },
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
                                onOpenAlbum = { bucketId -> navController.navigate(AlbumDetailRoute(bucketId)) },
                                onOpenFavorites = { navController.navigate(FavoritesRoute) },
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
                    NavigationBar(Modifier.tabSwipe(swipePx, enabled = { true }, onSwipe = ::swipeTab)) {
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
 * A one-finger sideways swipe of at least [distancePx] calls [onSwipe] with +1 (swiped left: next tab) or -1.
 * Runs after the children (normal pass), so anything that already claimed the drag, like vertical scrolling,
 * drag-to-select or a pinch, wins and the tab stays.
 */
private fun Modifier.tabSwipe(distancePx: Float, enabled: () -> Boolean, onSwipe: (Int) -> Unit): Modifier =
    pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (enabled() && abs(total) > distancePx) onSwipe(if (total < 0) 1 else -1) },
            onHorizontalDrag = { change, amount ->
                if (enabled()) {
                    change.consume()
                    total += amount
                }
            },
        )
    }

/** How far a sideways swipe must travel to change tab. */
private val TabSwipeDistance = 48.dp

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
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
            // Opaque, so a screen sliding away during the back gesture never shows the one beneath through it.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.targetsViewer() =
    targetState.destination.hasRoute<ViewerRoute>()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.initialIsViewer() =
    initialState.destination.hasRoute<ViewerRoute>()
