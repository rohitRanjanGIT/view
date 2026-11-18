package com.rohit.gallery.feature.albums

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
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
import com.rohit.gallery.core.ui.components.MediaThumbnail
import com.rohit.gallery.core.ui.components.OrganizeIcons
import com.rohit.gallery.core.ui.theme.GalleryMotion
import com.rohit.gallery.feature.selection.SelectionCheck
import com.rohit.gallery.feature.selection.SelectionTopBar

private const val ALBUM_COLUMNS = 2
private val ALBUM_GAP = 12.dp

/**
 * The Albums tab: pinned albums first (in pin order), then the rest in the chosen sort; hidden ones are left
 * out. Long-press selects albums for a bulk pin / unpin or hide.
 */
@Composable
fun AlbumsScreen(
    onOpenAlbum: (Album) -> Unit,
    onEditAlbums: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    MediaAccessGate(modifier.fillMaxSize()) {
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
        val organized by viewModel.organized.collectAsStateWithLifecycle()
        val current = organized
        val context = LocalContext.current
        val selection = rememberAlbumSelection()

        // The nav host hides its bottom bar while selecting; this screen shows its own action bar instead.
        LaunchedEffect(selection.isSelecting) { onSelectionModeChange(selection.isSelecting) }
        DisposableEffect(Unit) { onDispose { onSelectionModeChange(false) } }
        BackHandler(enabled = selection.isSelecting) { selection.clear() }

        val list = current?.visible
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (selection.isSelecting) {
                    SelectionTopBar(
                        count = selection.ids.size,
                        onClose = selection::clear,
                        onSelectAll = { list?.let(selection::selectAll) },
                    )
                } else {
                    AlbumsHeader(
                        sort = current?.sort ?: AlbumSort.Newest,
                        onSort = viewModel::setSort,
                        onEditAlbums = onEditAlbums,
                        modifier = Modifier.statusBarsPadding(),
                    )
                }
                when {
                    list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("No albums yet", style = MaterialTheme.typography.titleMedium)
                    }
                    else -> {
                        val cellPx = (LocalWindowInfo.current.containerSize.width / ALBUM_COLUMNS)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(ALBUM_COLUMNS),
                            horizontalArrangement = Arrangement.spacedBy(ALBUM_GAP),
                            verticalArrangement = Arrangement.spacedBy(ALBUM_GAP),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentBottomPadding + 8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(list, key = { it.album.bucketId }) { entry ->
                                val id = entry.album.bucketId
                                AlbumCard(
                                    entry = entry,
                                    cellPx = cellPx,
                                    selecting = selection.isSelecting,
                                    selected = id in selection.ids,
                                    onClick = { if (selection.isSelecting) selection.toggle(id) else onOpenAlbum(entry.album) },
                                    onLongClick = { selection.toggle(id) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
            if (selection.isSelecting && list != null) {
                AlbumSelectionBar(
                    entries = list.filter { it.album.bucketId in selection.ids },
                    onPin = { ids, pinned ->
                        viewModel.setPinned(ids, pinned)
                        selection.clear()
                    },
                    onHide = { ids, hidden ->
                        viewModel.setHidden(ids, hidden)
                        selection.clear()
                        if (hidden) {
                            val what = if (ids.size == 1) "Album hidden" else "${ids.size} albums hidden"
                            Toast.makeText(context, "$what. Show them again in Edit albums.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/** "Albums" title with a menu: sort options and Edit albums. */
@Composable
private fun AlbumsHeader(sort: AlbumSort, onSort: (AlbumSort) -> Unit, onEditAlbums: () -> Unit, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Albums", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
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
