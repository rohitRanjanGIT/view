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
 * Two-finger pinch on a scrolling grid. While two fingers are down, their events are consumed in the
 * initial pass so the list underneath neither scrolls nor sees a tap or long press. [onPinch] gets the
 * scale relative to the fingers' distance at the start (or at the last time it returned true, which
 * restarts the measurement) and their centre. [onPinchEnd] fires once, as soon as fewer than two
 * fingers remain.
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
            var startSpan = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed >= 2 && !ended) {
                    pinching = true
                    val span = event.calculateCentroidSize(useCurrent = true)
                    // The span is 0 on the frame the second finger lands (the helper only counts fingers that
                    // were already down), so the baseline is the first non-zero span.
                    if (startSpan <= 0f) {
                        startSpan = span
                    } else if (span > 0f && pinch(span / startSpan, event.calculateCentroid(useCurrent = true))) {
                        startSpan = span
                    }
                } else if (pinching && !ended) {
                    ended = true
                    pinchEnd()
                }
                // Once a pinch has started, the rest of the gesture belongs to it.
                if (pinching) event.changes.forEach { it.consume() }
            }
            if (pinching && !ended) pinchEnd()
        }
    }
}
