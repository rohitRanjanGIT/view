package com.rohit.gallery.feature.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.AlbumEntry
import com.rohit.gallery.core.ui.components.MediaThumbnail
import com.rohit.gallery.core.ui.components.OrganizeIcons
import com.rohit.gallery.feature.selection.SelectionCheck
import com.rohit.gallery.feature.selection.SelectionTopBar

private val RowThumb = 48.dp
private const val PINNED_HEADER = "pinned-header"
private const val OTHERS_HEADER = "others-header"

/**
 * Edit albums: every album, hidden ones included (dimmed). Pin / unpin and hide / show each one; drag the
 * unpinned albums by their handle to set a custom order (which switches the sort to Custom).
 */
@Composable
fun EditAlbumsScreen(onBack: () -> Unit, viewModel: AlbumsViewModel = hiltViewModel()) {
    val organized by viewModel.organized.collectAsStateWithLifecycle()
    val current = organized
    val selection = rememberAlbumSelection()
    BackHandler(enabled = selection.isSelecting) { selection.clear() }
    val all = current?.let { it.pinned + it.others }.orEmpty()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selection.isSelecting) {
                SelectionTopBar(count = selection.ids.size, onClose = selection::clear, onSelectAll = { selection.selectAll(all) })
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text("Edit albums", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (current != null) {
                AlbumEditList(
                    pinned = current.pinned,
                    others = current.others,
                    selection = selection,
                    onTogglePin = { viewModel.setPinned(listOf(it.album.bucketId), !it.pinned) },
                    onToggleHidden = { viewModel.setHidden(listOf(it.album.bucketId), !it.hidden) },
                    onReorder = viewModel::saveCustomOrder,
                )
            }
        }
        if (selection.isSelecting) {
            AlbumSelectionBar(
                entries = all.filter { it.album.bucketId in selection.ids },
                onPin = { ids, pinned ->
                    viewModel.setPinned(ids, pinned)
                    selection.clear()
                },
                onHide = { ids, hidden ->
                    viewModel.setHidden(ids, hidden)
                    selection.clear()
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun AlbumEditList(
    pinned: List<AlbumEntry>,
    others: List<AlbumEntry>,
    selection: AlbumSelection,
    onTogglePin: (AlbumEntry) -> Unit,
    onToggleHidden: (AlbumEntry) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    val listState = rememberLazyListState()
    // The unpinned albums as currently dragged; reset whenever the saved order changes.
    var order by remember(others) { mutableStateOf(others) }
    val drag = remember { DragState() }
    val haptic = LocalHapticFeedback.current
    val latestOrder by rememberUpdatedState(order)

    LazyColumn(
        state = listState,
        // Room for the selection bar at the bottom.
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    ) {
        if (pinned.isNotEmpty()) {
            item(key = PINNED_HEADER) { SectionLabel("Pinned · in the order you pinned them") }
            items(pinned, key = { it.album.bucketId }) { entry ->
                AlbumEditRow(entry, selection, onTogglePin, onToggleHidden, dragHandle = null, modifier = Modifier.animateItem())
            }
        }
        item(key = OTHERS_HEADER) {
            SectionLabel(if (pinned.isEmpty()) "Drag to reorder · long-press to select" else "Albums · drag to reorder")
        }
        items(order, key = { it.album.bucketId }) { entry ->
            val id = entry.album.bucketId
            val dragging = drag.id == id
            AlbumEditRow(
                entry = entry,
                selection = selection,
                onTogglePin = onTogglePin,
                onToggleHidden = onToggleHidden,
                dragHandle = Modifier.pointerInput(id) {
                    detectDragGestures(
                        onDragStart = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            drag.start(id)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            drag.offset += amount.y
                            drag.swapIfPassed(listState, latestOrder) { order = it }
                        },
                        onDragEnd = {
                            drag.stop()
                            onReorder(latestOrder.map { it.album.bucketId })
                        },
                        onDragCancel = { drag.stop() },
                    )
                },
                modifier = Modifier
                    .then(if (dragging) Modifier.zIndex(1f) else Modifier.animateItem())
                    .graphicsLayer {
                        translationY = if (dragging) drag.offset else 0f
                        shadowElevation = if (dragging) 8.dp.toPx() else 0f
                    },
            )
        }
    }
}

/** The row being dragged and how far it has moved from its current list slot. */
private class DragState {
    var id by mutableStateOf<Long?>(null)
        private set
    var offset by mutableFloatStateOf(0f)

    fun start(bucketId: Long) {
        id = bucketId
        offset = 0f
    }

    fun stop() {
        id = null
        offset = 0f
    }

    /**
     * When the dragged row's centre passes into a neighbour, moves it there in [order] and keeps it under the
     * finger by shifting [offset] by the distance between the two slots.
     */
    fun swapIfPassed(listState: LazyListState, order: List<AlbumEntry>, onOrder: (List<AlbumEntry>) -> Unit) {
        val draggedId = id ?: return
        val visible = listState.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == draggedId } ?: return
        val centre = dragged.offset + dragged.size / 2 + offset
        val target = visible.firstOrNull { info ->
            info.key != draggedId && info.key is Long && order.any { it.album.bucketId == info.key } &&
                centre >= info.offset && centre < info.offset + info.size
        } ?: return
        val from = order.indexOfFirst { it.album.bucketId == draggedId }
        val to = order.indexOfFirst { it.album.bucketId == target.key }
        if (from < 0 || to < 0) return
        onOrder(order.toMutableList().apply { add(to, removeAt(from)) })
        offset += dragged.offset - target.offset
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/**
 * One album: thumbnail, name and count, pin and show/hide buttons, and a drag handle for unpinned albums.
 * Long-press selects it; while selecting, a tap toggles it and the per-row buttons make way for a check.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumEditRow(
    entry: AlbumEntry,
    selection: AlbumSelection,
    onTogglePin: (AlbumEntry) -> Unit,
    onToggleHidden: (AlbumEntry) -> Unit,
    dragHandle: Modifier?,
    modifier: Modifier = Modifier,
) {
    val thumbPx = with(LocalDensity.current) { RowThumb.roundToPx() }
    val id = entry.album.bucketId
    val selecting = selection.isSelecting
    val haptic = LocalHapticFeedback.current
    Surface(
        color = if (id in selection.ids) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selecting) selection.toggle(id) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selection.toggle(id)
                },
            ),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selecting) SelectionCheck(id in selection.ids)
            // Hidden albums are dimmed, but their buttons stay fully visible.
            val dim = if (entry.hidden) 0.4f else 1f
            MediaThumbnail(
                item = entry.album.cover,
                cellSizePx = thumbPx,
                modifier = Modifier.size(RowThumb).clip(RoundedCornerShape(10.dp)).alpha(dim),
                showBadge = false,
            )
            Column(Modifier.weight(1f).alpha(dim)) {
                Text(entry.album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (entry.hidden) "${entry.album.count} · Hidden" else "${entry.album.count}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selecting) return@Row
            IconButton(onClick = { onTogglePin(entry) }) {
                Icon(
                    OrganizeIcons.Pin,
                    contentDescription = if (entry.pinned) "Unpin" else "Pin to top",
                    tint = if (entry.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            IconButton(onClick = { onToggleHidden(entry) }) {
                Icon(
                    if (entry.hidden) OrganizeIcons.Hidden else OrganizeIcons.Visible,
                    contentDescription = if (entry.hidden) "Show album" else "Hide album",
                )
            }
            if (dragHandle != null) {
                Icon(
                    OrganizeIcons.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandle.padding(12.dp),
                )
            }
        }
    }
}
