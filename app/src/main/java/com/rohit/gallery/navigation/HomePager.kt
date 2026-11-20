package com.rohit.gallery.navigation

import android.os.SystemClock
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Snap of the tab pages: a quick spring with no bounce (stiffer than the pager's default), used for flings, bar taps
 * and back alike so every tab change feels the same.
 */
val TabTapSpec: AnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f)

/** Share of a page the finger must drag (without a fling) before the next tab wins. */
private const val SNAP_THRESHOLD = 0.3f

/**
 * Pictures and Albums side by side: the page follows the finger and snaps to the nearest tab. Both stay composed, so
 * each keeps its scroll position. A second finger (a pinch to resize the grid) or [userScrollEnabled]
 * false (selecting) keeps the pages still; the grids then get the gesture.
 */
@Composable
fun HomePager(
    state: PagerState,
    userScrollEnabled: Boolean,
    modifier: Modifier = Modifier,
    page: @Composable (Int) -> Unit,
) {
    var multiTouch by remember { mutableStateOf(false) }
    // If a second finger lands after the page already moved a little, put it back on its tab.
    LaunchedEffect(multiTouch) {
        if (multiTouch && state.currentPageOffsetFraction != 0f) state.animateScrollToPage(state.currentPage, animationSpec = TabTapSpec)
    }
    HorizontalPager(
        state = state,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } > 1) multiTouch = true
                    } while (event.changes.any { it.pressed })
                    multiTouch = false
                }
            },
        beyondViewportPageCount = homeTabs.size - 1,
        userScrollEnabled = userScrollEnabled && !multiTouch,
        flingBehavior = PagerDefaults.flingBehavior(
            state = state,
            snapAnimationSpec = TabTapSpec,
            snapPositionalThreshold = SNAP_THRESHOLD,
        ),
        key = { it },
    ) { index ->
        Box(Modifier.fillMaxSize()) { page(index) }
    }
}

/**
 * Dragging sideways on the bottom bar moves the pages too (they follow the finger), then settles on the next or
 * previous tab once the drag went far enough.
 */
fun Modifier.dragsPager(state: PagerState, scope: CoroutineScope): Modifier = pointerInput(state) {
    var startPage = 0
    var total = 0f
    val threshold = 48.dp.toPx()
    detectHorizontalDragGestures(
        onDragStart = {
            startPage = state.currentPage
            total = 0f
        },
        onDragEnd = {
            val target = when {
                abs(total) < threshold -> startPage
                total < 0 -> startPage + 1
                else -> startPage - 1
            }.coerceIn(0, state.pageCount - 1)
            scope.launch { state.animateScrollToPage(target, animationSpec = TabTapSpec) }
        },
        onDragCancel = { scope.launch { state.animateScrollToPage(state.currentPage, animationSpec = TabTapSpec) } },
        onHorizontalDrag = { change, amount ->
            change.consume()
            total += amount
            state.dispatchRawDelta(-amount)
        },
    )
}

/** How long a swipe past the last page has to be held. */
private const val PAST_END_HOLD_MS = 600L

/**
 * Swipe left past the last page (Albums) and hold for [PAST_END_HOLD_MS]: a buzz, then [onHold] (Settings). A quick
 * swipe does nothing, so it never opens by accident. Only watches (consumes nothing), so the pager's stretch at its
 * edge still shows under the finger. Moving back, lifting, a second finger or a vertical drag cancels. Checked at
 * touch-down with [enabled] (Albums settled, nothing selected).
 */
@Composable
fun Modifier.holdPastLastPage(enabled: () -> Boolean, onHold: () -> Unit): Modifier {
    val haptic = LocalHapticFeedback.current
    val latestEnabled by rememberUpdatedState(enabled)
    val latestHold by rememberUpdatedState(onHold)
    return pointerInput(Unit) {
        val distance = 48.dp.toPx()
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!latestEnabled()) return@awaitEachGesture
            var armedAt = -1L
            while (true) {
                val event = if (armedAt < 0) {
                    awaitPointerEvent(PointerEventPass.Initial)
                } else {
                    val left = PAST_END_HOLD_MS - (SystemClock.uptimeMillis() - armedAt)
                    withTimeoutOrNull(left.coerceAtLeast(1L)) { awaitPointerEvent(PointerEventPass.Initial) }
                } ?: run {
                    // Held long enough without letting go.
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    latestHold()
                    return@awaitEachGesture
                }
                if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed) return@awaitEachGesture
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (abs(dy) > slop && abs(dy) > abs(dx)) return@awaitEachGesture
                armedAt = when {
                    dx > -distance -> -1L
                    armedAt < 0 -> SystemClock.uptimeMillis()
                    else -> armedAt
                }
            }
        }
    }
}
