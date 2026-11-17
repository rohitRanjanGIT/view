package com.rohit.gallery.feature.viewer

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import com.rohit.gallery.core.ui.theme.GalleryMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.asImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.placeholder
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.LocalThumbnailCache
import com.rohit.gallery.core.ui.components.mediaSharedElement
import com.rohit.gallery.core.ui.components.thumbKey
import kotlin.math.abs
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

private const val DISMISS_VELOCITY = 1800f
private val DismissDistance = 120.dp

@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    onCurrentItemChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val list = items
    if (list == null) {
        Box(modifier.fillMaxSize().background(Color.Black))
    } else {
        ViewerContent(
            items = list,
            startId = viewModel.startId,
            onBack = onBack,
            onCurrentItemChanged = onCurrentItemChanged,
            viewModel = viewModel,
            modifier = modifier,
        )
    }
}

@Composable
private fun ViewerContent(
    items: List<MediaItem>,
    startId: Long,
    onBack: () -> Unit,
    onCurrentItemChanged: (Long) -> Unit,
    viewModel: ViewerViewModel,
    modifier: Modifier = Modifier,
) {
    val latestItems by rememberUpdatedState(items)
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val startIndex = remember { items.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = startIndex) { latestItems.size }
    val currentIndex = pagerState.currentPage.coerceIn(0, items.lastIndex)
    val currentItem = items[currentIndex]

    // Keep the grid's shared-element target in sync with what is on screen, so closing the
    // viewer flies back to the right thumbnail.
    LaunchedEffect(currentItem.id) { onCurrentItemChanged(currentItem.id) }

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var askedForLocation by rememberSaveable { mutableStateOf(false) }
    SystemBarsEffect(visible = chromeVisible)

    // Drag down to dismiss, drag up to open the info sheet.
    val scope = rememberCoroutineScope()
    var offsetY by remember { mutableFloatStateOf(0f) }
    val dismissDistancePx = with(LocalDensity.current) { DismissDistance.toPx() }
    val dragState = rememberDraggableState { delta ->
        // Resist upward travel; the sheet only needs a small nudge to feel connected.
        offsetY += if (offsetY + delta < 0) delta * 0.4f else delta
    }
    val progress = (abs(offsetY) / (dismissDistancePx * 3)).coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 1f - 0.7f * progress))
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    when {
                        offsetY > dismissDistancePx || velocity > DISMISS_VELOCITY -> onBack()
                        offsetY < -dismissDistancePx * 0.4f || velocity < -DISMISS_VELOCITY -> {
                            showInfo = true
                            scope.launch { animate(offsetY, 0f) { v, _ -> offsetY = v } }
                        }
                        else -> scope.launch { animate(offsetY, 0f) { v, _ -> offsetY = v } }
                    }
                },
            ),
    ) {
        HorizontalPager(
            state = pagerState,
            key = { page -> latestItems.getOrNull(page)?.id ?: page },
            beyondViewportPageCount = 1,
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offsetY
                    val s = 1f - 0.25f * progress
                    scaleX = s
                    scaleY = s
                },
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            ViewerPage(
                item = item,
                isCurrent = page == pagerState.currentPage,
                onTap = { chromeVisible = !chromeVisible },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(GalleryMotion.FADE_MS)),
            exit = fadeOut(tween(GalleryMotion.FADE_MS)),
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopStart),
        ) {
            ViewerTopBar(
                item = currentItem,
                onBack = onBack,
                onInfo = { showInfo = true },
                modifier = Modifier.graphicsLayer { alpha = 1f - progress },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(GalleryMotion.FADE_MS)),
            exit = fadeOut(tween(GalleryMotion.FADE_MS)),
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 1f - progress }
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp),
            ) {
                ViewerFilmstrip(
                    items = items,
                    currentIndex = currentIndex,
                    onSelect = { index -> scope.launch { pagerState.scrollToPage(index) } },
                )
            }
        }
    }

    if (showInfo) {
        InfoSheet(
            item = currentItem,
            askedForLocation = askedForLocation,
            onAskedForLocation = { askedForLocation = true },
            loadDetails = viewModel::loadDetails,
            onDismiss = { showInfo = false },
        )
    }
}

@Composable
private fun ViewerPage(
    item: MediaItem,
    isCurrent: Boolean,
    onTap: () -> Unit,
) {
    val cache = LocalThumbnailCache.current
    val context = LocalPlatformContext.current
    // The grid thumbnail is already in memory; show it while the full image decodes so the
    // open transition never flashes blank.
    val request = remember(item.id, item.dateModifiedMs) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .placeholder(cache.get(item.thumbKey)?.asImage())
            .build()
    }
    ZoomableAsyncImage(
        model = request,
        contentDescription = null,
        onClick = { onTap() },
        modifier = Modifier
            .fillMaxSize()
            .then(if (isCurrent) Modifier.mediaSharedElement(item.id, force = true) else Modifier),
    )
}

@Composable
private fun ViewerTopBar(
    item: MediaItem,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(
            formatDateTime(item.sortDateMs),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onInfo) {
            Icon(Icons.Filled.Info, contentDescription = "Details", tint = Color.White)
        }
    }
}

/** Hides the system bars with the viewer chrome and keeps their icons readable on black. */
@Composable
private fun SystemBarsEffect(visible: Boolean) {
    val activity = LocalActivity.current
    val view = LocalView.current
    val window = activity?.window ?: return
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }

    DisposableEffect(controller) {
        val wasLightStatus = controller.isAppearanceLightStatusBars
        val wasLightNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = wasLightStatus
            controller.isAppearanceLightNavigationBars = wasLightNav
        }
    }
    LaunchedEffect(visible, controller) {
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
