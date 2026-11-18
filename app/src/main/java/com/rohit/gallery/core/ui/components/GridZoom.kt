package com.rohit.gallery.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import com.rohit.gallery.core.ui.theme.GalleryMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Pinch scale (since the last switch) at which the grid switches density, while the fingers are still down. */
private const val ZOOM_IN_AT = 1.1f
private const val ZOOM_OUT_AT = 0.9f

/** How far the grid follows the fingers. Past the last level it only gives a little (rubber band). */
private const val MIN_LIVE_SCALE = 0.6f
private const val MAX_LIVE_SCALE = 1.6f
private const val RUBBER_BAND = 0.25f

private const val SWITCH_MS = 220

/** The cell a pinch was on: its media id and its centre in the list's (unscaled) layout coordinates. */
class ZoomAnchorCell(val itemId: Long, val centre: Offset)

/**
 * Visual state of a pinch on a photo grid (timeline or a flat grid). Every transform is `screen = offset + scale * layoutPoint`.
 *
 * While the fingers move, the grid follows them (pivot = the fingers' centre). Past a threshold it switches
 * density at once: a snapshot of the old grid is laid over the new one, the new layout is scrolled so the
 * anchor cell is near the fingers, and both layers then scale together (so the anchor cell stays one cell)
 * while the snapshot fades out. Pinching on after a switch keeps going to the next density.
 *
 * Only graphics-layer code reads these values, so pinching never recomposes the grid.
 */
@Stable
class GridZoomState {
    var gridScale by mutableFloatStateOf(1f)
        private set
    var gridOffset by mutableStateOf(Offset.Zero)
        private set

    /** The old grid during a switch, drawn over the new one. */
    var overlay by mutableStateOf<ImageBitmap?>(null)
        private set
    var overlayScale by mutableFloatStateOf(1f)
        private set
    var overlayOffset by mutableStateOf(Offset.Zero)
        private set
    var overlayAlpha by mutableFloatStateOf(1f)
        private set

    /**
     * A switch is in progress: from commit until its animation ends. The grid doesn't follow the fingers
     * meanwhile, but their movement still counts towards the next switch, so a fast pinch chains switches.
     */
    private var busy = false

    /** Pinch scale shown as 1 (the grid's real size); reset after a switch so the grid never jumps. */
    private var displayBase = 1f
    private var resetDisplayBase = false
    private var pending: Pending? = null
    private var transition: Transition? = null
    private var lastPivot = Offset.Zero
    private var job: Job? = null

    private class Pending(val cell: ZoomAnchorCell, val screenCentre: Offset, val fromScale: Float, val fromColumns: Int)

    private class Transition(
        val startScale: Float,
        val startCentre: Offset,
        val endCentre: Offset,
        val newAnchor: Offset,
        val oldAnchor: Offset,
        val oldToNew: Float,
    )

    /**
     * Follows the fingers and switches density past a threshold. Returns true when the pinch should measure
     * from here on (at each switch), so the next switch needs a further [ZOOM_IN_AT] / [ZOOM_OUT_AT].
     */
    fun onPinch(
        scale: Float,
        centroid: Offset,
        columns: Int,
        canZoomIn: Boolean,
        canZoomOut: Boolean,
        scope: CoroutineScope,
        snapshot: suspend () -> ImageBitmap?,
        anchorAt: (Offset) -> ZoomAnchorCell?,
        onSwitch: (zoomIn: Boolean) -> Unit,
    ): Boolean {
        if (busy) return false
        if (resetDisplayBase) {
            resetDisplayBase = false
            displayBase = scale
        }
        job?.cancel()
        val live = scale / displayBase
        val shown = when {
            live > 1f && !canZoomIn -> 1f + (live - 1f) * RUBBER_BAND
            live < 1f && !canZoomOut -> 1f + (live - 1f) * RUBBER_BAND
            else -> live.coerceIn(MIN_LIVE_SCALE, MAX_LIVE_SCALE)
        }
        setPinch(shown, centroid)

        val zoomIn = when {
            scale >= ZOOM_IN_AT && canZoomIn -> true
            scale <= ZOOM_OUT_AT && canZoomOut -> false
            else -> return false
        }
        // The pivot stays put under scaling, so the screen point under the fingers is also its layout point.
        val cell = anchorAt(centroid) ?: return false

        busy = true
        displayBase = 1f
        val fromOffset = gridOffset
        scope.launch {
            val image = runCatching { snapshot() }.getOrNull()
            pending = Pending(cell, fromOffset + cell.centre * shown, shown, columns)
            overlayScale = shown
            overlayOffset = fromOffset
            overlayAlpha = 1f
            overlay = image
            onSwitch(zoomIn)
        }
        return true
    }

    /** Fingers lifted: if no switch is running, ease the grid back to its real size. */
    fun onPinchEnd(scope: CoroutineScope) {
        if (busy || gridScale == 1f) return
        val pivot = lastPivot
        job?.cancel()
        job = scope.launch {
            animate(gridScale, 1f, animationSpec = GalleryMotion.snappy()) { value, _ -> setPinch(value, pivot) }
        }
    }

    /**
     * During the first composition of the new density: [newCentreOf] scrolls the new layout and returns where
     * the anchor cell's centre is in it (layout coordinates), or null if the item is gone. Sets the first
     * frame to match how the old grid looked when the switch happened.
     */
    fun startSwitch(newColumns: Int, newCentreOf: (itemId: Long, preferredY: Float) -> Offset?) {
        val p = pending ?: return
        pending = null
        val newAnchor = newCentreOf(p.cell.itemId, p.screenCentre.y)
        if (newAnchor == null) {
            transition = null
            return
        }
        // Equal cell size on screen: fromScale / fromColumns == startScale / newColumns.
        val startScale = p.fromScale * newColumns / p.fromColumns
        transition = Transition(
            startScale = startScale,
            startCentre = p.screenCentre,
            endCentre = newAnchor,
            newAnchor = newAnchor,
            oldAnchor = p.cell.centre,
            oldToNew = p.fromColumns.toFloat() / newColumns,
        ).also { applyProgress(it, 0f) }
    }

    /** After the first frame of a new density: runs the switch animation (or just resets). */
    fun finishSwitch(scope: CoroutineScope) {
        val t = transition
        transition = null
        job?.cancel()
        if (t == null) {
            overlay = null
            setPinch(1f, Offset.Zero)
            busy = false
            resetDisplayBase = true
            return
        }
        job = scope.launch {
            try {
                animate(0f, 1f, animationSpec = tween(SWITCH_MS, easing = FastOutSlowInEasing)) { p, _ ->
                    applyProgress(t, p)
                }
            } finally {
                overlay = null
                setPinch(1f, Offset.Zero)
                busy = false
                resetDisplayBase = true
            }
        }
    }

    private fun applyProgress(t: Transition, p: Float) {
        val scale = lerp(t.startScale, 1f, p)
        val centre = lerp(t.startCentre, t.endCentre, p)
        gridScale = scale
        gridOffset = centre - t.newAnchor * scale
        val oldScale = scale * t.oldToNew
        overlayScale = oldScale
        overlayOffset = centre - t.oldAnchor * oldScale
        overlayAlpha = 1f - p
    }

    private fun setPinch(scale: Float, pivot: Offset) {
        lastPivot = pivot
        gridScale = scale
        gridOffset = pivot * (1f - scale)
    }
}

/** Draws the grid with the zoom state's live transform. */
fun Modifier.zoomLayer(zoom: GridZoomState): Modifier = graphicsLayer {
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = zoom.gridScale
    scaleY = zoom.gridScale
    translationX = zoom.gridOffset.x
    translationY = zoom.gridOffset.y
}

/** The old grid's snapshot during a density switch, over the new grid. Draws nothing otherwise. */
@Composable
fun ZoomOverlay(zoom: GridZoomState) {
    val image = zoom.overlay ?: return
    Spacer(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = zoom.overlayScale
                scaleY = zoom.overlayScale
                translationX = zoom.overlayOffset.x
                translationY = zoom.overlayOffset.y
                alpha = zoom.overlayAlpha
            }
            .drawBehind { drawImage(image) },
    )
}
