package com.rohit.gallery.core.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * How long two fingers must be down, with no third, before a pinch starts. System three-finger gestures (the
 * three-finger swipe-down screenshot) land their fingers a few ms apart; without this wait the first two would already
 * be zooming the grid when the third arrives. Short enough not to be felt in a real pinch.
 */
private const val PINCH_SETTLE_MS = 100L

/**
 * Two-finger pinch on a scrolling grid. While two fingers are down, their events are consumed in the
 * initial pass so the list underneath neither scrolls nor sees a tap or long press. [onPinch] gets the
 * scale relative to the fingers' distance at the start (or at the last time it returned true, which
 * restarts the measurement) and their centre. [onPinchEnd] fires once, as soon as fewer than two
 * fingers remain.
 *
 * Three or more fingers are never a pinch: such a gesture is left alone for its whole length (a pinch already
 * under way ends, and the grid settles back).
 */
@Composable
fun Modifier.pinchToZoom(
    onPinch: (scale: Float, centroid: Offset) -> Boolean,
    onPinchEnd: () -> Unit,
): Modifier {
    val pinch by rememberUpdatedState(onPinch)
    val pinchEnd by rememberUpdatedState(onPinchEnd)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            var ended = false
            var ignored = false
            var startSpan = 0f
            var twoSinceMs = -1L
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                val now = event.changes.maxOf { it.uptimeMillis }
                if (pressed >= 3 && !ignored) {
                    // A system three-finger gesture, not a pinch.
                    ignored = true
                    if (pinching && !ended) {
                        ended = true
                        pinchEnd()
                    }
                }
                if (pressed == 2 && !ended && !ignored) {
                    if (twoSinceMs < 0) twoSinceMs = now
                    if (pinching || now - twoSinceMs >= PINCH_SETTLE_MS) {
                        val span = event.calculateCentroidSize(useCurrent = true)
                        if (!pinching) {
                            // The baseline is taken when the pinch starts, so the wait never shows up as a jump.
                            pinching = true
                            startSpan = span
                        } else if (startSpan <= 0f) {
                            startSpan = span
                        } else if (span > 0f && pinch(span / startSpan, event.calculateCentroid(useCurrent = true))) {
                            startSpan = span
                        }
                    }
                } else if (pressed < 2 && pinching && !ended) {
                    ended = true
                    pinchEnd()
                }
                // Once a pinch has started, the rest of the gesture belongs to it.
                if (pinching && !ignored) event.changes.forEach { it.consume() }
            }
            if (pinching && !ended) pinchEnd()
        }
    }
}
