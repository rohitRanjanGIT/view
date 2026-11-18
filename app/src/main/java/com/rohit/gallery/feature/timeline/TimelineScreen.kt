package com.rohit.gallery.feature.timeline

import android.app.Activity
import android.content.IntentSender
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import com.rohit.gallery.core.ui.components.FastScroller
import com.rohit.gallery.core.ui.components.LocalViewerReturnTarget
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.permissions.MediaAccessGate
import com.rohit.gallery.core.ui.components.MediaThumbnail
import com.rohit.gallery.core.ui.components.mediaSharedElement
import com.rohit.gallery.core.ui.theme.GalleryMotion
import com.rohit.gallery.feature.selection.SelectionActionsBar
import com.rohit.gallery.feature.selection.SelectionCheck
import com.rohit.gallery.feature.selection.SelectionState
import com.rohit.gallery.feature.selection.SelectionTopBar
import com.rohit.gallery.feature.selection.gridGestures
import java.time.LocalDate
import kotlinx.coroutines.launch

private val CellGap = 2.dp
private val AutoScrollEdge = 96.dp
private const val AUTO_SCROLL_MAX_PX = 36f

@Composable
fun TimelineScreen(
    onOpenMedia: (MediaItem) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    MediaAccessGate(modifier.fillMaxSize()) {
        TimelineContent(viewModel, onOpenMedia, onSelectionModeChange, contentBottomPadding)
    }
}

@Composable
private fun TimelineContent(
    viewModel: TimelineViewModel,
    onOpenMedia: (MediaItem) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    contentBottomPadding: Dp,
) {
    // Also covers first load, permission changes and returning from the photo picker.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection = viewModel.selection
    val selecting = selection.isSelecting

    // The nav host hides its bottom bar while selecting; this screen shows its own action bar instead.
    LaunchedEffect(selecting) { onSelectionModeChange(selecting) }
    DisposableEffect(Unit) { onDispose { onSelectionModeChange(false) } }
    BackHandler(enabled = selecting) { selection.clear() }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            TimelineUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            TimelineUiState.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No photos or videos yet", style = MaterialTheme.typography.titleMedium)
            }
            is TimelineUiState.Content -> {
                TimelineGrid(s, selection, contentBottomPadding, onOpenMedia, viewModel::setLevel)
                if (selecting) SelectionBars(viewModel, s, selection, Modifier.matchParentSize())
            }
        }
    }
}

/** Top and bottom bars shown while selecting, plus the actions they trigger. */
@Composable
private fun SelectionBars(
    viewModel: TimelineViewModel,
    content: TimelineUiState.Content,
    selection: SelectionState,
    modifier: Modifier = Modifier,
) {
    val allFavorite by remember(content) {
        derivedStateOf {
            selection.ids.isNotEmpty() && selection.ids.all { id ->
                content.indexById[id]?.let { content.items[it].isFavorite } == true
            }
        }
    }

    Box(modifier) {
        SelectionTopBar(
            count = selection.ids.size,
            onClose = selection::clear,
            onSelectAll = { selection.selectAll(content.items) },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        SelectionActionsBar(
            controller = viewModel.selectionActions,
            selection = selection,
            allFavorite = allFavorite,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private class HitResult(val item: MediaItem, val index: Int)

@Composable
private fun TimelineGrid(
    content: TimelineUiState.Content,
    selection: SelectionState,
    contentBottomPadding: Dp,
    onOpenMedia: (MediaItem) -> Unit,
    onLevelChange: (GridLevel) -> Unit,
) {
    val columns = content.columns
    val density = LocalDensity.current
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val gapPx = with(density) { CellGap.roundToPx() }
    val cellSizePx = (windowWidthPx - gapPx * (columns - 1)) / columns
    val today = remember { LocalDate.now() }
    val locale = remember { Locale.getDefault() }

    val listState = rememberLazyListState()
    PrefetchThumbnails(listState, content.days, cellSizePx)

    // Header and row heights, measured from the list (they follow font scale and cell size). Saved so a
    // screen coming back from the viewer has the real values before its first layout.
    var headerPx by rememberSaveable { mutableIntStateOf(with(density) { 40.dp.roundToPx() }) }
    var rowPx by rememberSaveable(cellSizePx) { mutableIntStateOf(cellSizePx + gapPx) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            visible.firstOrNull { it.contentType == "header" }?.size to
                visible.firstOrNull { it.contentType == "row" }?.size
        }.collect { (header, row) ->
            if (header != null && header > 0) headerPx = header
            if (row != null && row > 0) rowPx = row
        }
    }
    val scrollMap = remember(content.days, headerPx, rowPx) { TimelineScrollMap(content.days, headerPx, rowPx) }

    // Height of the list area that shows content: below the status bar, above the bottom bar.
    val visiblePx = LocalWindowInfo.current.containerSize.height -
        WindowInsets.statusBars.getTop(density) - with(density) { contentBottomPadding.roundToPx() }
    ScrollToReturnedItem(listState, content, scrollMap, headerPx, rowPx, visiblePx)

    val zoom = remember { TimelineZoomState() }
    KeepZoomAnchor(zoom, content, listState, scrollMap, rowPx, cellSizePx, gapPx)

    val currentContent by rememberUpdatedState(content)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    /** The cell under [position] (this list's coordinates), or null on a header, gap or empty space. */
    fun itemAt(position: Offset): HitResult? {
        val info = listState.layoutInfo
        // Item offsets are measured from the padded content start; viewportStartOffset is -padding.
        val y = position.y + info.viewportStartOffset
        val row = info.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size } ?: return null
        val key = row.key as? String ?: return null
        if (!key.startsWith(ROW_KEY_PREFIX)) return null
        val c = currentContent
        val items = c.rowsByFirstId[key.removePrefix(ROW_KEY_PREFIX).toLongOrNull()] ?: return null
        val column = (position.x / (cellSizePx + gapPx)).toInt().coerceIn(0, c.columns - 1)
        val item = items.getOrNull(column) ?: return null
        return HitResult(item, c.indexById[item.id] ?: return null)
    }

    /** The item to keep in place when a pinch at [position] changes density: the cell there, or the nearest row's. */
    fun anchorAt(position: Offset): Long? {
        itemAt(position)?.let { return it.item.id }
        val info = listState.layoutInfo
        val y = position.y + info.viewportStartOffset
        val row = info.visibleItemsInfo
            .filter { (it.key as? String)?.startsWith(ROW_KEY_PREFIX) == true }
            .minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - y) } ?: return null
        val items = currentContent.rowsByFirstId[(row.key as String).removePrefix(ROW_KEY_PREFIX).toLongOrNull()]
            ?: return null
        val column = (position.x / (cellSizePx + gapPx)).toInt().coerceIn(0, items.lastIndex)
        return items[column].id
    }

    // While drag-selecting, scroll when the finger is near the top or bottom edge. The pointer
    // lives in a plain holder (not state) so moving the finger never recomposes the grid.
    val pointer = remember { object { var position: Offset? = null } }
    var dragActive by remember { mutableStateOf(false) }
    // Pointer positions are relative to the list, which already starts below the status bar.
    val topEdgePx = with(density) { AutoScrollEdge.toPx() }
    val bottomEdgePx = with(density) { (contentBottomPadding + AutoScrollEdge).toPx() }
    LaunchedEffect(dragActive) {
        while (dragActive) {
            val p = pointer.position ?: break
            val height = listState.layoutInfo.viewportSize.height
            val step = when {
                p.y < topEdgePx -> -((topEdgePx - p.y) / (AutoScrollEdge.value * density.density)).coerceIn(0f, 1f)
                p.y > height - bottomEdgePx -> ((p.y - (height - bottomEdgePx)) / (AutoScrollEdge.value * density.density)).coerceIn(0f, 1f)
                else -> 0f
            } * AUTO_SCROLL_MAX_PX
            if (step != 0f) {
                listState.scrollBy(step)
                itemAt(p)?.let { selection.dragTo(it.index, currentContent.items) }
            }
            androidx.compose.runtime.withFrameNanos { }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxSize()
            // The list starts below the status bar (not top content padding): a pinned day header
            // sits at the viewport top, and with padding it slid under the clock.
            .windowInsetsPadding(WindowInsets.statusBars)
            .clipToBounds()
            // Outside the scaled list, so finger positions are not affected by the scale they drive.
            .pinchToZoom(
                onPinch = zoom::onPinch,
                onPinchEnd = { _, centroid ->
                    zoom.onPinchEnd(scope, currentContent.level, centroid, ::anchorAt)?.let { level ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLevelChange(level)
                    }
                },
            ),
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .zoomLayer(zoom)
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
                    itemAt(pos)?.let { selection.dragTo(it.index, currentContent.items) }
                },
                onDragEnd = {
                    dragActive = false
                    pointer.position = null
                    selection.endDrag()
                },
            ),
        contentPadding = PaddingValues(bottom = contentBottomPadding),
    ) {
        val byMonth = content.level.byMonth
        content.days.forEach { day ->
            stickyHeader(key = "day-${day.epochDay}", contentType = "header") {
                DayHeader(if (byMonth) monthLabel(day.epochDay, locale) else dayLabel(day.epochDay, today))
            }
            items(
                items = day.rows,
                key = { row -> rowKey(row) },
                contentType = { "row" },
            ) { row ->
                ThumbnailRow(row, columns, cellSizePx, selection)
            }
        }
    }
    }
    TimelineFastScroller(
        listState = listState,
        map = scrollMap,
        bottomPadding = contentBottomPadding,
        modifier = Modifier.align(Alignment.TopEnd),
    )
    }
}

/**
 * When this grid reappears after the viewer closes, scrolls the photo the viewer ended on into view
 * (centered) unless it is already fully visible. Runs once, during the first composition, through
 * [LazyListState.requestScrollToItem], so the very first frame is already in place for the close flight.
 */
@Composable
private fun ScrollToReturnedItem(
    listState: LazyListState,
    content: TimelineUiState.Content,
    map: TimelineScrollMap,
    headerPx: Int,
    rowPx: Int,
    visiblePx: Int,
) {
    val returnTarget = LocalViewerReturnTarget.current
    remember {
        val id = returnTarget.take() ?: return@remember
        val itemIndex = content.indexById[id] ?: return@remember
        val rowIndex = lazyRowIndexOf(content.days, itemIndex) ?: return@remember
        val rowTop = map.offsetOf(rowIndex, 0)
        val top = map.offsetOf(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        // The pinned day header covers the top of the list.
        val fullyVisible = rowTop >= top + headerPx && rowTop + rowPx <= top + visiblePx
        if (!fullyVisible) {
            val (index, offset) = map.positionAt((rowTop - (visiblePx - rowPx) / 2).coerceAtLeast(0L))
            listState.requestScrollToItem(index, offset)
        }
    }
}

/** Fast-scroll handle for the timeline, with the month under the handle in a bubble while dragging. */
@Composable
private fun TimelineFastScroller(
    listState: LazyListState,
    map: TimelineScrollMap,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    // Scrollable distance: content height minus the part of the viewport inside the paddings.
    fun maxScrollPx(): Long {
        val info = listState.layoutInfo
        val visibleContent = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
        return (map.totalPx - visibleContent).coerceAtLeast(1L)
    }

    // Only worth it when the timeline is several screens long.
    val longEnough by remember(map) {
        derivedStateOf { map.totalPx > 4L * listState.layoutInfo.viewportSize.height.coerceAtLeast(1) }
    }
    if (!longEnough) return

    // Drag positions go through a conflated channel: each is scrolled to once (latest wins during a fast
    // drag). Not state: a kept "last target" was re-applied whenever the map changed (any library change),
    // jumping the list back to where the handle was last released.
    val latestMap by rememberUpdatedState(map)
    val targets = remember { Channel<Float>(Channel.CONFLATED) }
    LaunchedEffect(listState) {
        for (fraction in targets) {
            val current = latestMap
            val info = listState.layoutInfo
            val visibleContent = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
            val maxScroll = (current.totalPx - visibleContent).coerceAtLeast(1L)
            val (index, offset) = current.positionAt((fraction * maxScroll).toLong())
            listState.scrollToItem(index, offset)
        }
    }

    val locale = remember { Locale.getDefault() }
    FastScroller(
        scrollFraction = {
            map.offsetOf(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset).toFloat() / maxScrollPx()
        },
        isScrolling = listState.isScrollInProgress,
        onDragTo = { targets.trySend(it) },
        labelAt = { fraction ->
            map.epochDayAt((fraction * maxScrollPx()).toLong())?.let { monthLabel(it, locale) }.orEmpty()
        },
        topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        bottomPadding = bottomPadding,
        modifier = modifier,
    )
}

@Composable
private fun DayHeader(label: String) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ThumbnailRow(row: List<MediaItem>, columns: Int, cellSizePx: Int, selection: SelectionState) {
    val selecting = selection.isSelecting
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = CellGap),
        horizontalArrangement = Arrangement.spacedBy(CellGap),
    ) {
        row.forEach { item ->
            val cellModifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .mediaSharedElement(item.id)
            if (selecting) {
                SelectableThumbnail(item, cellSizePx, selection, cellModifier)
            } else {
                MediaThumbnail(item = item, cellSizePx = cellSizePx, modifier = cellModifier)
            }
        }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** A grid cell in selection mode: shrinks with rounded corners when selected and shows a check badge. */
@Composable
private fun SelectableThumbnail(
    item: MediaItem,
    cellSizePx: Int,
    selection: SelectionState,
    modifier: Modifier = Modifier,
) {
    // derivedStateOf: only cells whose own membership changes recompose when the selection changes.
    val selected by remember(item.id) { derivedStateOf { item.id in selection.ids } }
    val scale by animateFloatAsState(if (selected) 0.86f else 1f, GalleryMotion.snappy(), label = "selectScale")
    Box(modifier) {
        MediaThumbnail(
            item = item,
            cellSizePx = cellSizePx,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape(10.dp)
                    clip = scale < 1f
                },
        )
        SelectionCheck(selected, Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}
