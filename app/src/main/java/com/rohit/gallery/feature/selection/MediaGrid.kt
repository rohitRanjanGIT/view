package com.rohit.gallery.feature.selection

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.MediaThumbnail
import com.rohit.gallery.core.ui.components.LocalViewerReturnTarget
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalWindowInfo
import com.rohit.gallery.core.ui.components.mediaSharedElement

const val GRID_COLUMNS = 4

/** Rough height of a secondary screen's title bar, for deciding whether a returned item is on screen. */
private val ReturnChromeHeight = 64.dp

private class GridHit(val item: MediaItem, val index: Int)

/** A flat grid of [list]: tap, long-press and drag to select, with auto-scroll at the edges. */
@Composable
fun SelectableMediaGrid(
    list: List<MediaItem>,
    selection: SelectionState,
    cellPx: Int,
    onOpenMedia: (MediaItem) -> Unit,
    header: String? = null,
) {
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    val currentList by rememberUpdatedState(list)
    val indexById = remember(list) { HashMap<Long, Int>(list.size * 2).also { m -> list.forEachIndexed { i, it -> m[it.id] = i } } }
    val currentIndex by rememberUpdatedState(indexById)

    // Back from the viewer: bring the item it ended on into view before the first frame (see ViewerReturnTarget).
    val returnTarget = LocalViewerReturnTarget.current
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    remember {
        val id = returnTarget.take() ?: return@remember
        val gridIndex = (indexById[id] ?: return@remember) + if (header != null) 1 else 0
        // Rows that fit fully between the title bar and the bottom padding (an estimate is enough here).
        val chromePx = with(density) { (ReturnChromeHeight + 96.dp).roundToPx() } + statusBarPx
        val fullRows = ((windowHeightPx - chromePx) / cellPx.coerceAtLeast(1) - 1).coerceAtLeast(1)
        val first = gridState.firstVisibleItemIndex
        if (gridIndex < first || gridIndex >= first + fullRows * GRID_COLUMNS) {
            gridState.requestScrollToItem((gridIndex - GRID_COLUMNS * (fullRows / 2)).coerceAtLeast(0))
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

    val pointer = remember { object { var position: Offset? = null } }
    var dragActive by remember { mutableStateOf(false) }
    val edgePx = with(LocalDensity.current) { 96.dp.toPx() }
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

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(GRID_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        modifier = Modifier
            .fillMaxSize()
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
            ),
    ) {
        if (header != null) item(span = { GridItemSpan(GRID_COLUMNS) }) {
            Text(
                header,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(list, key = { it.id }) { item ->
            Box(Modifier.aspectRatio(1f).mediaSharedElement(item.id)) {
                MediaThumbnail(item, cellPx, Modifier.fillMaxSize())
                if (selection.isSelecting) {
                    SelectionCheck(item.id in selection.ids, Modifier.align(Alignment.TopStart).padding(6.dp))
                }
            }
        }
    }
}
