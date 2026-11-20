package com.rohit.gallery.core.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.ui.theme.GalleryMotion
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Past this share of the width (or with a flick), letting go goes back. */
private const val BACK_THRESHOLD = 0.3f

/**
 * iOS-style back: drag right from anywhere on the screen and it slides away with the finger, showing the screen
 * underneath; let go heading past [BACK_THRESHOLD] (or flick) to go back, otherwise it springs back. Either way a spring
 * started at the finger's speed finishes the move.
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
    val scope = rememberCoroutineScope()
    // Set while the screen glides off (or back) on its own after the finger lifts; a new touch then waits.
    val finishing = remember { object { var job: Job? = null } }
    return pointerInput(dispatcher) {
        val slop = viewConfiguration.touchSlop
        val longPressMs = viewConfiguration.longPressTimeoutMillis
        val flingPx = 500.dp.toPx()
        awaitEachGesture {
            val width = size.width.toFloat().coerceAtLeast(1f)
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!isEnabled || finishing.job?.isActive == true) return@awaitEachGesture
            fun event(x: Float, y: Float, progress: Float) = BackEventCompat(x, y, progress, BackEventCompat.EDGE_LEFT)
            /**
             * After the finger lifts at [progress] moving at [velocityX] px/s: a spring that starts at that speed carries
             * the screen off ([goBack]) or back, still seeking the back animation, so the motion never changes pace.
             * Then the back is completed (only a sliver is left) or cancelled.
             */
            fun finish(progress: Float, velocityX: Float, y: Float, goBack: Boolean) {
                finishing.job = scope.launch {
                    var finished = false
                    try {
                        animate(
                            progress, if (goBack) 1f else 0f, velocityX / width,
                            GalleryMotion.fluid(response = 0.3f, visibilityThreshold = 0.002f),
                        ) { value, _ ->
                            val p = value.coerceIn(0f, 1f)
                            dispatcher.dispatchOnBackProgressed(event(p * width, y, p))
                        }
                        finished = true
                    } finally {
                        if (goBack && finished) dispatcher.onBackPressed() else dispatcher.dispatchOnBackCancelled()
                    }
                }
            }
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var total = Offset.Zero
            var dragging = false
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
                        val progress = ((change.position.x - down.position.x) / width).coerceIn(0f, 1f)
                        val velocityX = tracker.calculateVelocity().x
                        // Where the flick is heading decides; a flick back to the left always keeps the screen.
                        val heading = progress + GalleryMotion.project(velocityX) / width
                        val goBack = velocityX > -flingPx && (heading > BACK_THRESHOLD || velocityX > flingPx)
                        finish(progress, velocityX, change.position.y, goBack)
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
