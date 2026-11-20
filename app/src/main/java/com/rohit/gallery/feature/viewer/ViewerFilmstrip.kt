package com.rohit.gallery.feature.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.SeekParameters
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.MediaThumbnail
import com.rohit.gallery.core.ui.theme.GalleryMotion
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val ItemWidth = 40.dp
private val ItemHeight = 56.dp
private val ItemGap = 2.dp
private val ItemShape = RoundedCornerShape(6.dp)

/**
 * How much bigger a thumbnail is drawn, by its distance from the centre (in items): the centred one most, the two on
 * each side a little less, the rest flat. Between whole steps it blends, so the step slides smoothly with the strip.
 */
private val PopSteps = floatArrayOf(0.22f, 0.12f, 0.05f, 0f)

/**
 * Strip of small thumbnails under the viewer, and a video's timeline (One UI style).
 *
 * Everything sits on one line, and one number says which point of it is under the centre: the big photo's position
 * (followed live, also mid-swipe), or the finger's while the strip is dragged or gliding. Dragging moves only the strip,
 * 1:1; the big photo shows the centred item's cached thumbnail ([onScrub]) and the full photo opens once the strip
 * lands ([onSelect]). A flick glides on at the finger's speed and a spring lands it on a thumbnail.
 *
 * On a video, tapping its thumbnail opens it up from the centre into its frames, pushing the neighbours aside. Then the
 * centre line is the playhead: the strip moves with the video, dragging inside the frames scrubs it, and dragging past
 * either end carries on into the neighbours (landing on one moves there and closes the video again). Tap again, or
 * swipe the big photo, to close it. Positions and sizes are applied in layout and drawing, so moving never recomposes.
 */
@Composable
fun ViewerFilmstrip(
    items: List<MediaItem>,
    pagerState: PagerState,
    playback: PlaybackState?,
    onScrub: (Int?) -> Unit,
    onSelect: suspend (index: Int, animate: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val itemW = with(density) { ItemWidth.toPx() }
    val stride = with(density) { (ItemWidth + ItemGap).toPx() }
    val itemH = with(density) { ItemHeight.toPx() }
    val frameW = with(density) { FrameWidth.toPx() }
    val stripH = itemH * (1f + PopSteps[0])
    val halfScreen = LocalWindowInfo.current.containerSize.width / 2f
    // Sharp enough for the popped-up centre thumbnail too.
    val cellPx = stripH.roundToInt()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val frameStore = remember { VideoFrameStore(context.applicationContext, scope, itemH.roundToInt()) }
    val latestItems by rememberUpdatedState(items)
    val latestPlayback by rememberUpdatedState(playback)
    val latestOnScrub by rememberUpdatedState(onScrub)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val current = pagerState.currentPage

    // The video shown as frames (also while it closes), how wide its frames are, and how far open it is (0..1).
    var openIndex by remember { mutableStateOf<Int?>(null) }
    var openWidth by remember { mutableFloatStateOf(0f) }
    var wantOpen by remember { mutableStateOf(false) }
    val open = remember { Animatable(0f) }
    LaunchedEffect(current) { if (openIndex != null && openIndex != current) wantOpen = false }
    LaunchedEffect(wantOpen) {
        open.animateTo(if (wantOpen) 1f else 0f, GalleryMotion.fluid(response = 0.35f))
        if (!wantOpen) openIndex = null
    }

    // The video's time, 0..1: every frame while it plays open, otherwise from the player's periodic position.
    var live by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playback, playback?.isPlaying, wantOpen) {
        val p = playback ?: return@LaunchedEffect
        fun read() {
            val d = p.player.duration
            if (d > 0) live = (p.player.currentPosition.toFloat() / d).coerceIn(0f, 1f)
        }
        read()
        while (p.isPlaying && wantOpen) withFrameNanos { read() }
    }
    LaunchedEffect(playback?.positionMs) {
        val p = playback ?: return@LaunchedEffect
        if (!p.isPlaying && p.durationMs > 0) live = (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f)
    }

    // Geometry along the line, in px. The open video takes its frames' width; the items after it move along.
    fun extra(): Float = if (openIndex == null) 0f else (openWidth - itemW).coerceAtLeast(0f) * open.value
    fun left(i: Int): Float = i * stride + (openIndex?.let { if (i > it) extra() else 0f } ?: 0f)
    fun width(i: Int): Float = if (i == openIndex) itemW + extra() else itemW
    fun centre(i: Int): Float = left(i) + width(i) / 2
    // Point on the line -> position in items (2.5 = halfway between items 2 and 3); across the open video it moves
    // through its one item, so the size step and the "centred item" stay right.
    fun indexAt(x: Float): Float {
        val v = openIndex ?: return (x - itemW / 2) / stride
        val e = extra()
        val start = left(v)
        return when {
            e <= 0f || x < start -> (x - itemW / 2) / stride
            x <= start + itemW + e -> v - itemW / (2 * stride) + (x - start) / (itemW + e) * (itemW / stride)
            else -> (x - e - itemW / 2) / stride
        }
    }
    fun isOpenCurrent() = openIndex != null && openIndex == pagerState.currentPage
    fun playheadOf(v: Int): Float = left(v) + live * width(v)

    // The point under the centre line: the finger's, or the big photo's (blended to the playhead as a video opens).
    var lead by remember { mutableStateOf<Float?>(null) }
    fun followed(): Float {
        val pos = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceAtLeast(0f)
        val i = floor(pos).toInt()
        val f = pos - i
        val base = centre(i) + (centre(i + 1) - centre(i)) * f
        return if (isOpenCurrent()) base + (playheadOf(openIndex!!) - base) * open.value else base
    }
    fun centreX(): Float = lead ?: followed()

    // Swiping the big photo closes an open video (the strip follows the photo again).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress && lead == null }.collect { if (it) wantOpen = false }
    }

    // Only the items near the screen are composed; this changes only when one comes into or out of view.
    val range by remember {
        derivedStateOf {
            val x = centreX()
            val first = floor(indexAt(x - halfScreen)).toInt() - 2
            val last = ceil(indexAt(x + halfScreen)).toInt() + 2
            first.coerceAtLeast(0)..last.coerceAtMost(latestItems.lastIndex)
        }
    }

    // Dragging and gliding.
    val drag = remember {
        object {
            var video = false; var wasPlaying = false; var centred = -1; var glide: Job? = null
            // [raw]: where the finger would put the strip; [walled]: the drag started inside an open video, which then
            // keeps it (past its ends the strip stretches and springs back); [atEdge]: for one buzz per edge reached.
            var raw = 0f; var walled = false; var atEdge = false
        }
    }
    // True only while the strip glides on its own: then a touch grabs it at once (state, as draggable reads it in composition).
    var gliding by remember { mutableStateOf(false) }
    fun inOpenVideo(x: Float): Boolean {
        val v = openIndex ?: return false
        return wantOpen && v == pagerState.currentPage && latestPlayback != null && x >= left(v) && x <= left(v) + width(v)
    }
    fun leaveVideo(seekTo: Float?) {
        val p = latestPlayback
        if (!drag.video || p == null) return
        drag.video = false
        p.player.setSeekParameters(SeekParameters.EXACT)
        if (seekTo != null) {
            p.seekTo((seekTo * p.durationMs).toLong())
            live = seekTo
        }
        if (drag.wasPlaying) p.player.play()
    }
    // An open video's frames as a wall: inside them the finger's position, past them a stretch that resists more and
    // more (and one buzz on reaching the end).
    fun walledAt(raw: Float): Float {
        val v = openIndex ?: return raw
        val start = left(v)
        val end = start + width(v)
        val edge = raw < start || raw > end
        if (edge && !drag.atEdge) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        drag.atEdge = edge
        return when {
            raw < start -> start + GalleryMotion.rubberBand(raw - start, halfScreen)
            raw > end -> end + GalleryMotion.rubberBand(raw - end, halfScreen)
            else -> raw
        }
    }
    fun moveTo(x: Float) {
        lead = x
        val p = latestPlayback
        if (p != null && (drag.walled || inOpenVideo(x))) {
            if (!drag.video) {
                drag.video = true
                drag.wasPlaying = p.player.isPlaying
                p.player.pause()
                // Keyframes only while moving: fast enough to follow the finger.
                p.player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
            val v = openIndex!!
            val t = ((x - left(v)) / width(v)).coerceIn(0f, 1f)
            live = t
            p.player.seekTo((t * p.durationMs).toLong())
        }
        // Held inside the video: the neighbours are not reached, so no ticks and no stand-in photos.
        if (drag.walled) return
        val centred = indexAt(x).roundToInt().coerceIn(0, latestItems.lastIndex)
        if (centred != drag.centred) {
            // A light tick for each item passed, like the detents of a dial.
            if (drag.centred != -1) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            drag.centred = centred
            latestOnScrub(centred)
        }
    }
    suspend fun land(x: Float) {
        if (inOpenVideo(x)) {
            val v = openIndex!!
            leaveVideo(((x - left(v)) / width(v)).coerceIn(0f, 1f))
        } else {
            val index = indexAt(x).roundToInt().coerceIn(0, latestItems.lastIndex)
            leaveVideo(null)
            if (index != pagerState.currentPage) latestOnSelect(index, false)
        }
        latestOnScrub(null)
        drag.centred = -1
        drag.walled = false
        drag.atEdge = false
        lead = null
    }
    val dragState = rememberDraggableState { delta ->
        drag.raw -= delta
        moveTo(if (drag.walled) walledAt(drag.raw) else drag.raw)
    }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { stripH.toDp() })
            // Thumbnails are placed along the whole line; anything past the strip's own edges is cut off (in landscape the
            // strip ends at the action rail).
            .clipToBounds()
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                startDragImmediately = gliding,
                onDragStarted = {
                    drag.glide?.cancel()
                    if (lead == null) {
                        lead = centreX()
                        drag.centred = indexAt(lead!!).roundToInt()
                        drag.walled = inOpenVideo(lead!!)
                    }
                    drag.raw = lead!!
                },
                onDragStopped = { velocity ->
                    val from = lead ?: centreX()
                    val projected = from + GalleryMotion.project(-velocity)
                    val target = when {
                        // Inside an open video the flick ends at its first or last frame at most.
                        drag.walled -> openIndex?.let { projected.coerceIn(left(it), left(it) + width(it)) } ?: projected
                        inOpenVideo(projected) -> projected
                        else -> {
                            val index = indexAt(projected).roundToInt().coerceIn(0, latestItems.lastIndex)
                            // Heading for the open video's own slot: stay inside its frames.
                            if (index == openIndex && inOpenVideo(centre(index))) {
                                projected.coerceIn(left(index), left(index) + width(index))
                            } else {
                                centre(index)
                            }
                        }
                    }
                    drag.glide = scope.launch {
                        var landed = false
                        gliding = true
                        try {
                            animate(from, target, -velocity, GalleryMotion.fluid(response = 0.3f)) { x, _ ->
                                drag.raw = x
                                moveTo(x)
                            }
                            landed = true
                        } finally {
                            gliding = false
                            // A new touch took over (it carries on from here); otherwise land.
                            if (landed) land(target)
                        }
                    }
                },
            )
            .drawWithContent {
                drawContent()
                // The playhead, while a video is open.
                if (isOpenCurrent() && open.value > 0f) {
                    val lineWidth = 3.dp.toPx()
                    drawRoundRect(
                        Color.White.copy(alpha = open.value),
                        topLeft = Offset(size.width / 2 - lineWidth / 2, 0f),
                        size = Size(lineWidth, size.height),
                        cornerRadius = CornerRadius(lineWidth / 2),
                    )
                }
            },
        content = {
            for (i in range) {
                val item = items[i]
                key(item.id) {
                    val isCurrentVideo = i == current && item.isVideo && playback != null
                    val tap = Modifier.clickable {
                        if (isCurrentVideo) {
                            if (!wantOpen || openIndex != i) {
                                if (openIndex != i) scope.launch { open.snapTo(0f) }
                                openIndex = i
                                openWidth = frameCountFor(playback?.durationMs ?: 0L) * frameW
                                wantOpen = true
                            } else {
                                wantOpen = false
                            }
                        } else {
                            scope.launch { latestOnSelect(i, true) }
                        }
                    }
                    if (i == openIndex) {
                        val duration = (if (i == current) playback?.durationMs else null) ?: 0L
                        OpenVideoItem(
                            item = item,
                            frames = frameStore.frames(item, (openWidth / frameW).roundToInt().coerceAtLeast(1), duration, startFraction = { live }),
                            cellPx = cellPx,
                            open = { open.value },
                            playheadX = { centreX() - left(i) },
                            modifier = tap,
                        )
                    } else {
                        StripThumbnail(item, cellPx, Modifier.clip(ItemShape).then(tap))
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val first = range.first
        val x = centreX()
        val at = indexAt(x)
        val placeables = measurables.mapIndexed { j, measurable ->
            val i = first + j
            val h = if (i == openIndex) itemH + (stripH - itemH) * open.value else itemH
            measurable.measure(Constraints.fixed(width(i).roundToInt().coerceAtLeast(1), h.roundToInt()))
        }
        layout(constraints.maxWidth, stripH.roundToInt()) {
            val mid = constraints.maxWidth / 2f
            placeables.forEachIndexed { j, placeable ->
                val i = first + j
                val distance = i - at
                val pop = 1f + popExtra(abs(distance))
                // An open video is drawn at its real size (it is already taller); closed items pop by distance.
                val scale = if (i == openIndex) pop + (1f - pop) * open.value else pop
                placeable.placeWithLayer((left(i) - x + mid).roundToInt(), (stripH - placeable.height).roundToInt()) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    // Pushed aside by the bigger ones nearer the centre, so they never overlap.
                    translationX = sign(distance) * itemW * popShift(abs(distance))
                }
            }
        }
    }
}

/** A thumbnail in the strip; a video gets a small play badge so it stands out among photos. */
@Composable
private fun StripThumbnail(item: MediaItem, cellPx: Int, modifier: Modifier) {
    Box(modifier) {
        MediaThumbnail(item = item, cellSizePx = cellPx, showBadge = false, modifier = Modifier.fillMaxSize())
        if (item.isVideo) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(14.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(1.dp),
            )
        }
    }
}

/** A video in the strip while open: its thumbnail fades into its frames as it widens. */
@Composable
private fun OpenVideoItem(
    item: MediaItem,
    frames: List<androidx.compose.ui.graphics.ImageBitmap?>,
    cellPx: Int,
    open: () -> Float,
    playheadX: () -> Float,
    modifier: Modifier,
) {
    val thumbnail = rememberThumbnail(item)
    Box(modifier.clip(ItemShape)) {
        MediaThumbnail(item = item, cellSizePx = cellPx, showBadge = false, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - open() })
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = open() }) {
            drawVideoFrames(frames, thumbnail, playheadX())
        }
    }
}

/** Extra size (share of the item) at [distance] items from the centre, blended between [PopSteps]. */
private fun popExtra(distance: Float): Float {
    val step = floor(distance).toInt()
    if (step >= PopSteps.lastIndex) return 0f
    val f = distance - step
    return PopSteps[step] + (PopSteps[step + 1] - PopSteps[step]) * f
}

/**
 * How far (share of the item width) a thumbnail at [distance] moves outward: half the extra width of every thumbnail
 * between it and the centre, both sides. The extra size is linear between steps, so this is its exact integral.
 */
private fun popShift(distance: Float): Float {
    var shift = 0f
    var step = 0
    while (step < PopSteps.lastIndex && step + 1 <= distance) {
        shift += (PopSteps[step] + PopSteps[step + 1]) / 2
        step++
    }
    if (step < PopSteps.lastIndex) {
        val f = distance - step
        shift += PopSteps[step] * f + (PopSteps[step + 1] - PopSteps[step]) * f * f / 2
    }
    return shift
}
