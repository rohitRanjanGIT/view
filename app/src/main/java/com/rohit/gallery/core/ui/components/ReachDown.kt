package com.rohit.gallery.core.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.ui.theme.GalleryMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How far the grid comes down when pulled: this share of the area it fills. */
private const val REACH_FRACTION = 0.3f

/**
 * Pull down at the top of a grid and the whole grid comes down by [REACH_FRACTION] of the screen, so its top rows
 * are in thumb reach; a large title fills the space above. It stays down until the grid is pushed back up.
 *
 * The grid follows the finger 1:1 (rubber-banded past the open position), and on release it goes to whichever
 * position the gesture's momentum points at (see [GalleryMotion.project]).
 */
@Stable
class ReachState internal constructor(expanded: Boolean) {
    /** Where the grid rests: down (true) or in its normal place. Saved, so it survives leaving the screen. */
    var expanded by mutableStateOf(expanded)
        private set

    /** How far down the grid is drawn now, in px. */
    var offset by mutableFloatStateOf(0f)
        private set

    internal var maxPx by mutableFloatStateOf(0f)
        private set

    internal var dragging = false
        private set

    /** Finger travel before rubber-banding, so a drag past the open position keeps tracking smoothly. */
    private var raw = 0f
    private var settleJob: Job? = null

    /** 0 with the grid in place, 1 when it is fully down. */
    val progress: Float get() = if (maxPx > 0f) (offset / maxPx).coerceIn(0f, 1f) else 0f

    internal fun resize(max: Float) {
        maxPx = max
        if (!dragging && settleJob?.isActive != true) {
            offset = if (expanded) max else 0f
            raw = offset
        }
    }

    /** A drag takes over from a settle in flight, starting where the grid is now (never a jump). */
    internal fun beginDrag() {
        if (dragging) return
        settleJob?.cancel()
        settleJob = null
        raw = rawFor(offset)
        dragging = true
    }

    /** Moves by [dy] px (positive is down); returns how much of it was used. */
    internal fun dragBy(dy: Float): Float {
        val before = raw
        raw = (raw + dy).coerceAtLeast(0f)
        offset = shownFor(raw)
        return raw - before
    }

    internal val pastHalfway: Boolean get() = raw > maxPx / 2

    /** Ends a drag with [velocity] px/s: goes to the rest position the momentum points at. */
    internal fun release(velocity: Float, scope: CoroutineScope) {
        dragging = false
        val target = if (offset + GalleryMotion.project(velocity) > maxPx / 2) maxPx else 0f
        expanded = target > 0f
        if (offset == target) return
        settleJob = scope.launch {
            animate(offset, target, velocity, GalleryMotion.dragReturn()) { value, _ -> offset = value }
            raw = target
        }
    }

    private fun shownFor(r: Float) = if (r <= maxPx) r else maxPx + GalleryMotion.rubberBand(r - maxPx, maxPx)

    private fun rawFor(shown: Float): Float {
        if (shown <= maxPx) return shown.coerceAtLeast(0f)
        // The rubber band never quite reaches maxPx of overshoot; stay clear of its asymptote.
        val over = (shown - maxPx).coerceAtMost(maxPx * 0.9f)
        return maxPx + GalleryMotion.rubberBandInverse(over, maxPx)
    }

    companion object {
        val Saver = Saver<ReachState, Boolean>(save = { it.expanded }, restore = { ReachState(it) })
    }
}

/** A [ReachDown] state the screen holds itself, e.g. to fade out its own small title while the big one shows. */
@Composable
fun rememberReachState(): ReachState = rememberSaveable(saver = ReachState.Saver) { ReachState(false) }

/**
 * Wraps a scrolling grid ([content]) with pull-down-to-reach. The grid's own scrolling is untouched: only a drag
 * that the grid can't use (pulling down at its top) moves it down, and a push up first brings it back up.
 * [title] and [subtitle] show in the space above it; [topInset] keeps them clear of the status bar.
 */
@Composable
fun ReachDown(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    state: ReachState = rememberReachState(),
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val connection = remember(state) {
        object : NestedScrollConnection {
            fun track(dy: Float): Float {
                state.beginDrag()
                val wasPast = state.pastHalfway
                val used = state.dragBy(dy)
                // A tick when letting go would now change where the grid rests.
                if (state.pastHalfway != wasPast) {
                    haptic.performHapticFeedback(
                        if (state.pastHalfway) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick,
                    )
                }
                return used
            }

            // Pushing up while the grid is down: bring it up first, then let the grid scroll.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                if (state.offset <= 0f && !state.dragging) return Offset.Zero
                return Offset(0f, track(available.y))
            }

            // Pulling down past the grid's top: what the grid couldn't scroll moves it down.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f || state.maxPx <= 0f) return Offset.Zero
                return Offset(0f, track(available.y))
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!state.dragging) return Velocity.Zero
                // Pushed all the way back up and still going: the fling belongs to the grid.
                if (state.offset <= 0f && available.y <= 0f) {
                    state.release(0f, scope)
                    return Velocity.Zero
                }
                state.release(available.y, scope)
                return Velocity(0f, available.y)
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { state.resize(it.height * REACH_FRACTION) }
            .nestedScroll(connection),
    ) {
        ReachTitle(title, subtitle, state, topInset)
        Box(Modifier.fillMaxSize().offset { IntOffset(0, state.offset.roundToInt()) }) { content() }
    }
}

/** The big title in the space the grid leaves, centred in it, fading and growing in as the grid comes down. */
@Composable
private fun ReachTitle(title: String, subtitle: String?, state: ReachState, topInset: Dp) {
    val density = LocalDensity.current
    val heightDp = with(density) { state.maxPx.toDp() }
    val insetPx = with(density) { topInset.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(heightDp)
            .graphicsLayer {
                val p = state.progress
                alpha = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
                val scale = 0.92f + 0.08f * p
                scaleX = scale
                scaleY = scale
                // Box spans [0, maxPx]; keep its content centred in the revealed band [topInset, offset].
                translationY = (state.offset + insetPx - state.maxPx) / 2
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
