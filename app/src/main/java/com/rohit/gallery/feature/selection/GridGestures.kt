package com.rohit.gallery.feature.selection

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

private const val TAP = 0
private const val SCROLL = 1

/**
 * One gesture detector for the whole grid: tap, long-press, and drag after long-press.
 *
 * Scrolling is untouched: before the long-press fires we only watch (the list consumes the
 * events if the finger starts scrolling, and we step aside). After the long-press we take over
 * on the Initial pass and consume the movement, so the list does not scroll while you drag-select.
 *
 * Positions are in this modifier's coordinate space; the caller maps them to items.
 */
@Composable
fun Modifier.gridGestures(
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    return pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val outcome = withTimeoutOrNull(longPressMs) {
            var result = -1
            while (result < 0) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                result = when {
                    change == null || event.changes.size > 1 || change.isConsumed -> SCROLL
                    change.changedToUp() -> TAP
                    (change.position - down.position).getDistance() > slop -> SCROLL
                    else -> -1
                }
            }
            result
        }
        when (outcome) {
            TAP -> tap(down.position)
            null -> {
                // Timed out with the finger still down and still: a long press.
                longPress(down.position)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        dragEnd()
                        break
                    }
                    change.consume()
                    drag(change.position)
                }
            }
        }
    }
}
}
