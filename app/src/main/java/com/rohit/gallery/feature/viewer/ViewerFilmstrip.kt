package com.rohit.gallery.feature.viewer

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.MediaThumbnail
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

private val ItemWidth = 40.dp
private val ItemHeight = 56.dp

/**
 * Strip of small thumbnails under the viewer. The current item stays centered; swiping the strip makes whichever
 * thumbnail passes the middle the current item, live, and the strip snaps so one thumbnail ends exactly centered.
 */
@Composable
fun ViewerFilmstrip(
    items: List<MediaItem>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val sidePadding = (screenWidth - ItemWidth) / 2
    val itemPx = with(density) { ItemHeight.roundToPx() }

    // "Scrubbing" means the user moves the strip: a finger drag or the fling + snap after it. Only then does the
    // centered thumbnail drive the photo; our own scroll to follow the pager passes over other items and must not.
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var flinging by remember { mutableStateOf(false) }
    val snap = rememberSnapFlingBehavior(listState, SnapPosition.Center)
    val fling = remember(snap) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                flinging = true
                try {
                    return with(snap) { performFling(initialVelocity) }
                } finally {
                    flinging = false
                }
            }
        }
    }
    val scrubbing = dragged || flinging
    val latestScrubbing by rememberUpdatedState(scrubbing)
    val latestIndex by rememberUpdatedState(currentIndex)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(listState) {
        snapshotFlow { if (latestScrubbing) listState.centeredIndex() else null }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { index ->
                if (index != latestIndex) {
                    // A light tick for each photo the strip passes, like the detents of a dial.
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    latestOnSelect(index)
                }
            }
    }

    // With half a screen of content padding, scrolling an item to the start centers it.
    LaunchedEffect(currentIndex) {
        if (!latestScrubbing && listState.centeredIndex() != currentIndex) listState.animateScrollToItem(currentIndex)
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(ItemHeight),
        contentPadding = PaddingValues(horizontal = sidePadding),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        flingBehavior = fling,
    ) {
        itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> "thumb" }) { index, item ->
            MediaThumbnail(
                item = item,
                cellSizePx = itemPx,
                showBadge = false,
                modifier = Modifier
                    .width(ItemWidth)
                    .height(ItemHeight)
                    .clipToBounds()
                    .then(if (index == currentIndex) Modifier.border(2.dp, Color.White) else Modifier)
                    .clickable { onSelect(index) },
            )
        }
    }
}

/** The item whose middle is closest to the middle of the strip (item offsets start after the content padding). */
private fun LazyListState.centeredIndex(): Int? {
    val info = layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
}
