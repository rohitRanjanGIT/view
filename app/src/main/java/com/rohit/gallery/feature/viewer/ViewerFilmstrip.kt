package com.rohit.gallery.feature.viewer

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.MediaThumbnail

private val ItemWidth = 40.dp
private val ItemHeight = 56.dp

/** Strip of small thumbnails under the viewer; the current item stays centered. */
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

    // With half a screen of content padding, scrolling an item to the start centers it.
    LaunchedEffect(currentIndex) { listState.animateScrollToItem(currentIndex) }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(ItemHeight),
        contentPadding = PaddingValues(horizontal = sidePadding),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> "thumb" }) { index, item ->
            val shape = RoundedCornerShape(6.dp)
            MediaThumbnail(
                item = item,
                cellSizePx = itemPx,
                showBadge = false,
                modifier = Modifier
                    .width(ItemWidth)
                    .height(ItemHeight)
                    .clip(shape)
                    .then(if (index == currentIndex) Modifier.border(2.dp, Color.White, shape) else Modifier)
                    .clickable { onSelect(index) },
            )
        }
    }
}
