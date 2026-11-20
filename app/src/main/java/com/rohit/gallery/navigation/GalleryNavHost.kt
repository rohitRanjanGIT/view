package com.rohit.gallery.navigation

import androidx.compose.foundation.layout.navigationBarsPadding
import dev.chrisbanes.haze.hazeSource
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.Icons
import com.rohit.gallery.core.ui.components.swipeBack
import com.rohit.gallery.core.ui.components.FloatingTabAction
import com.rohit.gallery.core.ui.components.frostedChrome
import com.rohit.gallery.core.ui.components.rememberChromeHaze
import com.rohit.gallery.core.ui.components.LocalChromeHaze
import androidx.compose.foundation.pager.rememberPagerState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import com.rohit.gallery.core.ui.theme.GalleryMotion
import androidx.activity.BackEventCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rohit.gallery.core.ui.components.FloatingTab
import com.rohit.gallery.core.ui.components.FloatingTabBar
import com.rohit.gallery.core.ui.components.LocalSharedPhoto
import com.rohit.gallery.core.ui.components.LocalUndoBar
import com.rohit.gallery.core.ui.components.UndoBarHost
import com.rohit.gallery.core.ui.components.UndoBarState
import com.rohit.gallery.core.ui.components.LocalSharedTransitionScope
import com.rohit.gallery.feature.albums.AlbumDetailScreen
import com.rohit.gallery.feature.albums.AlbumsScreen
import com.rohit.gallery.feature.albums.EditAlbumsScreen
import com.rohit.gallery.feature.albums.NewAlbumScreen
import com.rohit.gallery.feature.albums.PickForAlbumScreen
import com.rohit.gallery.feature.albums.UserAlbumScreen
import com.rohit.gallery.feature.favorites.FavoritesScreen
import com.rohit.gallery.feature.search.SearchScreen
import com.rohit.gallery.feature.settings.SettingsScreen
import com.rohit.gallery.feature.trash.TrashScreen
import com.rohit.gallery.feature.trash.RestoreRepairEffect
import com.rohit.gallery.feature.timeline.TimelineScreen
import com.rohit.gallery.feature.vault.VaultScreen
import com.rohit.gallery.feature.viewer.ViewerScreen
import com.rohit.gallery.feature.viewer.prefetchViewerImage
import com.rohit.gallery.feature.viewer.rememberViewerViewModelFactory
import com.rohit.gallery.core.model.MediaItem
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import com.rohit.gallery.feature.videos.VideosScreen
import kotlinx.coroutines.launch

/** Height of the M3 navigation bar, without the system navigation-bar inset. */
// Room the content keeps above the bottom edge: the floating capsule (64 dp), its margin, and some air above it.
private val NavigationBarHeight = 92.dp

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
    // The Pictures viewer is a layer inside the home screen, so the bar has to be told when it is open.
    var homeViewerOpen by remember { mutableStateOf(false) }
    val showBottomBar = !selectionActive && !homeViewerOpen && currentDestination?.hasRoute<HomeRoute>() == true
    // Which tab is showing; kept here so it survives opening the viewer and other screens.
    val homePager = rememberPagerState { homeTabs.size }

    // Content of top-level screens gets a constant bottom inset instead of Scaffold's, so the
    // grid does not resize when the bar disappears while the viewer opens.
    val contentBottomPadding = NavigationBarHeight +
        with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

    val scope = rememberCoroutineScope()
    // The bottom bar is frosted glass over the tabs, which scroll on underneath it.
    val navHaze = rememberChromeHaze()
    // "Moved to trash · Undo", shown above the tab bar on any screen.
    val undoBar = remember { UndoBarState() }
    val context = LocalContext.current
    val windowSize = LocalWindowInfo.current.containerSize

    /** Opens [host]'s viewer at [item]: starts its decode right away, then tags the cell and shows the viewer. */
    fun openViewer(host: ViewerHost, item: MediaItem, route: ViewerRoute) {
        if (host.route != null && !host.closing) return
        // A tap while the last photo is still flying home: land it now and open the new one straight away.
        if (host.closing) host.finishClose()
        prefetchViewerImage(context, item, windowSize.width, windowSize.height)
        // Tag the grid cell first so the shared element exists on both sides.
        host.prepare(item.id)
        scope.launch {
            androidx.compose.runtime.withFrameNanos { }
            host.open(route)
        }
    }

    // Photos back from Trash get the date and favourite mark they had (Android can lose them on the way).
    RestoreRepairEffect()

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this, LocalUndoBar provides undoBar) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    // Opened by another app: only the viewer, no permission gate or timeline underneath.
                    startDestination = externalStart ?: HomeRoute,
                    modifier = Modifier.fillMaxSize(),
                    // Back gesture, iOS-style stack: the screen slides off with the finger at full size (towards the side it
                    // was swiped from), while the one underneath slides in from a third of the way over, brightening (see
                    // BackStack). Both are linear, so the screen stays exactly under the finger. The viewer has its own.
                    predictivePopEnterTransition = { swipeEdge ->
                        slideInHorizontally(tween(BACK_MS, easing = LinearEasing)) { width -> if (swipeEdge == BackEventCompat.EDGE_LEFT) -width / 3 else width / 3 }
                    },
                    predictivePopExitTransition = { swipeEdge ->
                        slideOutHorizontally(tween(BACK_MS, easing = LinearEasing)) { width -> if (swipeEdge == BackEventCompat.EDGE_LEFT) width else -width }
                    },
                ) {
                    topLevel<HomeRoute>(navController) { host ->
                        DisposableEffect(host.viewerShowing) {
                            homeViewerOpen = host.viewerShowing
                            onDispose { }
                        }
                        HomePager(
                            state = homePager,
                            userScrollEnabled = !selectionActive,
                            // Settings has no tab or button: swipe left past the edge of Albums and hold 0.6 s.
                            modifier = Modifier
                                .hazeSource(navHaze)
                                .holdPastLastPage(
                                    enabled = { homePager.currentPage == ALBUMS_PAGE && homePager.currentPageOffsetFraction == 0f && !selectionActive },
                                    onHold = { navController.navigate(SettingsRoute) },
                                ),
                        ) { page ->
                            when (page) {
                                0 -> TimelineScreen(
                                    onOpenMedia = { item -> openViewer(host, item, ViewerRoute(item.id)) },
                                    onOpenSearch = { navController.navigate(SearchRoute) },
                                    onSelectionModeChange = { selectionActive = it },
                                    contentBottomPadding = contentBottomPadding,
                                )
                                ALBUMS_PAGE -> AlbumsScreen(
                                    onOpenAlbum = { navController.navigate(AlbumDetailRoute(it.bucketId)) },
                                    onOpenUserAlbum = { navController.navigate(UserAlbumRoute(it)) },
                                    // The hidden way in: push up past the last album and hold for 1.2 seconds.
                                    onOpenLocked = { navController.navigate(VaultRoute) },
                                    onOpenSearch = { navController.navigate(SearchRoute) },
                                    onCreateAlbum = { name -> navController.navigate(PickForAlbumRoute(name)) },
                                    onEditAlbums = { navController.navigate(EditAlbumsRoute) },
                                    onOpenFavorites = { navController.navigate(FavoritesRoute) },
                                    onOpenVideos = { navController.navigate(VideosRoute) },
                                    onOpenTrash = { navController.navigate(TrashRoute) },
                                    onSelectionModeChange = { selectionActive = it },
                                    contentBottomPadding = contentBottomPadding,
                                )
                            }
                        }
                        // Back from Albums slides to Pictures first.
                        BackHandler(enabled = homePager.currentPage != 0 && !selectionActive) {
                            scope.launch { homePager.animateScrollToPage(0, animationSpec = TabTapSpec) }
                        }
                    }
                    topLevel<SearchRoute>(navController) { host ->
                        SearchScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMedia = { item, query -> openViewer(host, item, ViewerRoute(item.id, searchQuery = query)) },
                        )
                    }
                    // Slides in from the right (it came from a swipe left past Albums) and back out to the right.
                    composable<SettingsRoute>(
                        enterTransition = { slideInHorizontally(tween(GalleryMotion.FADE_MS * 2)) { it } },
                        popExitTransition = { slideOutHorizontally(tween(GalleryMotion.FADE_MS * 2)) { it } },
                    ) {
                        // iOS-style: drag right anywhere to slide Settings away, back to Pictures / Albums.
                        Box(Modifier.fillMaxSize().backStack(this@composable).background(MaterialTheme.colorScheme.background).swipeBack(enabled = true)) {
                            SettingsScreen(onBack = { navController.popBackStack() }, onOpenLocked = { navController.navigate(VaultRoute) })
                        }
                    }
                    composable<NewAlbumRoute> {
                        NewAlbumScreen(onClose = { navController.popBackStack() })
                    }
                    composable<PickForAlbumRoute> {
                        PickForAlbumScreen(
                            onClose = { navController.popBackStack() },
                            // A new album opens once made; Back from it goes to where + was tapped.
                            onCreated = { id ->
                                navController.navigate(UserAlbumRoute(id)) { popUpTo<PickForAlbumRoute> { inclusive = true } }
                            },
                        )
                    }
                    topLevel<EditAlbumsRoute>(navController) {
                        EditAlbumsScreen(onBack = { navController.popBackStack() })
                    }
                    topLevel<AlbumDetailRoute>(navController) { host ->
                        AlbumDetailScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMedia = { item, bucketId, oldestFirst ->
                                openViewer(host, item, ViewerRoute(item.id, bucketId = bucketId, oldestFirst = oldestFirst))
                            },
                            onAddPhotos = { bucketId, name -> navController.navigate(NewAlbumRoute(name, bucketId)) },
                        )
                    }
                    topLevel<UserAlbumRoute>(navController) { host ->
                        UserAlbumScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMedia = { item, albumId, oldestFirst ->
                                openViewer(host, item, ViewerRoute(item.id, userAlbumId = albumId, oldestFirst = oldestFirst))
                            },
                            onAddPhotos = { albumId, name -> navController.navigate(PickForAlbumRoute(name, albumId)) },
                        )
                    }
                    topLevel<FavoritesRoute>(navController) { host ->
                        FavoritesScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMedia = { item -> openViewer(host, item, ViewerRoute(item.id, favoritesOnly = true)) },
                        )
                    }
                    topLevel<VideosRoute>(navController) { host ->
                        VideosScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMedia = { item -> openViewer(host, item, ViewerRoute(item.id, videosOnly = true)) },
                        )
                    }
                    topLevel<VaultRoute>(navController) { host ->
                        VaultScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMedia = { item, album -> openViewer(host, item, ViewerRoute(item.id, vault = true, vaultAlbum = album)) },
                            // Locking again closes a locked photo that is still open.
                            onRelock = { host.close() },
                        )
                    }
                    topLevel<TrashRoute>(navController) { host ->
                        TrashScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMedia = { item -> openViewer(host, item, ViewerRoute(item.id, trashed = true)) },
                        )
                    }
                    // Only for files other apps ask us to show: the viewer on its own, with no grid underneath.
                    // Viewers opened inside the app are layers over their grid screen (see ViewerOverlay).
                    composable<ViewerRoute>(
                        enterTransition = { fadeIn(tween(GalleryMotion.FADE_MS)) },
                        exitTransition = { fadeOut(tween(GalleryMotion.FADE_MS)) },
                        popEnterTransition = { fadeIn(tween(GalleryMotion.FADE_MS)) },
                        popExitTransition = { fadeOut(tween(GalleryMotion.FADE_MS)) },
                    ) { entry ->
                        val route = entry.toRoute<ViewerRoute>()
                        val factory = rememberViewerViewModelFactory()
                        ViewerScreen(
                            // The external viewer is the whole back stack: closing it returns to the caller.
                            onBack = { if (route.externalUri != null) onExitExternal() else navController.popBackStack() },
                            onCurrentItemChanged = { },
                            onOpenAlbum = { bucketId -> navController.navigate(AlbumDetailRoute(bucketId)) },
                            onOpenFavorites = { navController.navigate(FavoritesRoute) },
                            viewModel = viewModel { factory.create(route) },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showBottomBar,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(tween(GalleryMotion.FADE_MS)),
                    exit = fadeOut(tween(GalleryMotion.FADE_MS)),
                ) {
                    FloatingTabBar(
                        tabs = homeTabs.map { FloatingTab(it.label, it.icon) },
                        selectedIndex = homePager.targetPage,
                        position = { homePager.currentPage + homePager.currentPageOffsetFraction },
                        onSelect = { index -> scope.launch { homePager.animateScrollToPage(index, animationSpec = TabTapSpec) } },
                        haze = navHaze,
                        modifier = Modifier.dragsPager(homePager, scope),
                        // Settings: tap here, or swipe left past Albums and hold.
                        action = FloatingTabAction("Settings", Icons.Outlined.Settings) { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                    )
                }
                UndoBarHost(
                    state = undoBar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = NavigationBarHeight),
                )
            }
        }
    }
}

/**
 * A destination of the app. It hosts its own viewer as a layer on top ([ViewerOverlay]), so a grid stays composed
 * under the photo it opened and the close flight lands in it without a rebuild.
 */
private inline fun <reified T : Any> androidx.navigation.NavGraphBuilder.topLevel(
    navController: NavController,
    crossinline content: @Composable (ViewerHost) -> Unit,
) {
    composable<T>(
        exitTransition = { fadeOut(tween(GalleryMotion.FADE_MS)) },
        popEnterTransition = { fadeIn(tween(GalleryMotion.FADE_MS)) },
    ) {
        val host = rememberViewerHost()
        CompositionLocalProvider(LocalSharedPhoto provides host) {
            // Opaque, so a screen sliding away during the back gesture never shows the one beneath through it.
            Box(Modifier.fillMaxSize().backStack(this@composable).background(MaterialTheme.colorScheme.background)) {
                content(host)
                ViewerOverlay(
                    host = host,
                    onOpenAlbum = { bucketId -> navController.navigate(AlbumDetailRoute(bucketId)) },
                    onOpenFavorites = { navController.navigate(FavoritesRoute) },
                )
            }
        }
    }
}
