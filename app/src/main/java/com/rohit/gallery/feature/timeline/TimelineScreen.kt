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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
                TimelineGrid(s, selection, contentBottomPadding, onOpenMedia)
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
) {
    val columns = content.columns
    val density = LocalDensity.current
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val gapPx = with(density) { CellGap.roundToPx() }
    val cellSizePx = (windowWidthPx - gapPx * (columns - 1)) / columns
    val today = remember { LocalDate.now() }

    val listState = rememberLazyListState()
    PrefetchThumbnails(listState, content.days, cellSizePx)

    val currentContent by rememberUpdatedState(content)
    val haptic = LocalHapticFeedback.current

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

    // While drag-selecting, scroll when the finger is near the top or bottom edge. The pointer
    // lives in a plain holder (not state) so moving the finger never recomposes the grid.
    val pointer = remember { object { var position: Offset? = null } }
    var dragActive by remember { mutableStateOf(false) }
    val topEdgePx = with(density) { WindowInsets.statusBars.getTop(this).toFloat() + AutoScrollEdge.toPx() }
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

    LazyColumn(
        state = listState,
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
                    itemAt(pos)?.let { selection.dragTo(it.index, currentContent.items) }
                },
                onDragEnd = {
                    dragActive = false
                    pointer.position = null
                    selection.endDrag()
                },
            ),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = contentBottomPadding,
        ),
    ) {
        content.days.forEach { day ->
            stickyHeader(key = "day-${day.epochDay}", contentType = "header") {
                DayHeader(dayLabel(day.epochDay, today))
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
