package com.rohit.gallery.feature.viewer

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.runtime.snapshotFlow
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.ZoomableImageState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import com.rohit.gallery.core.ui.theme.GalleryMotion
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
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
import com.rohit.gallery.core.ui.components.rememberSharedTransitionActive
import com.rohit.gallery.core.ui.components.thumbKey
import kotlin.math.abs
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

private const val DISMISS_VELOCITY = 1800f
private val DismissDistance = 120.dp
private val UpSwipeOpenDistance = 48.dp

/** How far the photo shrinks at the end of a back gesture, as drag-down progress (0.9: about 60 % size, the
 * background mostly faded, corners rounded like a card). */
private const val BACK_MAX_PROGRESS = 0.9f

/** How far the photo leans towards the swiped edge at the end of a back gesture. */
private val BackLean = 96.dp

/** Share of the screen height the details panel takes; the photo gets the rest. */
private const val InfoPanelFraction = 0.46f

/** Zoom-out before closing a zoomed photo. */
private const val ZOOM_OUT_MS = 220

@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    onCurrentItemChanged: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val list = items
    if (list == null) {
        Box(modifier.fillMaxSize().background(Color.Black))
    } else if (list.isEmpty() && viewModel.isExternal) {
        val context = LocalContext.current
        Box(modifier.fillMaxSize().background(Color.Black))
        LaunchedEffect(Unit) {
            Toast.makeText(context, "Can't open this file", Toast.LENGTH_SHORT).show()
            onBack()
        }
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
    onCurrentItemChanged: (Long?) -> Unit,
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
    var muted by rememberSaveable { mutableStateOf(false) }
    val playback = rememberPlayback(currentItem, muted, viewModel.playbackPositions)
    val trashActions = rememberTrashViewerActions(viewModel)
    TrashViewerDialog(trashActions, currentItem)

    // Drag down (any diagonal) to dismiss, drag up to open the info sheet. The image follows the
    // finger in both axes; the transform is applied inside the shared element (see ViewerPage) so
    // the close flight starts exactly where the finger let go.
    val scope = rememberCoroutineScope()
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dismissed by remember { mutableStateOf(false) }
    // 0..1 while the system back gesture is in progress (see PredictiveBackHandler below).
    var backShrink by remember { mutableFloatStateOf(0f) }
    var zoomed by remember { mutableStateOf(false) }
    // Videos do not zoom; make sure a previous photo's zoom state cannot block the dismiss gesture.
    LaunchedEffect(currentItem.isVideo) { if (currentItem.isVideo) zoomed = false }
    val dismissDistancePx = with(LocalDensity.current) { DismissDistance.toPx() }
    val upSwipeOpenPx = with(LocalDensity.current) { UpSwipeOpenDistance.toPx() }
    // Plain holder (not state): per-gesture bookkeeping that must not trigger recomposition.
    val swipe = remember { object { var mode = SwipeMode.None; var travel = 0f } }
    val infoScroll = rememberScrollState()
    val progress = maxOf((dragY.coerceAtLeast(0f) / (dismissDistancePx * 3)).coerceIn(0f, 1f), backShrink * BACK_MAX_PROGRESS)
    val dismissAlpha by animateFloatAsState(if (dismissed) 0f else 1f, tween(160), label = "dismissAlpha")
    val exitProgress by animateFloatAsState(if (dismissed) 1f else 0f, tween(180), label = "exit")
    val chromeAlpha = dismissAlpha * (1f - maxOf((abs(dragY) / dismissDistancePx).coerceIn(0f, 1f), backShrink))

    fun settle(spec: androidx.compose.animation.core.AnimationSpec<Float>) {
        scope.launch { animate(dragX, 0f, animationSpec = spec) { v, _ -> dragX = v } }
        scope.launch { animate(dragY, 0f, animationSpec = spec) { v, _ -> dragY = v } }
    }

    val dragLayer: GraphicsLayerScope.() -> Unit = {
        translationX = dragX
        translationY = dragY
        val s = (1f - 0.45f * progress) * (1f - 0.2f * exitProgress)
        alpha = 1f - exitProgress
        scaleX = s
        scaleY = s
        shape = RoundedCornerShape((28.dp * progress).toPx())
        clip = progress > 0f
    }
    // The same drag, for the flight copy: a swipe-down close flies from where the finger left the photo.
    val flightPlacement: () -> FlightPlacement = {
        val p = maxOf((dragY.coerceAtLeast(0f) / (dismissDistancePx * 3)).coerceIn(0f, 1f), backShrink * BACK_MAX_PROGRESS)
        FlightPlacement(dragX, dragY, 1f - 0.45f * p)
    }

    // Details: the photo shrinks upward into the area above an inline panel (stacked, not overlapping).
    val infoProgress by animateFloatAsState(if (showInfo) 1f else 0f, GalleryMotion.snappy(), label = "info")
    BackHandler(enabled = showInfo) { showInfo = false }

    // Closing while zoomed in: animate the zoom out first, then close, so the flight back to the grid
    // starts from the fitted photo instead of jumping to it. Plain holder: set by the current image page.
    val currentZoom = remember { object { var state: ZoomableImageState? = null; var closing = false } }
    fun close() {
        if (currentZoom.closing) return
        val zoomState = currentZoom.state
        if (zoomed && zoomState != null) {
            currentZoom.closing = true
            scope.launch {
                runCatching { zoomState.zoomableState.resetZoom(tween(ZOOM_OUT_MS)) }
                onBack()
            }
        } else {
            onBack()
        }
    }
    BackHandler(enabled = zoomed && !showInfo) { close() }

    // System back gesture (not zoomed, details closed): the photo shrinks and leans towards the edge the swipe
    // started from, following the finger. Letting go flies it into its grid cell like a drag-down close;
    // cancelling springs it back. Replaces the navigation library's shrinking-card preview for this screen.
    val backLeanPx = with(LocalDensity.current) { BackLean.toPx() }
    PredictiveBackHandler(enabled = !zoomed && !showInfo && !dismissed) { events ->
        try {
            events.collect { event ->
                val toward = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                // Ease-out, so even a short swipe already shows a clear change.
                val eased = 1f - (1f - event.progress) * (1f - event.progress)
                backShrink = eased
                dragX = toward * eased * backLeanPx
            }
            dismissed = true
            onBack()
        } catch (e: CancellationException) {
            settle(GalleryMotion.standard())
            scope.launch { animate(backShrink, 0f, animationSpec = GalleryMotion.standard()) { v, _ -> backShrink = v } }
            throw e
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dismissAlpha * (1f - 0.85f * progress))),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalDismissGestures(
                    enabled = { !zoomed && !dismissed },
                    onDrag = { delta ->
                        // What a swipe does is decided once, when it starts, and never changes mid-swipe
                        // (otherwise closing the details could carry on into closing the viewer).
                        if (swipe.mode == SwipeMode.None) {
                            swipe.mode = when {
                                delta.y < 0 -> if (showInfo) SwipeMode.Ignore else SwipeMode.OpenInfo
                                showInfo -> SwipeMode.CloseInfo
                                else -> SwipeMode.Dismiss
                            }
                        }
                        when (swipe.mode) {
                            SwipeMode.OpenInfo -> {
                                // No image movement; open as soon as it is clearly a swipe up.
                                swipe.travel -= delta.y
                                if (swipe.travel > upSwipeOpenPx) showInfo = true
                            }
                            SwipeMode.CloseInfo -> {
                                swipe.travel += delta.y
                                if (swipe.travel > upSwipeOpenPx) showInfo = false
                            }
                            SwipeMode.Dismiss -> {
                                dragX += delta.x
                                dragY = (dragY + delta.y).coerceAtLeast(0f)
                            }
                            else -> Unit
                        }
                    },
                    onEnd = { _, _, velocityY ->
                        val mode = swipe.mode
                        swipe.mode = SwipeMode.None
                        swipe.travel = 0f
                        when (mode) {
                            SwipeMode.OpenInfo -> if (velocityY < -DISMISS_VELOCITY) showInfo = true
                            SwipeMode.CloseInfo -> if (velocityY > DISMISS_VELOCITY) showInfo = false
                            SwipeMode.Dismiss ->
                                if (dragY > dismissDistancePx || velocityY > DISMISS_VELOCITY) {
                                    // The flight stays on: the photo glides from here into its grid cell.
                                    dismissed = true
                                    onBack()
                                } else {
                                    settle(GalleryMotion.standard())
                                }
                            else -> Unit
                        }
                    },
                    onCancel = {
                        swipe.mode = SwipeMode.None
                        swipe.travel = 0f
                        settle(GalleryMotion.standard())
                    },
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                key = { page -> latestItems.getOrNull(page)?.id ?: page },
                beyondViewportPageCount = 1,
                pageSpacing = 12.dp,
                modifier = Modifier.layout { measurable, constraints ->
                    // The pager gets the screen minus the panel, kept at the top.
                    val height = (constraints.maxHeight * (1f - InfoPanelFraction * infoProgress)).roundToInt()
                    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                },
            ) { page ->
                val item = items.getOrNull(page) ?: return@HorizontalPager
                val isCurrent = page == pagerState.currentPage
                ViewerPage(
                    item = item,
                    isCurrent = isCurrent,
                    playback = if (isCurrent) playback else null,
                    dragLayer = if (isCurrent) dragLayer else null,
                    flightPlacement = if (isCurrent) flightPlacement else null,
                    onZoomedChange = { zoomed = it },
                    onZoomState = { state -> currentZoom.state = state },
                    onTap = { if (showInfo) showInfo = false else chromeVisible = !chromeVisible },
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
                    onBack = ::close,
                    onInfo = { showInfo = !showInfo },
                    modifier = Modifier.graphicsLayer { alpha = chromeAlpha },
                )
            }

            // Filmstrip and video controls give way to the details panel.
            AnimatedVisibility(
                visible = chromeVisible && !showInfo,
                enter = fadeIn(tween(GalleryMotion.FADE_MS)),
                exit = fadeOut(tween(GalleryMotion.FADE_MS)),
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = chromeAlpha }
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp),
                ) {
                    if (viewModel.isTrash) {
                        TrashViewerBar(onRestore = { trashActions.restore(currentItem) }, onDelete = trashActions::askDelete)
                    }
                    if (playback != null) {
                        VideoControls(playback, onToggleMute = { muted = !muted })
                    }
                    ViewerFilmstrip(
                        items = items,
                        currentIndex = currentIndex,
                        onSelect = { index -> scope.launch { pagerState.scrollToPage(index) } },
                    )
                }
            }
        }

        if (showInfo || infoProgress > 0.001f) {
            InfoPanel(
                item = currentItem,
                askedForLocation = askedForLocation,
                onAskedForLocation = { askedForLocation = true },
                loadDetails = viewModel::loadDetails,
                scrollState = infoScroll,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(InfoPanelFraction)
                    .graphicsLayer { translationY = (1f - infoProgress) * size.height }
                    // Swiping down on the panel (when scrolled to its top) closes it and returns to the photo.
                    .verticalDismissGestures(
                        enabled = { infoScroll.value == 0 },
                        downOnly = true,
                        onDrag = { delta ->
                            swipe.travel += delta.y
                            if (swipe.travel > upSwipeOpenPx) showInfo = false
                        },
                        onEnd = { _, _, velocityY ->
                            swipe.travel = 0f
                            if (velocityY > DISMISS_VELOCITY) showInfo = false
                        },
                        onCancel = { swipe.travel = 0f },
                    ),
            )
        }
    }
}

@Composable
private fun ImagePage(
    item: MediaItem,
    isCurrent: Boolean,
    dragLayer: (GraphicsLayerScope.() -> Unit)?,
    flightPlacement: (() -> FlightPlacement)?,
    onZoomedChange: (Boolean) -> Unit,
    onZoomState: (ZoomableImageState?) -> Unit,
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
    val zoomState = rememberZoomableImageState()
    if (isCurrent) {
        LaunchedEffect(zoomState) {
            snapshotFlow { (zoomState.zoomableState.zoomFraction ?: 0f) > 0.02f }.collect(onZoomedChange)
        }
        DisposableEffect(zoomState) {
            onZoomState(zoomState)
            onDispose { onZoomState(null) }
        }
    }
    val transitionActive = rememberSharedTransitionActive()
    val flightEnabled = isCurrent
    Box(Modifier.fillMaxSize()) {
        if (flightEnabled) SharedFlightFrame(item, rememberThumbnail(item), transitionActive, placement = flightPlacement)
        ZoomableAsyncImage(
            model = request,
            contentDescription = null,
            state = zoomState,
            onClick = { onTap() },
            modifier = Modifier
                .fillMaxSize()
                .then(if (dragLayer != null) Modifier.graphicsLayer(dragLayer) else Modifier)
                // The photo-shaped copy flies during transitions; this interactive one waits.
                .graphicsLayer { alpha = if (flightEnabled && transitionActive()) 0f else 1f },
        )
    }
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
            // Files from other apps may carry no date at all.
            if (item.sortDateMs > 0) formatDateTime(item.sortDateMs) else "",
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

@Composable
private fun ViewerPage(
    item: MediaItem,
    isCurrent: Boolean,
    playback: PlaybackState?,
    dragLayer: (GraphicsLayerScope.() -> Unit)?,
    flightPlacement: (() -> FlightPlacement)?,
    onZoomedChange: (Boolean) -> Unit,
    onZoomState: (ZoomableImageState?) -> Unit,
    onTap: () -> Unit,
) {
    if (item.isVideo) {
        VideoPage(item, isCurrent, playback, dragLayer, flightPlacement, onTap)
    } else {
        ImagePage(item, isCurrent, dragLayer, flightPlacement, onZoomedChange, onZoomState, onTap)
    }
}

/** A video page: the cached thumbnail until the first frame is drawn, then the player surface. */
@Composable
private fun VideoPage(
    item: MediaItem,
    isCurrent: Boolean,
    playback: PlaybackState?,
    dragLayer: (GraphicsLayerScope.() -> Unit)?,
    flightPlacement: (() -> FlightPlacement)?,
    onTap: () -> Unit,
) {
    val thumbnail = rememberThumbnail(item)
    val transitionActive = rememberSharedTransitionActive()
    val flightEnabled = isCurrent
    val shutter: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            thumbnail?.let {
                Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
    if (flightEnabled) SharedFlightFrame(item, thumbnail, transitionActive, placement = flightPlacement)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (flightEnabled && transitionActive()) 0f else 1f }
            .then(if (dragLayer != null) Modifier.graphicsLayer(dragLayer) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap() },
    ) {
        if (playback != null) {
            // TextureView (not SurfaceView) so the drag-to-dismiss scale, rounding and fade apply to the video.
            ContentFrame(
                player = playback.player,
                modifier = Modifier.fillMaxSize(),
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = ContentScale.Fit,
                keepContentOnReset = true,
                shutter = shutter,
            )
        } else {
            shutter()
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(10.dp),
            )
        }
    }
    }
}


/** What the current vertical swipe on the photo does; fixed at the start of the swipe. */
private enum class SwipeMode { None, OpenInfo, CloseInfo, Dismiss, Ignore }
