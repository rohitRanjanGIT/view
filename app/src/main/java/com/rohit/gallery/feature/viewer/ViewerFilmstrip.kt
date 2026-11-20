package com.rohit.gallery.feature.viewer

import androidx.compose.animation.core.animate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.MediaThumbnail
import com.rohit.gallery.core.ui.theme.GalleryMotion
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.flow.distinctUntilChanged

private val ItemWidth = 40.dp
private val ItemHeight = 56.dp
private val ItemGap = 2.dp
private val ItemShape = RoundedCornerShape(6.dp)

/**
 * How much bigger a thumbnail is drawn, by its distance from the centre (in items): the centred one most, the two on
 * each side a little less, the rest flat. Between whole steps it blends, so the step slides smoothly with the strip.
 */
private val PopSteps = floatArrayOf(0.22f, 0.12f, 0.05f, 0f)

/**
 * Strip of small thumbnails under the viewer. It follows the big photo continuously (also mid-swipe of the photo).
 * Dragging it moves only the strip, 1:1: the big photo shows the centred item's cached thumbnail ([onScrub]) and the
 * full photo opens once the strip comes to rest ([onSelect]). A flick glides on at the finger's speed and a spring
 * lands it on the nearest thumbnail. The centred thumbnail and its neighbours are drawn bigger, in the graphics layer
 * only, so scrolling never recomposes a thumbnail.
 */
@Composable
fun ViewerFilmstrip(
    items: List<MediaItem>,
    pagerState: PagerState,
    onScrub: (Int?) -> Unit,
    onSelect: (index: Int, animate: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = pagerState.currentPage)
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val sidePadding = (screenWidth - ItemWidth) / 2
    val itemWidthPx = with(density) { ItemWidth.toPx() }
    val stridePx = with(density) { (ItemWidth + ItemGap).toPx() }
    // Sharp enough for the popped-up centre thumbnail too.
    val cellPx = with(density) { (ItemHeight * (1f + PopSteps[0])).roundToPx() }
    val latestCount by rememberUpdatedState(items.size)

    // "Scrubbing": the user moves the strip (finger drag, or the glide after it). Only then does the strip lead.
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var flinging by remember { mutableStateOf(false) }
    val fling = remember(listState, stridePx) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                flinging = true
                try {
                    // Glide where the flick points, then a spring that starts at the finger's speed lands on a thumbnail.
                    val from = listState.centrePosition(stridePx) * stridePx
                    val projected = from + GalleryMotion.project(initialVelocity)
                    val target = (projected / stridePx).roundToInt().coerceIn(0, (latestCount - 1).coerceAtLeast(0)) * stridePx
                    var last = from
                    animate(from, target, initialVelocity, GalleryMotion.fluid(response = 0.25f)) { value, _ ->
                        last += scrollBy(value - last)
                    }
                    return 0f
                } finally {
                    flinging = false
                }
            }
        }
    }
    val scrubbing = dragged || flinging
    val latestScrubbing by rememberUpdatedState(scrubbing)
    val latestOnScrub by rememberUpdatedState(onScrub)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val haptic = LocalHapticFeedback.current

    // While scrubbing: the centred item, live (a light tick for each one passed, like the detents of a dial).
    // When it ends: open that item.
    LaunchedEffect(listState) {
        var scrubbed: Int? = null
        snapshotFlow { if (latestScrubbing) listState.centrePosition(stridePx).roundToInt() else null }
            .distinctUntilChanged()
            .collect { index ->
                if (index != null) {
                    if (scrubbed != null) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    scrubbed = index
                    latestOnScrub(index)
                } else {
                    scrubbed?.let { latestOnSelect(it, false) }
                    scrubbed = null
                }
            }
    }

    // Otherwise the strip follows the big photo, fraction by fraction (so it moves with a swipe of the photo).
    LaunchedEffect(listState, pagerState) {
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }
            .collect { position ->
                if (latestScrubbing) return@collect
                val index = floor(position).toInt().coerceAtLeast(0)
                val offset = ((position - index) * stridePx).roundToInt()
                if (listState.firstVisibleItemIndex != index || listState.firstVisibleItemScrollOffset != offset) {
                    listState.scrollToItem(index, offset)
                }
            }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(ItemHeight * (1f + PopSteps[0])),
        contentPadding = PaddingValues(horizontal = sidePadding),
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
        verticalAlignment = Alignment.Bottom,
        flingBehavior = fling,
    ) {
        itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> "thumb" }) { index, item ->
            MediaThumbnail(
                item = item,
                cellSizePx = cellPx,
                showBadge = false,
                modifier = Modifier
                    .graphicsLayer {
                        val distance = index - listState.centrePosition(stridePx)
                        val scale = 1f + popExtra(abs(distance))
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        // Pushed aside by the bigger ones nearer the centre, so they never overlap.
                        translationX = sign(distance) * itemWidthPx * popShift(abs(distance))
                    }
                    .width(ItemWidth)
                    .height(ItemHeight)
                    .clip(ItemShape)
                    .clickable { onSelect(index, true) },
            )
        }
    }
}

/**
 * Where the strip's centre is, in items (2.5 = halfway between items 2 and 3). With half a screen of content padding,
 * an item scrolled to the start is centred, and each item with its gap scrolls [stridePx].
 */
private fun LazyListState.centrePosition(stridePx: Float): Float =
    firstVisibleItemIndex + firstVisibleItemScrollOffset / stridePx

/** Extra size (share of the item) at [distance] items from the centre, blended between [PopSteps]. */
private fun popExtra(distance: Float): Float {
    val step = floor(distance).toInt()
    if (step >= PopSteps.lastIndex) return 0f
    val f = distance - step
    return PopSteps[step] + (PopSteps[step + 1] - PopSteps[step]) * f
}

/**
 * How far (share of the item width) a thumbnail at [distance] moves outward: half the extra width of every thumbnail
 * between it and the centre, both sides. The extra size is linear between steps, so this is its exact integral.
 */
private fun popShift(distance: Float): Float {
    var shift = 0f
    var step = 0
    while (step < PopSteps.lastIndex && step + 1 <= distance) {
        shift += (PopSteps[step] + PopSteps[step + 1]) / 2
        step++
    }
    if (step < PopSteps.lastIndex) {
        val f = distance - step
        shift += PopSteps[step] * f + (PopSteps[step + 1] - PopSteps[step]) * f * f / 2
    }
    return shift
}
