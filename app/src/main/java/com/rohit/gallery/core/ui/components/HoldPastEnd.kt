package com.rohit.gallery.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** How long the push past the end has to be held. */
private const val HOLD_MS = 1_200L

/** How far past the end the finger has to push before the hold counts. */
private val PushDistance = 64.dp

/**
 * A hidden gesture on a scrolling list: at the end of the list, keep pushing up past it (the list stretches) and hold
 * for [HOLD_MS]: a buzz, then [onHold]. No tick before that, so nothing hints that the gesture exists. Lifting the finger or moving back down cancels. Only
 * watches what the list could not scroll (it never takes a scroll from it), so scrolling and the stretch are as usual.
 * Null [onHold] turns it off. (The Albums tab's way into Locked.)
 */
@Composable
fun Modifier.holdPastEnd(onHold: (() -> Unit)?): Modifier {
    if (onHold == null) return this
    val haptic = LocalHapticFeedback.current
    val thresholdPx = with(LocalDensity.current) { PushDistance.toPx() }
    val latestHold by rememberUpdatedState(onHold)
    val tracker = remember { PastEndTracker() }
    val holding = tracker.pushed >= thresholdPx
    LaunchedEffect(holding) {
        if (!holding) return@LaunchedEffect
        delay(HOLD_MS)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        tracker.reset()
        latestHold()
    }
    return nestedScroll(tracker)
}

private class PastEndTracker : NestedScrollConnection {
    /** How far the finger has pushed up past the end in this drag, in pixels. */
    var pushed by mutableFloatStateOf(0f)
        private set

    fun reset() {
        pushed = 0f
    }

    // Moving back down takes off what was pushed (the list scrolls back first, so only report, never consume).
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0f && pushed > 0f) pushed = (pushed - available.y).coerceAtLeast(0f)
        return Offset.Zero
    }

    // Upward drag the list couldn't use: it is at its end.
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y < 0f) {
            pushed += -available.y
        }
        return Offset.Zero
    }

    // The finger lifted.
    override suspend fun onPreFling(available: Velocity): Velocity {
        reset()
        return Velocity.Zero
    }
}
