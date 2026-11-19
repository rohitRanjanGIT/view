package com.rohit.gallery.feature.albums

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.saveable.rememberSaveable
import com.rohit.gallery.feature.selection.NewAlbumDialog
import com.rohit.gallery.core.model.albumCountLabel
import com.rohit.gallery.core.ui.components.ReachDown
import com.rohit.gallery.core.ui.components.rememberReachState
import com.rohit.gallery.core.ui.components.ReachState
import com.rohit.gallery.core.ui.components.ReachTopIcons
import com.rohit.gallery.core.ui.components.ReachBarTitle
import com.rohit.gallery.feature.vault.rememberVaultLocker
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.hazeSource
import com.rohit.gallery.core.ui.components.frostedChrome
import com.rohit.gallery.core.ui.components.rememberChromeHaze
import com.rohit.gallery.core.ui.components.LocalChromeHaze
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.Album
import com.rohit.gallery.core.model.AlbumEntry
import com.rohit.gallery.core.model.AlbumSort
import com.rohit.gallery.core.permissions.MediaAccessGate
import com.rohit.gallery.core.ui.components.GridGeometry
import com.rohit.gallery.core.ui.components.GridZoomState
import com.rohit.gallery.core.ui.components.KeepGridZoomAnchor
import com.rohit.gallery.core.ui.components.MediaThumbnail
import com.rohit.gallery.core.ui.components.ZoomAnchorCell
import com.rohit.gallery.core.ui.components.ZoomOverlay
import com.rohit.gallery.core.ui.components.pinchToZoom
import com.rohit.gallery.core.ui.components.zoomLayer
import com.rohit.gallery.core.ui.components.OrganizeIcons
import com.rohit.gallery.core.ui.theme.GalleryMotion
import com.rohit.gallery.feature.selection.SelectionCheck
import com.rohit.gallery.feature.selection.SelectionTopBar

/** Albums per row a pinch steps through. */
private val ALBUM_COLUMN_STEPS = listOf(2, 3, 4)
private val ALBUM_EDGE = 16.dp
private val ALBUM_TOP = 8.dp
private val CardPlacement = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
private val ALBUM_GAP = 12.dp

/**
 * The Albums tab: pinned albums first (in pin order), then the rest in the chosen sort. Long-press
 * selects albums to pin / unpin or lock. Pulling down and holding for 2 seconds opens Locked (its only way in).
 */
@Composable
fun AlbumsScreen(
    onOpenAlbum: (Album) -> Unit,
    onEditAlbums: () -> Unit,
    onOpenLocked: () -> Unit,
    onOpenSearch: () -> Unit,
    onCreateAlbum: (name: String) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    MediaAccessGate(modifier.fillMaxSize()) {
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
        val organized by viewModel.organized.collectAsStateWithLifecycle()
        val columns by viewModel.columns.collectAsStateWithLifecycle()
        val current = organized
        val selection = rememberAlbumSelection()

        // The nav host hides its bottom bar while selecting; this screen shows its own action bar instead.
        LaunchedEffect(selection.isSelecting) { onSelectionModeChange(selection.isSelecting) }
        DisposableEffect(Unit) { onDispose { onSelectionModeChange(false) } }
        BackHandler(enabled = selection.isSelecting) { selection.clear() }

        val haze = rememberChromeHaze()
        // Also says whether Locked is turned on (Settings): off, there is no Lock action and no hold to open it.
        val locker = rememberVaultLocker(onLocked = { if (it > 0) selection.clear() })
        // Delete: everything in the albums goes to Trash; the albums then disappear.
        val deleter = rememberAlbumDeleter(onDeleted = { selection.clear() })
        val list = current?.visible
        // Held here so the header's small title can fade out while the big one shows (grid pulled down).
        val reach = rememberReachState()
        // New album (+), Search and the ⋮ menu: in the header, and riding above the grid while it is pulled down.
        var naming by rememberSaveable { mutableStateOf(false) }
        val icons: @Composable RowScope.() -> Unit = {
            IconButton(onClick = { naming = true }) { Icon(Icons.Filled.Add, contentDescription = "New album") }
            IconButton(onClick = onOpenSearch) { Icon(Icons.Filled.Search, contentDescription = "Search") }
            AlbumsMenu(current?.sort ?: AlbumSort.Newest, viewModel::setSort, onEditAlbums)
        }
        if (naming) {
            NewAlbumDialog(
                existingNames = list?.map { it.album.name }.orEmpty(),
                onCreate = { name ->
                    naming = false
                    onCreateAlbum(name)
                },
                onDismiss = { naming = false },
            )
        }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().hazeSource(haze)) {
                if (selection.isSelecting) {
                    SelectionTopBar(
                        count = selection.ids.size,
                        onClose = selection::clear,
                        onSelectAll = { list?.let(selection::selectAll) },
                    )
                } else {
                    AlbumsHeader(
                        icons = icons,
                        reach = reach,
                        modifier = Modifier.statusBarsPadding(),
                    )
                }
                when {
                    list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("No albums yet", style = MaterialTheme.typography.titleMedium)
                    }
                    else -> columns?.let { cols ->
                        // Pull down at the top to bring the albums into thumb reach.
                        ReachDown(
                            title = "Albums",
                            subtitle = albumCountLabel(list.size),
                            state = reach,
                            actions = icons,
                            // Locked has no button anywhere: pull down here and hold for 2 seconds.
                            onHold = if (locker.enabled) onOpenLocked else null,
                        ) {
                            AlbumGrid(
                                list = list,
                                columns = cols,
                                selection = selection,
                                contentBottomPadding = contentBottomPadding,
                                onOpenAlbum = onOpenAlbum,
                                onColumnsChange = viewModel::setColumns,
                            )
                        }
                    }
                }
            }
            if (selection.isSelecting && list != null) CompositionLocalProvider(LocalChromeHaze provides haze) {
                AlbumSelectionBar(
                    entries = list.filter { it.album.bucketId in selection.ids },
                    onPin = { ids, pinned ->
                        viewModel.setPinned(ids, pinned)
                        selection.clear()
                    },
                    // The whole album moves into Locked; it disappears from Albums once the originals are deleted.
                    onLock = if (locker.enabled) { ids -> locker.lock(viewModel.itemsIn(ids)) } else null,
                    onDelete = { ids -> deleter.delete(viewModel.itemsIn(ids), albums = ids.size) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * The album cards, [columns] per row. Pinch in / out steps through [ALBUM_COLUMN_STEPS]; the album under the
 * fingers stays in place while the old layout cross-fades into the new one (see GridZoomState).
 */
@Composable
internal fun AlbumGrid(
    list: List<AlbumEntry>,
    columns: Int,
    selection: AlbumSelection,
    contentBottomPadding: Dp,
    onOpenAlbum: (Album) -> Unit,
    onColumnsChange: (Int) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val widthPx = LocalWindowInfo.current.containerSize.width
    val edgePx = with(density) { ALBUM_EDGE.toPx() }
    val gapPx = with(density) { ALBUM_GAP.toPx() }
    val topPx = with(density) { ALBUM_TOP.toPx() }
    val cardWidth = (widthPx - 2 * edgePx - gapPx * (columns - 1)) / columns
    val currentColumns by rememberUpdatedState(columns)
    val currentList by rememberUpdatedState(list)
    val indexById = remember(list) { list.withIndex().associate { (i, e) -> e.album.bucketId to i } }

    // Height of the name and count under each picture, measured from a laid-out card (it follows font scale).
    val labelPx = remember { object { var value = with(density) { 48.dp.toPx() } } }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.size }
            .collect { size -> if (size != null && size.height > size.width) labelPx.value = (size.height - size.width).toFloat() }
    }

    val shownColumns = remember { object { var value = columns } }
    val columnsChanged = shownColumns.value != columns
    SideEffect { shownColumns.value = columns }

    val zoom = remember { GridZoomState() }
    val gridLayer = rememberGraphicsLayer()
    KeepGridZoomAnchor(
        zoom = zoom,
        gridState = gridState,
        geometry = GridGeometry(
            columns = columns,
            cellWidth = cardWidth,
            columnPitch = cardWidth + gapPx,
            rowPitch = cardWidth + labelPx.value + gapPx,
            anchorY = cardWidth / 2,
            startPadding = edgePx,
            topPadding = topPx,
        ),
        indexOf = { indexById[it] },
    )

    /** The album nearest to [position]; its picture's centre, in the grid's own coordinates. */
    fun anchorAt(position: Offset): ZoomAnchorCell? {
        val info = gridState.layoutInfo
        val cell = info.visibleItemsInfo.minByOrNull {
            val centreY = it.offset.y - info.viewportStartOffset + it.size.width / 2f
            kotlin.math.abs(centreY - position.y) * 4 + kotlin.math.abs(it.offset.x + it.size.width / 2f - position.x)
        } ?: return null
        val index = cell.index
        val pitch = cardWidth + gapPx
        val centre = Offset(
            x = edgePx + (index % currentColumns) * pitch + cardWidth / 2,
            y = cell.offset.y - info.viewportStartOffset + cardWidth / 2,
        )
        return ZoomAnchorCell(currentList[index].album.bucketId, centre)
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pinchToZoom(
                onPinch = { scale, centroid ->
                    val step = ALBUM_COLUMN_STEPS.indexOf(currentColumns)
                    zoom.onPinch(
                        scale = scale,
                        centroid = centroid,
                        columns = currentColumns,
                        canZoomIn = step > 0,
                        canZoomOut = step in 0 until ALBUM_COLUMN_STEPS.lastIndex,
                        scope = scope,
                        snapshot = { gridLayer.toImageBitmap() },
                        anchorAt = ::anchorAt,
                        onSwitch = { zoomIn ->
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onColumnsChange(ALBUM_COLUMN_STEPS[if (zoomIn) step - 1 else step + 1])
                        },
                    )
                },
                onPinchEnd = { zoom.onPinchEnd(scope) },
            ),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(ALBUM_GAP),
            verticalArrangement = Arrangement.spacedBy(ALBUM_GAP),
            contentPadding = PaddingValues(start = ALBUM_EDGE, end = ALBUM_EDGE, top = ALBUM_TOP, bottom = contentBottomPadding + 8.dp),
            modifier = Modifier
                .fillMaxSize()
                .zoomLayer(zoom)
                // Records the grid as it draws, so a switch can snapshot the old layout.
                .drawWithContent {
                    gridLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(gridLayer)
                },
        ) {
            items(list, key = { it.album.bucketId }) { entry ->
                val id = entry.album.bucketId
                AlbumCard(
                    entry = entry,
                    cellPx = cardWidth.toInt(),
                    selecting = selection.isSelecting,
                    selected = id in selection.ids,
                    onClick = { if (selection.isSelecting) selection.toggle(id) else onOpenAlbum(entry.album) },
                    onLongClick = { selection.toggle(id) },
                    // Cards glide when pins or sorting move them, but not when a pinch re-lays them out (the
                    // cross-fade handles that; sliding cards would fight it).
                    modifier = Modifier.animateItem(placementSpec = if (columnsChanged) null else CardPlacement),
                )
            }
        }
        ZoomOverlay(zoom)
    }
}

/** "Albums" title with a menu: sort options and Edit albums. */
@Composable
private fun AlbumsHeader(
    icons: @Composable RowScope.() -> Unit,
    reach: ReachState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReachBarTitle(reach, "Albums", MaterialTheme.typography.headlineMedium, Modifier.weight(1f))
        ReachTopIcons(reach, content = icons)
    }
}

/** The Albums tab's ⋮ menu: sort options and Edit albums. */
@Composable
private fun AlbumsMenu(sort: AlbumSort, onSort: (AlbumSort) -> Unit, onEditAlbums: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Album options")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Text(
                "Sort by",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AlbumSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        menuOpen = false
                        onSort(option)
                    },
                    trailingIcon = { if (option == sort) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Edit albums") },
                onClick = {
                    menuOpen = false
                    onEditAlbums()
                },
            )
        }
    }
}

/** An album card. While selecting it shows a check badge and shrinks a little when selected. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    entry: AlbumEntry,
    cellPx: Int,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val album = entry.album
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (selected) 0.92f else 1f, GalleryMotion.snappy(), label = "albumSelectScale")
    Column(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            MediaThumbnail(
                item = album.cover,
                cellSizePx = cellPx,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                showBadge = false,
            )
            if (entry.pinned) PinBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
            if (selecting) SelectionCheck(selected, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Text(
            album.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
        Text(
            "${album.count}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun PinBadge(modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier) {
        Icon(
            OrganizeIcons.Pin,
            contentDescription = "Pinned",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(4.dp).size(16.dp),
        )
    }
}
