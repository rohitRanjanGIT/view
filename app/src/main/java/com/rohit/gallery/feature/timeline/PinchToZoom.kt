package com.rohit.gallery.feature.timeline

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
 * Two-finger pinch on a scrolling grid. While two fingers are down, their events are consumed in the
 * initial pass so the list underneath neither scrolls nor sees a tap or long press. [onPinch] gets the
 * scale relative to the fingers' starting distance and their centre; [onPinchEnd] fires once, as soon
 * as fewer than two fingers remain.
 */
@Composable
fun Modifier.pinchToZoom(
    onPinch: (scale: Float, centroid: Offset) -> Unit,
    onPinchEnd: (scale: Float, centroid: Offset) -> Unit,
): Modifier {
    val pinch by rememberUpdatedState(onPinch)
    val pinchEnd by rememberUpdatedState(onPinchEnd)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            var ended = false
            var startSpan = 0f
            var scale = 1f
            var centroid = Offset.Zero
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed >= 2 && !ended) {
                    val span = event.calculateCentroidSize(useCurrent = true)
                    centroid = event.calculateCentroid(useCurrent = true)
                    if (!pinching) {
                        pinching = true
                        startSpan = span
                    } else if (startSpan > 0f) {
                        scale = span / startSpan
                        pinch(scale, centroid)
                    }
                } else if (pinching && !ended) {
                    ended = true
                    pinchEnd(scale, centroid)
                }
                // Once a pinch has started, the rest of the gesture belongs to it.
                if (pinching) event.changes.forEach { it.consume() }
            }
            if (pinching && !ended) pinchEnd(scale, centroid)
        }
    }
}
