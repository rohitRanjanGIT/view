package com.rohit.gallery.navigation

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
 * Pictures, Albums and Settings side by side: the page follows the finger and snaps to the nearest one. Neighbours of
 * the page showing stay composed, so Pictures and Albums keep their scroll positions. A second finger (a pinch to resize the grid) or [userScrollEnabled]
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
fun Modifier.dragsPager(state: PagerState, scope: CoroutineScope, lastPage: Int = state.pageCount - 1): Modifier = pointerInput(state, lastPage) {
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
            }.coerceIn(0, lastPage)
            scope.launch { state.animateScrollToPage(target, animationSpec = TabTapSpec) }
        },
        onDragCancel = { scope.launch { state.animateScrollToPage(state.currentPage, animationSpec = TabTapSpec) } },
        onHorizontalDrag = { change, amount ->
            change.consume()
            // Never past [lastPage] (Settings has no tab): a drag further left stops there.
            val atLast = state.currentPage + state.currentPageOffsetFraction >= lastPage
            if (amount < 0 && atLast) return@detectHorizontalDragGestures
            total += amount
            state.dispatchRawDelta(-amount)
        },
    )
}
