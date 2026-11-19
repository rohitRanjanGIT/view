package com.rohit.gallery.core.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
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

/** Top of the band the big title is centred in: just under where screens keep their top bar. */
private val TitleBandTop = 72.dp

/** The row of icons (search, ⋮) that rides just above the grid while it is down. */
private val ActionsRowHeight = 48.dp

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

    /** Where the wrapped area starts on screen, px: the title and icons are placed in screen terms. */
    internal var windowTop by mutableFloatStateOf(0f)

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
 * [title] and [subtitle] show in the space above it, and [actions] (the screen's icons) in a row just above the grid.
 * [topInset] is how far below this area's top the grid's own content starts (the status bar, for Pictures).
 */
@Composable
fun ReachDown(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    state: ReachState = rememberReachState(),
    actions: (@Composable RowScope.() -> Unit)? = null,
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


    val density = LocalDensity.current
    // Where the grid's top lands when it is down, in screen terms: the same on every screen, whatever sits above.
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val targetY = statusBarPx + LocalWindowInfo.current.containerSize.height * REACH_FRACTION
    val insetPx = with(density) { topInset.toPx() }
    val minPx = with(density) { (ActionsRowHeight * 3).toPx() }

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onGloballyPositioned { coords ->
                val top = coords.positionInWindow().y
                state.windowTop = top
                state.resize((targetY - top - insetPx).coerceAtLeast(minPx))
            }
            .nestedScroll(connection),
    ) {
        Box(Modifier.fillMaxSize().offset { IntOffset(0, state.offset.roundToInt()) }) { content() }
        // Drawn over the grid: on Pictures the grid's own background reaches up under the icon row.
        ReachTitle(title, subtitle, state, topInset)
        if (actions != null) ReachActions(state, topInset, actions)
    }
}

/**
 * The big title in the space the grid leaves, fading and growing in as the grid comes down. It is centred between
 * the usual top bar and the icon row above the grid, measured on screen, so it sits at the same height everywhere.
 */
@Composable
private fun ReachTitle(title: String, subtitle: String?, state: ReachState, topInset: Dp) {
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val bandTopPx = statusBarPx + with(density) { TitleBandTop.toPx() }
    val insetPx = with(density) { topInset.toPx() }
    val rowPx = with(density) { ActionsRowHeight.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = state.progress
                alpha = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
                val scale = 0.92f + 0.08f * p
                scaleX = scale
                scaleY = scale
                val top = (bandTopPx - state.windowTop).coerceAtLeast(0f)
                val bottom = state.offset + insetPx - rowPx
                translationY = (top + bottom) / 2 - size.height / 2
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

/**
 * The screen's icons (search, ⋮) in a row at the right, riding just above the grid while it is down. They fade in
 * as the screen's own copies at the top fade out (see [ReachState.progress]); only composed while the grid is down.
 */
@Composable
private fun ReachActions(state: ReachState, topInset: Dp, actions: @Composable RowScope.() -> Unit) {
    val shown by remember(state) { derivedStateOf { state.progress > 0f } }
    if (!shown) return
    val density = LocalDensity.current
    val insetPx = with(density) { topInset.toPx() }
    val rowPx = with(density) { ActionsRowHeight.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(ActionsRowHeight)
            .padding(end = 8.dp)
            .graphicsLayer {
                alpha = ((state.progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
                translationY = state.offset + insetPx - rowPx
            },
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        content = actions,
    )
}

/** Swallows touches (and hides from accessibility) so faded-out icons can't be pressed. */
private val BlockTouches = Modifier
    .clearAndSetSemantics { }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

/**
 * The screen's own top icons: they fade out as the grid comes down and their copies in the row above the grid fade
 * in. Once the grid is mostly down they still take their space (so the bar keeps its height) but can't be tapped.
 */
@Composable
fun ReachTopIcons(state: ReachState, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val hidden by remember(state) { derivedStateOf { state.progress >= 0.5f } }
    Row(
        modifier
            .graphicsLayer { alpha = (1f - state.progress * 2f).coerceIn(0f, 1f) }
            .then(if (hidden) BlockTouches else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
