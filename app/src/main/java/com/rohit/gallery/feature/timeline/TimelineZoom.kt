package com.rohit.gallery.feature.timeline

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import com.rohit.gallery.core.ui.components.GridZoomState

/**
 * After a pinch switched density: during the first composition of the new layout, scrolls so the anchor
 * item's row sits where the fingers were and starts the switch animation from there.
 */
@Composable
fun KeepZoomAnchor(
    zoom: GridZoomState,
    content: TimelineUiState.Content,
    listState: LazyListState,
    map: TimelineScrollMap,
    rowPx: Int,
    cellSizePx: Int,
    gapPx: Int,
) {
    val shownLevel = remember { object { var level = content.level } }
    if (shownLevel.level != content.level) {
        shownLevel.level = content.level
        zoom.startSwitch(content.columns) { itemId, preferredY ->
            val position = content.indexById[itemId]?.let { gridPositionOf(content.days, it) }
                ?: return@startSwitch null
            val rowTop = map.offsetOf(position.lazyIndex, 0)
            val top = (rowTop + rowPx / 2 - preferredY.toLong()).coerceAtLeast(0L)
            val (index, offset) = map.positionAt(top)
            listState.requestScrollToItem(index, offset)
            Offset(
                x = position.column * (cellSizePx + gapPx) + cellSizePx / 2f,
                y = (rowTop - top + rowPx / 2).toFloat(),
            )
        }
    }
    // Runs after the first frame of the new layout, which is drawn at the switch's starting transform.
    LaunchedEffect(content.level) { zoom.finishSwitch(this) }
}
