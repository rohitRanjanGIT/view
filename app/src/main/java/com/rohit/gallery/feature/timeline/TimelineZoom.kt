package com.rohit.gallery.feature.timeline

import androidx.compose.animation.core.animate
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.rohit.gallery.core.ui.theme.GalleryMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Pinch past these scales (on release) to change the grid density. */
private const val ZOOM_IN_AT = 1.15f
private const val ZOOM_OUT_AT = 0.87f

/** How far the grid follows the fingers before the release decides. */
private const val MIN_LIVE_SCALE = 0.5f
private const val MAX_LIVE_SCALE = 2f

/**
 * The item the fingers were on when a pinch changed the density, where it was on screen ([y], list
 * coordinates) and how big its cells looked ([fromScale] x the old [fromColumns] grid).
 */
class ZoomAnchor(val itemId: Long, val y: Float, val fromScale: Float, val fromColumns: Int)

/**
 * Visual state of a timeline pinch: the grid is drawn scaled by [scale] around [origin] while the fingers
 * move, then the new density is laid out and eases from the size the cells had to its real size.
 * Read only in the graphics layer, so pinching never recomposes the grid.
 */
@Stable
class TimelineZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var origin by mutableStateOf(Offset.Zero)
        private set

    /** Set when a pinch committed to a new density, until that density is laid out. */
    var pending: ZoomAnchor? = null
        private set

    private var animation: Job? = null

    fun onPinch(pinchScale: Float, centroid: Offset) {
        if (pending != null) return
        animation?.cancel()
        scale = pinchScale.coerceIn(MIN_LIVE_SCALE, MAX_LIVE_SCALE)
        origin = centroid
    }

    /**
     * Decides what the pinch meant. Returns the density to switch to (after recording [anchorAt]'s item as
     * the anchor), or null after easing back to the current one.
     */
    fun onPinchEnd(
        scope: CoroutineScope,
        current: GridLevel,
        centroid: Offset,
        anchorAt: (Offset) -> Long?,
    ): GridLevel? {
        if (pending != null) return null
        val target = when {
            scale >= ZOOM_IN_AT -> current.zoomIn()
            scale <= ZOOM_OUT_AT -> current.zoomOut()
            else -> null
        }
        val anchorId = target?.let { anchorAt(centroid) }
        if (target == null || anchorId == null) {
            animateTo(scope, from = scale, to = 1f)
            return null
        }
        pending = ZoomAnchor(anchorId, centroid.y, scale, current.columns)
        return target
    }

    /**
     * Called during the first composition of a new density with the anchor's new cell centre. Snaps the
     * grid to the size its cells had at the end of the pinch, so there is no jump, and returns the anchor.
     */
    fun takeAnchor(newColumns: Int, anchorCentre: (ZoomAnchor) -> Offset?): ZoomAnchor? {
        val anchor = pending ?: return null
        pending = null
        anchorCentre(anchor)?.let { origin = it }
        scale = anchor.fromScale * newColumns / anchor.fromColumns
        return anchor
    }

    /** Eases whatever scale the grid is at back to its real size. */
    fun settle(scope: CoroutineScope) {
        if (scale != 1f) animateTo(scope, from = scale, to = 1f)
    }

    private fun animateTo(scope: CoroutineScope, from: Float, to: Float) {
        animation?.cancel()
        animation = scope.launch {
            animate(from, to, animationSpec = GalleryMotion.snappy()) { value, _ -> scale = value }
        }
    }
}

/** Draws the grid at the zoom state's live scale. */
fun Modifier.zoomLayer(zoom: TimelineZoomState): Modifier = graphicsLayer {
    val s = zoom.scale
    scaleX = s
    scaleY = s
    if (size.width > 0f && size.height > 0f) {
        transformOrigin = TransformOrigin(zoom.origin.x / size.width, zoom.origin.y / size.height)
    }
}

/**
 * After a pinch changed the density: during the first composition of the new layout, scrolls so the
 * anchor item's row sits where the fingers were, then eases the grid to its real size.
 */
@Composable
fun KeepZoomAnchor(
    zoom: TimelineZoomState,
    content: TimelineUiState.Content,
    listState: LazyListState,
    map: TimelineScrollMap,
    rowPx: Int,
    cellSizePx: Int,
    gapPx: Int,
) {
    val shownLevel = remember { object { var level = content.level } }
    if (shownLevel.level != content.level) {
        shownLevel.level = content.level
        zoom.takeAnchor(content.columns) { anchor ->
            val position = content.indexById[anchor.itemId]?.let { gridPositionOf(content.days, it) }
                ?: return@takeAnchor null
            val rowTop = map.offsetOf(position.lazyIndex, 0)
            val top = (rowTop + rowPx / 2 - anchor.y.toLong()).coerceAtLeast(0L)
            val (index, offset) = map.positionAt(top)
            listState.requestScrollToItem(index, offset)
            Offset(
                x = position.column * (cellSizePx + gapPx) + cellSizePx / 2f,
                y = anchor.y,
            )
        }
    }
    // Runs after the first frame of the new layout, which is drawn at the snapped scale.
    LaunchedEffect(content.level) { zoom.settle(this) }
}
