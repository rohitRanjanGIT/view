package com.rohit.gallery.core.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset

/**
 * Fixed geometry of a lazy grid of equal cells, enough to place any cell without measuring it.
 *
 * Content (from the top, before scrolling): [topPadding], then [leadingItems] full-width items that are
 * [leadingHeight] tall in total (gap included), then rows every [rowPitch] px. In a row, cell `c` starts at
 * `startPadding + c * columnPitch`; [anchorY] is the point in a cell (from its top) that is kept under the
 * fingers, usually the middle of its picture.
 */
class GridGeometry(
    val columns: Int,
    val cellWidth: Float,
    val columnPitch: Float,
    val rowPitch: Float,
    val anchorY: Float,
    val startPadding: Float = 0f,
    val topPadding: Float = 0f,
    val leadingItems: Int = 0,
    val leadingHeight: Float = 0f,
)

/**
 * After a pinch changed the column count of a lazy grid: during the first composition of the new layout,
 * scrolls so the anchor item (found with [indexOf], counted after the leading items) sits where the fingers
 * were, and starts the switch animation. Exact apart from the very end of the list, which cannot scroll further.
 */
@Composable
fun KeepGridZoomAnchor(
    zoom: GridZoomState,
    gridState: LazyGridState,
    geometry: GridGeometry,
    indexOf: (itemId: Long) -> Int?,
) {
    val columns = geometry.columns
    val shown = remember { object { var columns = columns } }
    if (shown.columns != columns) {
        shown.columns = columns
        zoom.startSwitch(columns) { itemId, preferredY ->
            val index = indexOf(itemId) ?: return@startSwitch null
            val g = geometry
            val row = index / columns
            // Where the row starts in the content, and how far to scroll so it lands at the fingers.
            val rowTop = g.topPadding + g.leadingHeight + row * g.rowPitch
            // A lazy grid's scroll offset is measured from the end of the top padding: at scroll s the first
            // item's top is at topPadding - s on screen.
            val scroll = (rowTop - (preferredY - g.anchorY)).coerceAtLeast(0f)
            when {
                // Still within the leading items: scroll by pixels from the first item.
                scroll < g.leadingHeight -> gridState.requestScrollToItem(0, scroll.toInt())
                else -> {
                    val firstRow = ((scroll - g.leadingHeight) / g.rowPitch).toInt()
                    val offset = scroll - g.leadingHeight - firstRow * g.rowPitch
                    gridState.requestScrollToItem(g.leadingItems + firstRow * columns, offset.toInt())
                }
            }
            Offset(
                x = g.startPadding + (index % columns) * g.columnPitch + g.cellWidth / 2,
                y = rowTop - scroll + g.anchorY,
            )
        }
    }
    // Runs after the first frame of the new layout, which is drawn at the switch's starting transform.
    LaunchedEffect(columns) { zoom.finishSwitch(this) }
}
