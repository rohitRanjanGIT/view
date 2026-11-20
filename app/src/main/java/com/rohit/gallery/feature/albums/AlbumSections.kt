package com.rohit.gallery.feature.albums

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.model.Album
import com.rohit.gallery.core.model.AlbumEntry
import com.rohit.gallery.core.ui.components.AlbumCover
import com.rohit.gallery.core.ui.components.GridGeometry
import com.rohit.gallery.core.ui.components.GridZoomState
import com.rohit.gallery.core.ui.components.KeepGridZoomAnchor
import com.rohit.gallery.core.ui.components.ZoomAnchorCell
import com.rohit.gallery.core.ui.components.ZoomOverlay
import com.rohit.gallery.core.ui.components.pinchToZoom
import com.rohit.gallery.core.ui.components.zoomLayer
import com.rohit.gallery.core.ui.theme.GalleryMotion
import com.rohit.gallery.feature.selection.SelectionCheck
import kotlin.math.abs
import kotlin.math.roundToInt

/** The two kinds of album on the Albums tab. */
enum class AlbumSection {
    /** The phone's real folders (MediaStore buckets). */
    Device,

    /** Albums the user made: lists in the app's database (see UserAlbumRepository). */
    User,
}

/** Albums per row a pinch steps through (in an expanded section). */
private val SECTION_COLUMN_STEPS = listOf(2, 3, 4)
private val EDGE = 16.dp
private val TOP = 8.dp
private val GAP = 12.dp
private val CoverCardShape = RoundedCornerShape(20.dp)

/** Share of the screen width a cover card in a collapsed row takes (three and a sliver show, hinting the row scrolls). */
private const val COVER_CARD_FRACTION = 0.28f
private const val CELL = "cell"
private const val SECTION_MS = 260
private val CardPlacement = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)

/**
 * The Albums tab's page: "On this device" (folders) and "Albums" (the user's own), each a header with View all and a
 * sideways row of big cover cards. View all opens that section in place into the usual album grid (pinch changes how
 * many per row), and closes the other one; Show less folds it back. [footer] (Collections) comes last.
 *
 * One lazy grid holds everything, so the page scrolls as one and an opened section lazily lays out only what shows.
 * Selecting works in one section at a time: a long press in the other section starts over there.
 */
@Composable
internal fun SectionedAlbums(
    device: List<AlbumEntry>,
    user: List<AlbumEntry>,
    expanded: AlbumSection?,
    onExpand: (AlbumSection?) -> Unit,
    columns: Int,
    deviceSelection: AlbumSelection,
    userSelection: AlbumSelection,
    contentBottomPadding: Dp,
    onOpenDevice: (Album) -> Unit,
    onOpenUser: (albumId: Long) -> Unit,
    onNewAlbum: () -> Unit,
    onColumnsChange: (Int) -> Unit,
    footer: @Composable () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val widthPx = LocalWindowInfo.current.containerSize.width
    val edgePx = with(density) { EDGE.toPx() }
    val gapPx = with(density) { GAP.toPx() }
    val topPx = with(density) { TOP.toPx() }
    val cardWidth = (widthPx - 2 * edgePx - gapPx * (columns - 1)) / columns
    val coverWidthPx = (widthPx * COVER_CARD_FRACTION).roundToInt()
    val selecting = deviceSelection.isSelecting || userSelection.isSelecting

    fun selectionOf(section: AlbumSection) = if (section == AlbumSection.Device) deviceSelection else userSelection

    // One section selects at a time: a long press over in the other starts afresh there.
    fun toggle(section: AlbumSection, id: Long) {
        (if (section == AlbumSection.Device) userSelection else deviceSelection).clear()
        selectionOf(section).toggle(id)
    }

    fun click(section: AlbumSection, entry: AlbumEntry) {
        when {
            selecting -> toggle(section, entry.album.bucketId)
            section == AlbumSection.Device -> onOpenDevice(entry.album)
            else -> onOpenUser(entry.album.bucketId)
        }
    }

    // The open section's albums, and how many full-width items (headers, the other section's row) come before them.
    val open = when (expanded) {
        AlbumSection.Device -> device
        AlbumSection.User -> user
        null -> emptyList()
    }
    val leadingKeys = when (expanded) {
        AlbumSection.Device -> listOf(HEADER_DEVICE)
        AlbumSection.User -> listOf(HEADER_DEVICE, ROW_DEVICE, HEADER_USER)
        null -> emptyList()
    }
    val currentOpen by rememberUpdatedState(open)
    val currentColumns by rememberUpdatedState(columns)
    val currentLeading by rememberUpdatedState(leadingKeys.size)
    val indexById = remember(open) { open.withIndex().associate { (i, e) -> e.album.bucketId to i } }

    // Heights of the full-width items, measured as they lay out, so a pinch can place rows without measuring them.
    val heights = remember { HashMap<String, Int>() }
    // Height of the name and count under each picture in the grid, measured from a laid-out cell (follows font scale).
    val labelPx = remember { object { var value = with(density) { 48.dp.toPx() } } }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.contentType == CELL }?.size }
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
            leadingItems = leadingKeys.size,
            leadingHeight = leadingKeys.sumOf { (heights[it] ?: 0) + gapPx.toDouble() }.toFloat(),
        ),
        indexOf = { indexById[it] },
    )

    /** The album cell nearest to [position] in the open section, and its picture's centre in the grid's coordinates. */
    fun anchorAt(position: Offset): ZoomAnchorCell? {
        val info = gridState.layoutInfo
        val cell = info.visibleItemsInfo.filter { it.contentType == CELL }.minByOrNull {
            val centreY = it.offset.y - info.viewportStartOffset + it.size.width / 2f
            abs(centreY - position.y) * 4 + abs(it.offset.x + it.size.width / 2f - position.x)
        } ?: return null
        val index = cell.index - currentLeading
        val entry = currentOpen.getOrNull(index) ?: return null
        val centre = Offset(
            x = edgePx + (index % currentColumns) * (cardWidth + gapPx) + cardWidth / 2,
            y = cell.offset.y - info.viewportStartOffset + cardWidth / 2,
        )
        return ZoomAnchorCell(entry.album.bucketId, centre)
    }

    // Sections open and close with a cross-fade while what follows glides to its new place.
    val fadeIn = tween<Float>(SECTION_MS)
    val fadeOut = tween<Float>(SECTION_MS / 2)
    fun Modifier.measured(key: String) = onSizeChanged { heights[key] = it.height }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pinchToZoom(
                onPinch = { scale, centroid ->
                    // Only an open section is a grid to re-lay out; the rows have nothing to pinch.
                    if (expanded == null) return@pinchToZoom false
                    val step = SECTION_COLUMN_STEPS.indexOf(currentColumns)
                    zoom.onPinch(
                        scale = scale,
                        centroid = centroid,
                        columns = currentColumns,
                        canZoomIn = step > 0,
                        canZoomOut = step in 0 until SECTION_COLUMN_STEPS.lastIndex,
                        scope = scope,
                        snapshot = { gridLayer.toImageBitmap() },
                        anchorAt = ::anchorAt,
                        onSwitch = { zoomIn ->
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onColumnsChange(SECTION_COLUMN_STEPS[if (zoomIn) step - 1 else step + 1])
                        },
                    )
                },
                onPinchEnd = { zoom.onPinchEnd(scope) },
            ),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(GAP),
            verticalArrangement = Arrangement.spacedBy(GAP),
            contentPadding = PaddingValues(start = EDGE, end = EDGE, top = TOP, bottom = contentBottomPadding + 8.dp),
            modifier = Modifier
                .fillMaxSize()
                .zoomLayer(zoom)
                // Records the grid as it draws, so a switch can snapshot the old layout.
                .drawWithContent {
                    gridLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(gridLayer)
                },
        ) {
            for (section in AlbumSection.entries) {
                val entries = if (section == AlbumSection.Device) device else user
                val isOpen = expanded == section
                val headerKey = if (section == AlbumSection.Device) HEADER_DEVICE else HEADER_USER
                item(key = headerKey, span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    SectionHeader(
                        title = if (section == AlbumSection.Device) "On this device" else "Albums",
                        expanded = isOpen,
                        showToggle = entries.isNotEmpty(),
                        onToggle = { onExpand(if (isOpen) null else section) },
                        modifier = Modifier.measured(headerKey).animateItem(fadeIn, CardPlacement, fadeOut),
                    )
                }
                when {
                    isOpen && entries.isNotEmpty() -> items(entries, key = { "${section.name}-${it.album.bucketId}" }, contentType = { CELL }) { entry ->
                        val id = entry.album.bucketId
                        AlbumCard(
                            entry = entry,
                            cellPx = cardWidth.toInt(),
                            selecting = selecting,
                            selected = id in selectionOf(section).ids,
                            onClick = { click(section, entry) },
                            onLongClick = { toggle(section, id) },
                            // Cards glide when pins or sorting move them, but not when a pinch re-lays them out (the
                            // cross-fade handles that; sliding cards would fight it).
                            modifier = Modifier.animateItem(fadeIn, if (columnsChanged) null else CardPlacement, fadeOut),
                        )
                    }
                    else -> {
                        val rowKey = if (section == AlbumSection.Device) ROW_DEVICE else ROW_USER
                        item(key = rowKey, span = { GridItemSpan(maxLineSpan) }, contentType = "row") {
                            CoverRow(
                                section = section,
                                entries = entries,
                                cardWidthPx = coverWidthPx,
                                edgePx = edgePx.roundToInt(),
                                selecting = selecting,
                                selection = selectionOf(section),
                                onClick = ::click,
                                onLongClick = ::toggle,
                                onNewAlbum = onNewAlbum,
                                modifier = Modifier.measured(rowKey).animateItem(fadeIn, CardPlacement, fadeOut),
                            )
                        }
                    }
                }
            }
            item(key = "collections", span = { GridItemSpan(maxLineSpan) }, contentType = "collections") {
                Box(Modifier.animateItem(fadeIn, CardPlacement, fadeOut)) { footer() }
            }
        }
        ZoomOverlay(zoom)
    }
}

private const val HEADER_DEVICE = "header-device"
private const val HEADER_USER = "header-user"
private const val ROW_DEVICE = "row-device"
private const val ROW_USER = "row-user"

/** A section's title, with View all / Show less on the right. */
@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    showToggle: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showToggle) TextButton(onClick = onToggle) { Text(if (expanded) "Show less" else "View all") }
    }
}

/**
 * A closed section: its albums as big cover cards in a sideways row, edge to edge (the grid's side padding is
 * undone, so the row scrolls out under the screen edges). An empty "Albums" row offers a New album card instead.
 */
@Composable
private fun CoverRow(
    section: AlbumSection,
    entries: List<AlbumEntry>,
    cardWidthPx: Int,
    edgePx: Int,
    selecting: Boolean,
    selection: AlbumSelection,
    onClick: (AlbumSection, AlbumEntry) -> Unit,
    onLongClick: (AlbumSection, Long) -> Unit,
    onNewAlbum: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardWidth = with(LocalDensity.current) { cardWidthPx.toDp() }
    LazyRow(
        contentPadding = PaddingValues(horizontal = EDGE),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        modifier = modifier.layout { measurable, constraints ->
            // As wide as the screen, not the padded grid.
            val width = constraints.maxWidth + 2 * edgePx
            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
            layout(constraints.maxWidth, placeable.height) { placeable.place(-edgePx, 0) }
        },
    ) {
        when {
            entries.isNotEmpty() -> items(entries, key = { it.album.bucketId }) { entry ->
                val id = entry.album.bucketId
                CoverCard(
                    entry = entry,
                    widthPx = cardWidthPx,
                    selecting = selecting,
                    selected = id in selection.ids,
                    onClick = { onClick(section, entry) },
                    onLongClick = { onLongClick(section, id) },
                    modifier = Modifier.width(cardWidth),
                )
            }
            section == AlbumSection.User -> item(key = "new") { NewAlbumCard(onNewAlbum, Modifier.width(cardWidth)) }
            else -> item(key = "none") {
                Text(
                    "No photos on this device yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}

/** A big portrait cover with the album's name (and count) in white over a soft dark fade at the bottom. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverCard(
    entry: AlbumEntry,
    widthPx: Int,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val album = entry.album
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (selected) 0.92f else 1f, GalleryMotion.snappy(), label = "coverSelectScale")
    Box(
        modifier
            .aspectRatio(COVER_ASPECT)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CoverCardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        // Decoded at the card's taller side, so the crop stays sharp.
        AlbumCover(album.cover, (widthPx / COVER_ASPECT).roundToInt(), Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
            Text(
                album.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${album.count}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        }
        if (entry.pinned) PinBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        if (selecting) SelectionCheck(selected, Modifier.align(Alignment.TopStart).padding(8.dp))
    }
}

/** Width : height of a cover card. */
private const val COVER_ASPECT = 3f / 4f

/** The empty "Albums" row: a card that makes the first album. */
@Composable
private fun NewAlbumCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .aspectRatio(COVER_ASPECT)
            .clip(CoverCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(28.dp))
        Text("New album", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
        Text(
            "Collect photos from any folder",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp),
        )
    }
}
