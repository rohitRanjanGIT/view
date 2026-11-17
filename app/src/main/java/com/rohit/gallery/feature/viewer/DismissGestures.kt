package com.rohit.gallery.feature.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/** Downward drags may be fairly diagonal (a swipe from a top corner still closes the viewer). */
private const val DOWN_RATIO = 0.8f

/** Upward drags (open the info sheet) must be clearly vertical so they never fight the pager. */
private const val UP_RATIO = 1.2f

/**
 * Claims one-finger drags that are mostly vertical *before* the pager or zoomable image see them
 * (the Initial pass runs parent-first), so a diagonal swipe down closes the viewer instead of
 * changing the photo. Mostly horizontal drags are left alone for the pager. Does nothing while
 * the image is zoomed in (the zoomable image pans then) or when a second finger lands (pinch).
 *
 * [onDrag] gets the finger movement since the last event; [onEnd] gets the total movement and the
 * release velocity (px/s); [onCancel] fires if a second finger interrupts a claimed drag.
 */
fun Modifier.verticalDismissGestures(
    enabled: () -> Boolean,
    onDrag: (delta: Offset) -> Unit,
    onEnd: (total: Offset, velocityX: Float, velocityY: Float) -> Unit,
    onCancel: () -> Unit,
): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabled()) return@awaitEachGesture

        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var total = Offset.Zero
        var claimed = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || event.changes.size > 1) {
                if (claimed) onCancel()
                return@awaitEachGesture
            }
            if (change.changedToUpIgnoreConsumed()) {
                if (claimed) {
                    val v = tracker.calculateVelocity()
                    onEnd(total, v.x, v.y)
                }
                return@awaitEachGesture
            }

            val delta = change.positionChange()
            total += delta
            tracker.addPosition(change.uptimeMillis, change.position)

            if (!claimed) {
                if (total.getDistance() > slop) {
                    val dx = abs(total.x)
                    val dy = abs(total.y)
                    val vertical = if (total.y > 0) dy >= dx * DOWN_RATIO else dy > dx * UP_RATIO
                    if (!vertical) return@awaitEachGesture
                    claimed = true
                    onDrag(total)
                    change.consume()
                }
            } else {
                onDrag(delta)
                change.consume()
            }
        }
    }
}
