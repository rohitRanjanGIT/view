package com.rohit.gallery.feature.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.GridGeometry
import com.rohit.gallery.core.ui.components.GridZoomState
import com.rohit.gallery.core.ui.components.KeepGridZoomAnchor
import com.rohit.gallery.core.ui.components.FollowViewerPhoto
import com.rohit.gallery.core.ui.components.MediaThumbnail
import com.rohit.gallery.core.ui.components.ZoomAnchorCell
import com.rohit.gallery.core.ui.components.ZoomOverlay
import com.rohit.gallery.core.ui.components.mediaSharedElement
import com.rohit.gallery.core.ui.components.pinchToZoom
import com.rohit.gallery.core.ui.components.zoomLayer

/** Column counts a pinch steps through on flat grids (no month view: these grids have no date sections). */
val FLAT_GRID_COLUMNS = listOf(2, 4, 6)
const val GRID_COLUMNS = 4

private val CellGap = 2.dp

/** Rough height of a secondary screen's title bar, for deciding whether a returned item is on screen. */
private val ReturnChromeHeight = 64.dp
private const val HEADER_KEY = "grid-header"

private class GridHit(val item: MediaItem, val index: Int)

/**
 * A flat grid of [list]: tap, long-press and drag to select, with auto-scroll at the edges, and pinch to
 * change [columns] (one of [FLAT_GRID_COLUMNS]) through [onColumnsChange].
 */
@Composable
fun SelectableMediaGrid(
    list: List<MediaItem>,
    selection: SelectionState,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    header: String? = null,
) {
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    val press = rememberGridPressState()
    val scope = rememberCoroutineScope()
    val currentList by rememberUpdatedState(list)
    val indexById = remember(list) { HashMap<Long, Int>(list.size * 2).also { m -> list.forEachIndexed { i, it -> m[it.id] = i } } }
    val currentIndex by rememberUpdatedState(indexById)
    val currentColumns by rememberUpdatedState(columns)

    val density = LocalDensity.current
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val gapPx = with(density) { CellGap.toPx() }
    val cellWidth = (windowWidthPx - gapPx * (columns - 1)) / columns
    val cellPx = cellWidth.toInt()

    // While the viewer is open over this grid, keep its photo in view underneath (see FollowViewerPhoto).
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    FollowViewerPhoto { id ->
        val gridIndex = (indexById[id] ?: return@FollowViewerPhoto) + if (header != null) 1 else 0
        // Rows that fit fully between the title bar and the bottom padding (an estimate is enough here).
        val chromePx = with(density) { (ReturnChromeHeight + 96.dp).roundToPx() } + statusBarPx
        val fullRows = ((windowHeightPx - chromePx) / cellPx.coerceAtLeast(1) - 1).coerceAtLeast(1)
        val first = gridState.firstVisibleItemIndex
        if (gridIndex < first || gridIndex >= first + fullRows * columns) {
            gridState.scrollToItem((gridIndex - columns * (fullRows / 2)).coerceAtLeast(0))
        }
    }

    fun itemAt(position: Offset): GridHit? {
        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { cell ->
            position.x >= cell.offset.x && position.x < cell.offset.x + cell.size.width &&
                position.y >= cell.offset.y && position.y < cell.offset.y + cell.size.height
        } ?: return null
        val id = info.key as? Long ?: return null
        val index = currentIndex[id] ?: return null
        return GridHit(currentList[index], index)
    }

    /** The cell nearest to [position], for keeping it in place when a pinch changes the column count. */
    fun anchorAt(position: Offset): ZoomAnchorCell? {
        val cell = gridState.layoutInfo.visibleItemsInfo
            .filter { it.key is Long }
            .minByOrNull {
                val centre = Offset(it.offset.x + it.size.width / 2f, it.offset.y + it.size.height / 2f)
                (centre - position).getDistanceSquared()
            } ?: return null
        val centre = Offset(cell.offset.x + cell.size.width / 2f, cell.offset.y + cell.size.height / 2f)
        return ZoomAnchorCell(cell.key as Long, centre)
    }

    val zoom = remember { GridZoomState() }
    val gridLayer = rememberGraphicsLayer()
    val headerHeight = rememberHeaderHeight(gridState, header != null, gapPx)
    KeepGridZoomAnchor(
        zoom = zoom,
        gridState = gridState,
        geometry = GridGeometry(
            columns = columns,
            cellWidth = cellWidth,
            columnPitch = cellWidth + gapPx,
            rowPitch = cellWidth + gapPx,
            anchorY = cellWidth / 2,
            leadingItems = if (header != null) 1 else 0,
            leadingHeight = headerHeight(),
        ),
        indexOf = { indexById[it] },
    )

    val pointer = remember { object { var position: Offset? = null } }
    var dragActive by remember { mutableStateOf(false) }
    val edgePx = with(density) { 96.dp.toPx() }
    LaunchedEffect(dragActive) {
        while (dragActive) {
            val p = pointer.position ?: break
            val height = gridState.layoutInfo.viewportSize.height
            val step = when {
                p.y < edgePx -> -(edgePx - p.y) / edgePx * 36f
                p.y > height - edgePx -> (p.y - (height - edgePx)) / edgePx * 36f
                else -> 0f
            }
            if (step != 0f) {
                gridState.scrollBy(step)
                itemAt(p)?.let { selection.dragTo(it.index, currentList) }
            }
            withFrameNanos { }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Same colour as the rest of the screen, so the empty end of the last row matches.
            .background(MaterialTheme.colorScheme.background)
            .clipToBounds()
            .pinchToZoom(
                onPinch = { scale, centroid ->
                    val position = FLAT_GRID_COLUMNS.indexOf(currentColumns)
                    zoom.onPinch(
                        scale = scale,
                        centroid = centroid,
                        columns = currentColumns,
                        canZoomIn = position > 0,
                        canZoomOut = position in 0 until FLAT_GRID_COLUMNS.lastIndex,
                        scope = scope,
                        snapshot = { gridLayer.toImageBitmap() },
                        anchorAt = ::anchorAt,
                        onSwitch = { zoomIn ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onColumnsChange(FLAT_GRID_COLUMNS[if (zoomIn) position - 1 else position + 1])
                        },
                    )
                },
                onPinchEnd = { zoom.onPinchEnd(scope) },
            ),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(CellGap),
            verticalArrangement = Arrangement.spacedBy(CellGap),
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier
                .fillMaxSize()
                .zoomLayer(zoom)
                // Records the grid as it draws, so a column switch can snapshot the old grid.
                .drawWithContent {
                    gridLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(gridLayer)
                }
                .gridGestures(
                    onTap = { pos ->
                        itemAt(pos)?.let { hit ->
                            if (selection.isSelecting) selection.toggle(hit.item.id) else onOpenMedia(hit.item)
                        }
                    },
                    onLongPress = { pos ->
                        itemAt(pos)?.let { hit ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pointer.position = pos
                            dragActive = true
                            selection.startDrag(hit.index, hit.item.id)
                        }
                    },
                    onDrag = { pos ->
                        pointer.position = pos
                        itemAt(pos)?.let { selection.dragTo(it.index, currentList) }
                    },
                    onDragEnd = {
                        dragActive = false
                        pointer.position = null
                        selection.endDrag()
                    },
                    onPress = { pos, untilLongPressMs -> itemAt(pos)?.let { press.press(it.item.id, untilLongPressMs) } },
                    onPressEnd = { press.release() },
                ),
        ) {
            if (header != null) item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    header,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(list, key = { it.id }) { item ->
                Box(Modifier.aspectRatio(1f).pressScale(press, item.id).mediaSharedElement(item.id)) {
                    MediaThumbnail(item, cellPx, Modifier.fillMaxSize())
                    if (selection.isSelecting) {
                        SelectionCheck(item.id in selection.ids, Modifier.align(Alignment.TopStart).padding(6.dp))
                    }
                }
            }
        }
        ZoomOverlay(zoom)
    }
}

/** Height of the grid's header item (gap included) while it has been seen; 0 when there is none. */
@Composable
private fun rememberHeaderHeight(gridState: LazyGridState, hasHeader: Boolean, gapPx: Float): () -> Float {
    val headerPx = remember { object { var value = 0f } }
    if (hasHeader) {
        // Collected outside composition, so scrolling never recomposes this screen.
        LaunchedEffect(gridState) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == HEADER_KEY }?.size?.height }
                .collect { height -> if (height != null) headerPx.value = height + gapPx }
        }
    }
    return { if (hasHeader) headerPx.value else 0f }
}
