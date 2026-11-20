package com.rohit.gallery.core.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Past this share of the width (or with a flick), letting go goes back. */
private const val BACK_THRESHOLD = 0.3f

/**
 * iOS-style back: drag right from anywhere on the screen and it slides away with the finger, showing the screen
 * underneath; let go past [BACK_THRESHOLD] (or flick) to go back, otherwise it springs back.
 *
 * It drives Android's own predictive back with the finger (the same events an edge swipe sends), so the navigation
 * host plays the back animation it already has and every Back handler behaves as usual. Only claims a sideways drag
 * to the right with one finger that did not start as a long press (a long press then drag selects photos), and only
 * while [enabled] (not while selecting). Vertical drags and pinches are left to the grid.
 */
@Composable
fun Modifier.swipeBack(enabled: Boolean): Modifier {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher ?: return this
    val isEnabled by rememberUpdatedState(enabled)
    return pointerInput(dispatcher) {
        val slop = viewConfiguration.touchSlop
        val longPressMs = viewConfiguration.longPressTimeoutMillis
        val flingPx = 500.dp.toPx()
        awaitEachGesture {
            val width = size.width.toFloat().coerceAtLeast(1f)
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!isEnabled) return@awaitEachGesture
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var total = Offset.Zero
            var dragging = false
            fun event(x: Float, y: Float, progress: Float) = BackEventCompat(x, y, progress, BackEventCompat.EDGE_LEFT)
            while (true) {
                val pointerEvent = awaitPointerEvent(PointerEventPass.Initial)
                val change = pointerEvent.changes.firstOrNull { it.id == down.id }
                // A second finger (a pinch) or a lost pointer: put the screen back.
                if (change == null || pointerEvent.changes.count { it.pressed } > 1) {
                    if (dragging) dispatcher.dispatchOnBackCancelled()
                    return@awaitEachGesture
                }
                if (!change.pressed) {
                    if (dragging) {
                        change.consume()
                        val progress = (change.position.x - down.position.x) / width
                        if (progress > BACK_THRESHOLD || tracker.calculateVelocity().x > flingPx) {
                            dispatcher.onBackPressed()
                        } else {
                            dispatcher.dispatchOnBackCancelled()
                        }
                    }
                    return@awaitEachGesture
                }
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!dragging) {
                    total += change.positionChange()
                    if (total.getDistance() < slop) continue
                    val rightwards = total.x > 0f && abs(total.x) > abs(total.y)
                    val longPressed = change.uptimeMillis - down.uptimeMillis > longPressMs
                    if (!rightwards || longPressed) return@awaitEachGesture
                    dragging = true
                    dispatcher.dispatchOnBackStarted(event(down.position.x, down.position.y, 0f))
                }
                change.consume()
                val progress = ((change.position.x - down.position.x) / width).coerceIn(0f, 1f)
                dispatcher.dispatchOnBackProgressed(event(change.position.x, change.position.y, progress))
            }
        }
    }
}
