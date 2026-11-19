package com.rohit.gallery.feature.viewer

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import coil3.decode.BitmapFactoryDecoder
import coil3.memory.MemoryCache
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.asImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.placeholder
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.data.mediastore.MediaTraits
import com.rohit.gallery.core.ui.components.FavoriteHeartRed
import com.rohit.gallery.core.ui.components.LocalThumbnailCache
import com.rohit.gallery.core.ui.components.rememberSharedTransitionActive
import com.rohit.gallery.core.ui.components.thumbKey
import kotlin.math.abs
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

/**
 * Projection for swipe-down: shorter than a scroll's 0.998, so only a real flick closes the viewer on speed alone
 * (about 1700 px/s with no distance, close to the old fixed 1800 px/s threshold).
 */
private const val DISMISS_DECELERATION = 0.995f

/** Moving back up faster than this (per second) at release keeps the viewer open, however far it was pulled. */
private val FlickBackVelocity = 150.dp
private val DismissDistance = 120.dp

/** How far the photo shrinks at the end of a back gesture, as drag-down progress (0.9: about 60 % size, the
 * background mostly faded, corners rounded like a card). */
private const val BACK_MAX_PROGRESS = 0.9f

/** How far the photo leans towards the swiped edge at the end of a back gesture. */
private val BackLean = 24.dp

/** Share of the screen height the details panel takes; the photo gets the rest. */
private const val InfoPanelFraction = 0.46f

/** Zoom-out before closing a zoomed photo. */
private const val ZOOM_OUT_MS = 220

@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    onCurrentItemChanged: (Long?) -> Unit,
    onOpenAlbum: (bucketId: Long) -> Unit,
    onOpenFavorites: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ViewerViewModel,
    // A swipe-down close, with the finger's release velocity (px/s); [onBack] when not given.
    onSwipeClose: ((velocity: Offset) -> Unit)? = null,
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
            onSwipeClose = onSwipeClose,
            onCurrentItemChanged = onCurrentItemChanged,
            onOpenAlbum = onOpenAlbum,
            onOpenFavorites = onOpenFavorites,
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
    onSwipeClose: ((velocity: Offset) -> Unit)?,
    onCurrentItemChanged: (Long?) -> Unit,
    onOpenAlbum: (bucketId: Long) -> Unit,
    onOpenFavorites: () -> Unit,
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
    var traits by remember { mutableStateOf(MediaTraits.None) }
    LaunchedEffect(currentItem.id, currentItem.dateModifiedMs) {
        traits = MediaTraits.None
        traits = viewModel.traits(currentItem)
    }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    HdrWindowEffect(enabled = traits.ultraHdr && settings?.ultraHdr != false)
    // The Play pill: a motion photo's clip plays over it once, then the photo is back; a GIF stays on its first
    // frame until Play, then loops until Pause. Both reset when another item comes on screen.
    val isGif = currentItem.isGif
    val playable = !currentItem.isVideo && (isGif || traits.motionPhoto)
    var playing by remember(currentItem.id) { mutableStateOf(false) }
    val motionPlaying = playing && traits.motionPhoto
    // "Play motion photos automatically": once, as soon as one is opened (and known to be one).
    LaunchedEffect(currentItem.id, traits.motionPhoto) {
        if (traits.motionPhoto && settings?.motionAutoplay == true) playing = true
    }
    var muted by rememberSaveable { mutableStateOf(false) }
    // "Start videos muted" sets the first value only; the mute button then works as usual for this visit.
    var mutedFromSettings by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!mutedFromSettings) {
            muted = viewModel.settings.filterNotNull().first().startMuted
            mutedFromSettings = true
        }
    }
    val playback = rememberPlayback(currentItem, muted, viewModel.playbackPositions)
    val trashActions = rememberTrashViewerActions(viewModel)
    TrashViewerDialog(trashActions, currentItem)

    // Drag down (any diagonal) to dismiss, drag up to open the info sheet. The image follows the
    // finger in both axes; the transform is applied inside the shared element (see ViewerPage) so
    // the close flight starts exactly where the finger let go.
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dismissed by remember { mutableStateOf(false) }
    // 0..1 while the system back gesture is in progress (see PredictiveBackHandler below).
    var backShrink by remember { mutableFloatStateOf(0f) }
    var zoomed by remember { mutableStateOf(false) }
    // Videos do not zoom; make sure a previous photo's zoom state cannot block the dismiss gesture.
    LaunchedEffect(currentItem.isVideo) { if (currentItem.isVideo) zoomed = false }
    val dismissDistancePx = with(LocalDensity.current) { DismissDistance.toPx() }
    val flickBackPx = with(LocalDensity.current) { FlickBackVelocity.toPx() }
    // Plain holder (not state): per-gesture bookkeeping that must not trigger recomposition.
    val swipe = remember { object { var mode = SwipeMode.None; var infoStart = 0f; var infoTravel = 0f } }
    val settleJobs = remember { mutableListOf<Job>() }
    val infoScroll = rememberScrollState()
    val progress = maxOf((dragY.coerceAtLeast(0f) / (dismissDistancePx * 3)).coerceIn(0f, 1f), backShrink * BACK_MAX_PROGRESS)
    val dismissAlpha by animateFloatAsState(if (dismissed) 0f else 1f, tween(160), label = "dismissAlpha")
    val exitProgress by animateFloatAsState(if (dismissed) 1f else 0f, tween(180), label = "exit")
    val chromeAlpha = dismissAlpha * (1f - maxOf((abs(dragY) / dismissDistancePx).coerceIn(0f, 1f), backShrink))

    // Grabbing the photo while it springs back stops it where it is on screen; the drag carries on from there.
    fun stopSettle() {
        settleJobs.forEach { it.cancel() }
        settleJobs.clear()
    }
    // Springs the photo back to rest, starting at the finger's release [velocity] (px/s), each axis on its own.
    fun settle(velocity: Offset = Offset.Zero) {
        stopSettle()
        val spec = GalleryMotion.dragReturn<Float>()
        settleJobs += scope.launch { animate(dragX, 0f, velocity.x, spec) { v, _ -> dragX = v } }
        settleJobs += scope.launch { animate(dragY, 0f, velocity.y, spec) { v, _ -> dragY = v } }
    }

    // [contentAspect]: the photo's width / height (0 if unknown), so the rounding follows its real edges.
    val dragLayer: GraphicsLayerScope.(contentAspect: Float) -> Unit = { contentAspect ->
        translationX = dragX
        translationY = dragY
        val s = (1f - 0.45f * progress) * (1f - 0.2f * exitProgress)
        alpha = 1f - exitProgress
        scaleX = s
        scaleY = s
        val radius = (28.dp * progress).toPx()
        shape = if (contentAspect > 0f) FittedRoundedShape(contentAspect, radius) else RoundedCornerShape(radius)
        clip = progress > 0f
    }
    // The same drag, for the flight copy: a swipe-down close flies from where the finger left the photo.
    val flightPlacement: () -> FlightPlacement = {
        val p = maxOf((dragY.coerceAtLeast(0f) / (dismissDistancePx * 3)).coerceIn(0f, 1f), backShrink * BACK_MAX_PROGRESS)
        FlightPlacement(dragX, dragY, 1f - 0.45f * p)
    }

    // Details: the photo shrinks upward into the area above an inline panel (stacked, not overlapping).
    // The panel follows the finger 1:1 while dragged ([infoDrag]); otherwise [info] springs it open or shut, from
    // wherever it is, so it can be grabbed again mid-way.
    val info = remember { Animatable(if (showInfo) 1f else 0f) }
    var infoDrag by remember { mutableStateOf<Float?>(null) }
    val infoProgress = infoDrag ?: info.value
    val panelPx = LocalWindowInfo.current.containerSize.height * InfoPanelFraction
    LaunchedEffect(showInfo) {
        // Buttons, taps and Back; a released swipe has already started its own animation towards this target.
        val target = if (showInfo) 1f else 0f
        if (infoDrag == null && info.targetValue != target) info.animateTo(target, GalleryMotion.sheet())
    }
    // [dy]: finger movement in px, down closes.
    fun dragInfo(dy: Float) {
        if (infoDrag == null) {
            swipe.infoStart = info.value
            swipe.infoTravel = 0f
            scope.launch { info.stop() }
        }
        swipe.infoTravel += dy
        infoDrag = (swipe.infoStart - swipe.infoTravel / panelPx).coerceIn(0f, 1f)
    }
    // Opens or closes towards where the swipe was heading, starting at its speed ([velocityY] px/s, down closes).
    fun releaseInfo(velocityY: Float) {
        val from = infoDrag ?: return
        val open = from - GalleryMotion.project(velocityY) / panelPx > 0.5f
        showInfo = open
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            info.snapTo(from)
            infoDrag = null
            info.animateTo(if (open) 1f else 0f, GalleryMotion.sheet(), initialVelocity = -velocityY / panelPx)
        }
    }
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

    // Favourite and trash go through the system's confirmation (skipped when "Skip confirmation" is allowed).
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onWritten()
    }
    fun write(request: suspend () -> IntentSender?) {
        scope.launch {
            val sender = request()
            if (sender == null) {
                Toast.makeText(context, "Couldn't start that action", Toast.LENGTH_SHORT).show()
            } else {
                writeLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
        }
    }
    fun startOrToast(intent: Intent) {
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, "No app can open this", Toast.LENGTH_SHORT).show() }
    }
    val canWrite = viewModel.canWrite(currentItem)
    val bottomActions = if (viewModel.isTrash) {
        BottomActions(
            start = null,
            middle = listOf(
                ViewerAction(Icons.Filled.Refresh, "Restore", { trashActions.restore(currentItem) }),
                ViewerAction(Icons.Outlined.Delete, "Delete forever", trashActions::askDelete),
            ),
        )
    } else {
        BottomActions(
            start = ViewerAction(Icons.Outlined.Share, "Share", { startOrToast(viewModel.shareIntent(currentItem)) }),
            middle = buildList {
                if (canWrite) {
                    val favorite = currentItem.isFavorite
                    add(
                        ViewerAction(
                            if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (favorite) "Remove from favourites" else "Add to favourites",
                            { write { viewModel.favoriteRequest(currentItem) } },
                            tint = if (favorite) FavoriteHeartRed else null,
                        ),
                    )
                }
                if (!currentItem.isVideo) {
                    // Editing is handed to Google Photos for now (it saves a copy); our own editor comes later.
                    add(
                        ViewerAction(Icons.Outlined.Edit, "Edit", {
                            if (!viewModel.openInEditor(currentItem)) {
                                Toast.makeText(context, "No photo editor found", Toast.LENGTH_SHORT).show()
                            }
                        }),
                    )
                }
                if (canWrite) add(ViewerAction(Icons.Outlined.Delete, "Move to trash", { write { viewModel.trashRequest(currentItem) } }))
            },
        )
    }
    fun viewerMenu(item: MediaItem): List<ViewerMenuEntry> = if (viewModel.isTrash) emptyList() else buildList {
        fun intent(action: String) = Intent(action)
            .setDataAndType(item.uri, item.mimeType.ifBlank { if (item.isVideo) "video/*" else "image/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Read-only hand-offs: the other app can use the file but never change it.
        if (!item.isVideo) {
            add(ViewerMenuEntry("Use as…") { startOrToast(Intent.createChooser(intent(Intent.ACTION_ATTACH_DATA), "Use as")) })
        }
        add(ViewerMenuEntry("Open with another app") { startOrToast(Intent.createChooser(intent(Intent.ACTION_VIEW), null)) })
    }

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
            settle()
            scope.launch { animate(backShrink, 0f, animationSpec = GalleryMotion.dragReturn()) { v, _ -> backShrink = v } }
            throw e
        }
    }

    Box(
        modifier
            .fillMaxSize()
            // While a gesture fades the black, the grid underneath shows through, with a gap where this photo sits.
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
                            if (swipe.mode == SwipeMode.Dismiss) stopSettle()
                        }
                        when (swipe.mode) {
                            // The photo stays put; the panel follows the finger.
                            SwipeMode.OpenInfo, SwipeMode.CloseInfo -> dragInfo(delta.y)
                            SwipeMode.Dismiss -> {
                                dragX += delta.x
                                dragY = (dragY + delta.y).coerceAtLeast(0f)
                            }
                            else -> Unit
                        }
                    },
                    onEnd = { _, velocityX, velocityY ->
                        val mode = swipe.mode
                        swipe.mode = SwipeMode.None
                        val velocity = Offset(velocityX, velocityY)
                        when (mode) {
                            SwipeMode.OpenInfo, SwipeMode.CloseInfo -> releaseInfo(velocityY)
                            SwipeMode.Dismiss -> {
                                // Decide by where the swipe is heading, not where the finger stopped; flicking back
                                // up always keeps the viewer.
                                val heading = dragY + GalleryMotion.project(velocityY, DISMISS_DECELERATION)
                                if (velocityY > -flickBackPx && heading > dismissDistancePx) {
                                    // The flight stays on: the photo carries on at the finger's speed into its cell.
                                    dismissed = true
                                    onSwipeClose?.invoke(velocity) ?: onBack()
                                } else {
                                    settle(velocity)
                                }
                            }
                            else -> Unit
                        }
                    },
                    onCancel = {
                        val mode = swipe.mode
                        swipe.mode = SwipeMode.None
                        if (mode == SwipeMode.OpenInfo || mode == SwipeMode.CloseInfo) releaseInfo(0f) else settle()
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
                    animateGif = isCurrent && playing,
                    motionClip = if (isCurrent && motionPlaying) {
                        { clipModifier -> MotionClip(item, muted, onEnded = { playing = false }, modifier = clipModifier) }
                    } else {
                        null
                    },
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
                    menu = viewerMenu(currentItem),
                    modifier = Modifier.graphicsLayer { alpha = chromeAlpha },
                )
            }
        }

        if (showInfo || infoProgress > 0.001f) {
            InfoPanel(
                item = currentItem,
                askedForLocation = askedForLocation,
                onAskedForLocation = { askedForLocation = true },
                loadDetails = viewModel::loadDetails,
                // The album the item is in (not for Trash, or a file another app handed us).
                onOpenAlbum = if (viewModel.isTrash || !canWrite || currentItem.bucketId == 0L) null else {
                    { onOpenAlbum(currentItem.bucketId) }
                },
                onOpenFavorites = if (currentItem.isFavorite && !viewModel.isTrash) onOpenFavorites else null,
                scrollState = infoScroll,
                bottomSpace = ActionBarHeight + 24.dp,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(InfoPanelFraction)
                    .graphicsLayer { translationY = (1f - infoProgress) * size.height }
                    // Swiping down on the panel (when scrolled to its top) closes it and returns to the photo.
                    .verticalDismissGestures(
                        enabled = { infoScroll.value == 0 },
                        downOnly = true,
                        onDrag = { delta -> dragInfo(delta.y) },
                        onEnd = { _, _, velocityY -> releaseInfo(velocityY) },
                        onCancel = { releaseInfo(0f) },
                    ),
            )
        }

        // Bottom: video controls or the Play pill, the filmstrip, then the action bar. The action bar stays over the
        // details panel (in its colours); the rest gives way to it.
        Column(
            Modifier
                .align(androidx.compose.ui.Alignment.BottomStart)
                .fillMaxWidth()
                .graphicsLayer { alpha = chromeAlpha }
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f * (1f - infoProgress)))),
                )
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 4.dp),
        ) {
            AnimatedVisibility(
                visible = chromeVisible && !showInfo,
                enter = fadeIn(tween(GalleryMotion.FADE_MS)),
                exit = fadeOut(tween(GalleryMotion.FADE_MS)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (playback != null) {
                        VideoControls(playback, onToggleMute = { muted = !muted })
                    }
                    if (playable) {
                        PlayPill(
                            playing = playing,
                            onClick = { playing = !playing },
                            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally),
                        )
                    }
                    ViewerFilmstrip(
                        items = items,
                        currentIndex = currentIndex,
                        onSelect = { index -> scope.launch { pagerState.scrollToPage(index) } },
                    )
                }
            }
            AnimatedVisibility(
                visible = chromeVisible || showInfo,
                enter = fadeIn(tween(GalleryMotion.FADE_MS)),
                exit = fadeOut(tween(GalleryMotion.FADE_MS)),
            ) {
                ViewerActionBar(
                    start = bottomActions.start,
                    middle = bottomActions.middle,
                    end = ViewerAction(
                        if (showInfo) Icons.Filled.Info else Icons.Outlined.Info,
                        "Details",
                        { showInfo = !showInfo },
                    ),
                    onPanel = infoProgress,
                )
            }
        }
    }
}

/** Left circle and middle pill of the bottom bar; the right circle is always Details. */
private class BottomActions(val start: ViewerAction?, val middle: List<ViewerAction>)

@Composable
private fun ImagePage(
    item: MediaItem,
    isCurrent: Boolean,
    dragLayer: (GraphicsLayerScope.(Float) -> Unit)?,
    flightPlacement: (() -> FlightPlacement)?,
    onZoomedChange: (Boolean) -> Unit,
    onZoomState: (ZoomableImageState?) -> Unit,
    onTap: () -> Unit,
    animateGif: Boolean = false,
    motionClip: (@Composable (Modifier) -> Unit)? = null,
) {
    val cache = LocalThumbnailCache.current
    val context = LocalPlatformContext.current
    // The grid thumbnail is already in memory; show it while the full image decodes so the
    // open transition never flashes blank.
    val request = remember(item.id, item.dateModifiedMs, item.isGif && animateGif) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .placeholder(cache.get(item.thumbKey)?.asImage())
            .apply {
                if (item.isGif) {
                    // A GIF shows its first frame (plain bitmap decode) until Play; then the animated decoder.
                    // Separate cache keys, so the still frame never stands in for the animation.
                    val still = MemoryCache.Key("gif-still:${item.id}:${item.dateModifiedMs}")
                    if (animateGif) {
                        memoryCacheKey("gif-anim:${item.id}:${item.dateModifiedMs}")
                        placeholderMemoryCacheKey(still)
                    } else {
                        memoryCacheKey(still)
                        decoderFactory(BitmapFactoryDecoder.Factory())
                    }
                }
            }
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
    val thumbnail = rememberThumbnail(item)
    val aspect = contentAspect(item, thumbnail)
    val transitionActive = rememberSharedTransitionActive()
    val flightEnabled = isCurrent
    Box(Modifier.fillMaxSize()) {
        if (flightEnabled) SharedFlightFrame(item, thumbnail, transitionActive, placement = flightPlacement)
        ZoomableAsyncImage(
            model = request,
            contentDescription = null,
            state = zoomState,
            onClick = { onTap() },
            modifier = Modifier
                .fillMaxSize()
                .then(if (dragLayer != null) Modifier.graphicsLayer { dragLayer(aspect) } else Modifier)
                // The photo-shaped copy flies during transitions; this interactive one waits.
                .graphicsLayer { alpha = if (flightEnabled && transitionActive()) 0f else 1f },
        )
        // A motion photo's clip, over the photo and moved by the same gestures.
        motionClip?.invoke(
            Modifier
                .fillMaxSize()
                .then(if (dragLayer != null) Modifier.graphicsLayer { dragLayer(aspect) } else Modifier)
                .graphicsLayer { alpha = if (flightEnabled && transitionActive()) 0f else 1f },
        )
    }
}
/**
 * Puts the window in HDR mode while an Ultra HDR photo is on screen, so its gain map lifts the highlights above
 * normal white. Back to the default mode for other photos and when the viewer goes away.
 */
@Composable
private fun HdrWindowEffect(enabled: Boolean) {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window, enabled) {
        window.colorMode = if (enabled) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
        onDispose { window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT }
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
    dragLayer: (GraphicsLayerScope.(Float) -> Unit)?,
    flightPlacement: (() -> FlightPlacement)?,
    onZoomedChange: (Boolean) -> Unit,
    onZoomState: (ZoomableImageState?) -> Unit,
    onTap: () -> Unit,
    animateGif: Boolean,
    motionClip: (@Composable (Modifier) -> Unit)?,
) {
    if (item.isVideo) {
        VideoPage(item, isCurrent, playback, dragLayer, flightPlacement, onTap)
    } else {
        ImagePage(item, isCurrent, dragLayer, flightPlacement, onZoomedChange, onZoomState, onTap, animateGif, motionClip)
    }
}

private val MediaItem.isGif: Boolean get() = mimeType.equals("image/gif", ignoreCase = true)



/** A video page: the cached thumbnail until the first frame is drawn, then the player surface. */
@Composable
private fun VideoPage(
    item: MediaItem,
    isCurrent: Boolean,
    playback: PlaybackState?,
    dragLayer: (GraphicsLayerScope.(Float) -> Unit)?,
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
            .then(if (dragLayer != null) Modifier.graphicsLayer { dragLayer(contentAspect(item, thumbnail)) } else Modifier)
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

/** Width / height of the photo as shown (the decoded thumbnail knows the real orientation); 0 if unknown. */
private fun contentAspect(item: MediaItem, thumbnail: ImageBitmap?): Float = when {
    thumbnail != null && thumbnail.height > 0 -> thumbnail.width.toFloat() / thumbnail.height
    item.width > 0 && item.height > 0 -> item.width.toFloat() / item.height
    else -> 0f
}

/**
 * Rounds the corners of the photo itself rather than its full-screen page: the outline is the photo fitted
 * (centred, whole) inside the page, so letterboxed photos get visibly rounded corners while they shrink.
 */
private class FittedRoundedShape(private val aspect: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val wide = aspect > size.width / size.height
        val w = if (wide) size.width else size.height * aspect
        val h = if (wide) size.width / aspect else size.height
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        return Outline.Rounded(RoundRect(left, top, left + w, top + h, CornerRadius(radius)))
    }
}

/**
 * Plays the short clip inside a motion photo once, over the photo (Media3 reads the clip straight from the
 * JPEG), then calls [onEnded]. Until its first frame arrives it draws nothing, so the photo stays visible.
 */
@Composable
private fun MotionClip(item: MediaItem, muted: Boolean, onEnded: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val latestOnEnded by rememberUpdatedState(onEnded)
    val player = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(player, muted) { player.volume = if (muted) 0f else 1f }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) latestOnEnded()
            }

            override fun onPlayerError(error: PlaybackException) = latestOnEnded()
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    ContentFrame(
        player = player,
        modifier = modifier,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = ContentScale.Fit,
        keepContentOnReset = true,
        shutter = {},
    )
}
