package com.rohit.gallery.feature.selection

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import com.rohit.gallery.core.ui.theme.GalleryMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAP = 0
private const val SCROLL = 1

/**
 * How long the finger must rest before a cell shows it is pressed. Like a scroll view's content-touch delay: short
 * enough to read as instant, long enough that starting a scroll doesn't flash a cell.
 */
private const val PRESS_DELAY_MS = 70L

/** How far a held cell sinks by the time the long-press fires. */
private const val HELD_SCALE = 0.92f

/**
 * One gesture detector for the whole grid: press, tap, long-press, and drag after long-press.
 *
 * Scrolling is untouched: before the long-press fires we only watch (the list consumes the
 * events if the finger starts scrolling, and we step aside). After the long-press we take over
 * on the Initial pass and consume the movement, so the list does not scroll while you drag-select.
 *
 * [onPress] fires once the finger has rested briefly on the grid (with how long until the long-press), and
 * [onPressEnd] when that touch becomes a tap, a scroll or a long-press, or is cancelled.
 *
 * Positions are in this modifier's coordinate space; the caller maps them to items.
 */
@Composable
fun Modifier.gridGestures(
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onPress: (position: Offset, untilLongPressMs: Long) -> Unit = { _, _ -> },
    onPressEnd: () -> Unit = {},
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val press by rememberUpdatedState(onPress)
    val pressEnd by rememberUpdatedState(onPressEnd)
    return pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Tap or scroll before the press shows; then the rest of the long-press wait with the cell pressed.
        var outcome = withTimeoutOrNull(PRESS_DELAY_MS) { awaitTapOrScroll(down, slop) }
        if (outcome == null) {
            press(down.position, longPressMs - PRESS_DELAY_MS)
            try {
                outcome = withTimeoutOrNull(longPressMs - PRESS_DELAY_MS) { awaitTapOrScroll(down, slop) }
            } finally {
                pressEnd()
            }
        }
        when (outcome) {
            TAP -> tap(down.position)
            null -> {
                // Timed out with the finger still down and still: a long press.
                longPress(down.position)
                // finally: even if the gesture is cancelled mid-drag, the drag (and its edge
                // auto-scroll) must end, or the list keeps scrolling by itself.
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        change.consume()
                        drag(change.position)
                    }
                } finally {
                    dragEnd()
                }
            }
        }
    }
}
}

private suspend fun AwaitPointerEventScope.awaitTapOrScroll(down: PointerInputChange, slop: Float): Int {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id }
        when {
            change == null || event.changes.size > 1 || change.isConsumed -> return SCROLL
            change.changedToUp() -> return TAP
            (change.position - down.position).getDistance() > slop -> return SCROLL
        }
    }
}

/**
 * The grid cell under a resting finger. It sinks a little at once and keeps sinking while held, hinting at the
 * long-press to come, then springs back (with a little give) when the touch ends. Only the pressed cell reads the
 * scale, and only inside its graphics layer, so pressing never recomposes the grid.
 */
@Stable
class GridPressState(private val scope: CoroutineScope) {
    var pressedId by mutableStateOf<Long?>(null)
        private set
    internal val scale = Animatable(1f)
    private var job: Job? = null

    fun press(id: Long, untilLongPressMs: Long) {
        job?.cancel()
        pressedId = id
        job = scope.launch {
            scale.snapTo(1f)
            scale.animateTo(HELD_SCALE, tween(untilLongPressMs.toInt().coerceAtLeast(1), easing = LinearOutSlowInEasing))
        }
    }

    fun release() {
        if (pressedId == null) return
        job?.cancel()
        job = scope.launch {
            scale.animateTo(1f, GalleryMotion.dragReturn())
            pressedId = null
        }
    }
}

@Composable
fun rememberGridPressState(): GridPressState {
    val scope = rememberCoroutineScope()
    return remember(scope) { GridPressState(scope) }
}

/** Scales this cell while it is the pressed one (see [GridPressState]). */
fun Modifier.pressScale(press: GridPressState, id: Long): Modifier = graphicsLayer {
    if (press.pressedId == id) {
        val s = press.scale.value
        scaleX = s
        scaleY = s
    }
}
