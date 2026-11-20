package com.rohit.gallery.feature.timeline

import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.hazeSource
import com.rohit.gallery.core.ui.components.frostedChrome
import com.rohit.gallery.core.ui.components.rememberChromeHaze
import com.rohit.gallery.core.ui.components.LocalChromeHaze
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import com.rohit.gallery.core.ui.components.FastScroller
import com.rohit.gallery.core.ui.components.GridZoomState
import com.rohit.gallery.core.ui.components.ZoomAnchorCell
import com.rohit.gallery.core.ui.components.ZoomOverlay
import com.rohit.gallery.core.ui.components.pinchToZoom
import com.rohit.gallery.core.ui.components.zoomLayer
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
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.rohit.gallery.core.model.mediaCountLabel
import com.rohit.gallery.core.ui.components.ReachDown
import com.rohit.gallery.core.ui.components.ReachTopIcons
import com.rohit.gallery.core.ui.components.rememberReachState
import com.rohit.gallery.core.permissions.MediaAccessGate
import com.rohit.gallery.core.ui.components.MediaThumbnail
import com.rohit.gallery.core.ui.components.FollowViewerPhoto
import com.rohit.gallery.core.ui.components.mediaSharedElement
import com.rohit.gallery.core.ui.theme.GalleryMotion
import com.rohit.gallery.feature.selection.SelectionActionsBar
import com.rohit.gallery.feature.selection.SelectionCheck
import com.rohit.gallery.feature.selection.SelectionState
import com.rohit.gallery.feature.selection.SelectionTopBar
import com.rohit.gallery.feature.selection.gridGestures
import com.rohit.gallery.feature.selection.GridPressState
import com.rohit.gallery.feature.selection.pressScale
import com.rohit.gallery.feature.selection.rememberGridPressState
import java.time.LocalDate
import kotlinx.coroutines.launch

private val CellGap = 2.dp
private val SecondaryTitleBarHeight = 64.dp
private val AutoScrollEdge = 96.dp
private const val AUTO_SCROLL_MAX_PX = 36f

@Composable
fun TimelineScreen(
    onOpenMedia: (MediaItem) -> Unit,
    onOpenSearch: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    MediaAccessGate(modifier.fillMaxSize()) {
        TimelineContent(viewModel, onOpenMedia, onOpenSearch, onSelectionModeChange, contentBottomPadding)
    }
}

@Composable
private fun TimelineContent(
    viewModel: TimelineViewModel,
    onOpenMedia: (MediaItem) -> Unit,
    onOpenSearch: () -> Unit,
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
    // The selection bars float over the grid as frosted glass; the grid keeps scrolling underneath.
    val haze = rememberChromeHaze()
    // Pull-down-to-reach, held here so the search button can ride down with the grid.
    val reach = rememberReachState()

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            TimelineUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            TimelineUiState.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No photos or videos yet", style = MaterialTheme.typography.titleMedium)
            }
            is TimelineUiState.Content -> {
                val counts = remember(s.items) { mediaCountLabel(s.items) }
                Box(Modifier.fillMaxSize().hazeSource(haze)) {
                    // Pull down at the top to bring the photos into thumb reach.
                    ReachDown(
                        title = "Pictures",
                        subtitle = counts,
                        topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                        state = reach,
                        actions = { if (!selecting) SearchIcon(onOpenSearch) },
                        // On the "Today" line, as when the grid is up (a 48 dp icon row centred on the day header).
                        actionsOffset = DayHeaderHeight / 2 + 24.dp,
                    ) {
                        TimelineGrid(s, selection, contentBottomPadding, onOpenMedia, viewModel::setLevel)
                    }
                }
                if (!selecting) ReachTopIcons(reach, Modifier.align(Alignment.TopEnd)) { SearchButton(onOpenSearch) }
                if (selecting) {
                    CompositionLocalProvider(LocalChromeHaze provides haze) {
                        SelectionBars(viewModel, s, selection, Modifier.matchParentSize())
                    }
                }
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
fun TimelineGrid(
    content: TimelineUiState.Content,
    selection: SelectionState,
    contentBottomPadding: Dp,
    onOpenMedia: (MediaItem) -> Unit,
    onLevelChange: (GridLevel) -> Unit,
    underStatusBar: Boolean = true,
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
    // Below a title bar (secondary screens) the list starts lower; an estimate of its height is enough.
    val topChromePx = WindowInsets.statusBars.getTop(density) +
        if (underStatusBar) 0 else with(density) { SecondaryTitleBarHeight.roundToPx() }
    val visiblePx = LocalWindowInfo.current.containerSize.height -
        topChromePx - with(density) { contentBottomPadding.roundToPx() }
    FollowViewerItem(listState, content, scrollMap, headerPx, rowPx, visiblePx)

    val zoom = remember { GridZoomState() }
    val gridLayer = rememberGraphicsLayer()
    KeepZoomAnchor(zoom, content, listState, scrollMap, rowPx, cellSizePx, gapPx)

    val currentContent by rememberUpdatedState(content)
    val haptic = LocalHapticFeedback.current
    val press = rememberGridPressState()
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
    fun anchorAt(position: Offset): ZoomAnchorCell? {
        val info = listState.layoutInfo
        val y = position.y + info.viewportStartOffset
        val row = info.visibleItemsInfo
            .filter { (it.key as? String)?.startsWith(ROW_KEY_PREFIX) == true }
            .minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - y) } ?: return null
        val items = currentContent.rowsByFirstId[(row.key as String).removePrefix(ROW_KEY_PREFIX).toLongOrNull()]
            ?: return null
        val column = (position.x / (cellSizePx + gapPx)).toInt().coerceIn(0, items.lastIndex)
        val centre = Offset(
            x = column * (cellSizePx + gapPx) + cellSizePx / 2f,
            y = (row.offset - info.viewportStartOffset + cellSizePx / 2).toFloat(),
        )
        return ZoomAnchorCell(items[column].id, centre)
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

    // Same colour as the day headers, so gaps and the empty end of a short row match them (the window
    // behind is a slightly different shade).
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Box(
        Modifier
            .fillMaxSize()
            // The list starts below the status bar (not top content padding): a pinned day header
            // sits at the viewport top, and with padding it slid under the clock.
            .then(if (underStatusBar) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
            .clipToBounds()
            // Outside the scaled list, so finger positions are not affected by the scale they drive.
            .pinchToZoom(
                onPinch = { scale, centroid ->
                    val level = currentContent.level
                    zoom.onPinch(
                        scale = scale,
                        centroid = centroid,
                        columns = level.columns,
                        canZoomIn = level.zoomIn() != null,
                        canZoomOut = level.zoomOut() != null,
                        scope = scope,
                        snapshot = { gridLayer.toImageBitmap() },
                        anchorAt = ::anchorAt,
                        onSwitch = { zoomIn ->
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            (if (zoomIn) level.zoomIn() else level.zoomOut())?.let(onLevelChange)
                        },
                    )
                },
                onPinchEnd = { zoom.onPinchEnd(scope) },
            ),
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .zoomLayer(zoom)
            // Records the grid into a layer as it draws, so a density switch can snapshot the old grid.
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
                    itemAt(pos)?.let { selection.dragTo(it.index, currentContent.items) }
                },
                onDragEnd = {
                    dragActive = false
                    pointer.position = null
                    selection.endDrag()
                },
                onPress = { pos, untilLongPressMs -> itemAt(pos)?.let { press.press(it.item.id, untilLongPressMs) } },
                onPressEnd = { press.release() },
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
                ThumbnailRow(row, columns, cellSizePx, selection, press)
            }
        }
    }
    ZoomOverlay(zoom)
    }
    TimelineFastScroller(
        listState = listState,
        map = scrollMap,
        bottomPadding = contentBottomPadding,
        underStatusBar = underStatusBar,
        modifier = Modifier.align(Alignment.TopEnd),
    )
    }
}

/**
 * While the viewer is open over this grid, keeps the photo it shows in view underneath (centered) unless it is
 * already fully visible, so the close flight always has a cell to land in (see [FollowViewerPhoto]).
 */
@Composable
private fun FollowViewerItem(
    listState: LazyListState,
    content: TimelineUiState.Content,
    map: TimelineScrollMap,
    headerPx: Int,
    rowPx: Int,
    visiblePx: Int,
) {
    val latest by rememberUpdatedState(FollowGeometry(content, map, headerPx, rowPx, visiblePx))
    FollowViewerPhoto { id ->
        val g = latest
        val itemIndex = g.content.indexById[id] ?: return@FollowViewerPhoto
        val rowIndex = lazyRowIndexOf(g.content.days, itemIndex) ?: return@FollowViewerPhoto
        val rowTop = g.map.offsetOf(rowIndex, 0)
        val top = g.map.offsetOf(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        // The pinned day header covers the top of the list.
        val fullyVisible = rowTop >= top + g.headerPx && rowTop + g.rowPx <= top + g.visiblePx
        if (!fullyVisible) {
            val (index, offset) = g.map.positionAt((rowTop - (g.visiblePx - g.rowPx) / 2).coerceAtLeast(0L))
            listState.scrollToItem(index, offset)
        }
    }
}

/** What [FollowViewerItem] needs from the latest composition. */
private class FollowGeometry(
    val content: TimelineUiState.Content,
    val map: TimelineScrollMap,
    val headerPx: Int,
    val rowPx: Int,
    val visiblePx: Int,
)

/** Fast-scroll handle for the timeline, with the month under the handle in a bubble while dragging. */
@Composable
private fun TimelineFastScroller(
    listState: LazyListState,
    map: TimelineScrollMap,
    bottomPadding: Dp,
    underStatusBar: Boolean,
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
        // On Pictures the track starts below the pinned day header, leaving its right end to the search button.
        topPadding = if (underStatusBar) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + DayHeaderHeight else 0.dp,
        bottomPadding = bottomPadding,
        modifier = modifier,
    )
}

/** Search, at the right end of the pinned day header (the header leaves that space empty). */
@Composable
private fun SearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 8 dp in from the edge, like the Albums tab's ⋮, so the icon doesn't jump sideways when switching tabs.
    Box(modifier.windowInsetsPadding(WindowInsets.statusBars).padding(end = 8.dp).height(DayHeaderHeight), Alignment.Center) {
        SearchIcon(onClick)
    }
}

@Composable
private fun SearchIcon(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Search, contentDescription = "Search")
    }
}

/** Rough height of a day header: title text plus its 10 dp padding above and below. */
private val DayHeaderHeight = 40.dp

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
private fun ThumbnailRow(row: List<MediaItem>, columns: Int, cellSizePx: Int, selection: SelectionState, press: GridPressState) {
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
                .pressScale(press, item.id)
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
