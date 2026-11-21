package com.rohit.gallery.feature.albums

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.Album
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.AlbumSwitcherDrawer
import com.rohit.gallery.feature.selection.MediaListScreen

/** One device folder: its photos and videos in a grid, with the full selection actions and a newest/oldest order. */
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onOpenMedia: (MediaItem, bucketId: Long, oldestFirst: Boolean) -> Unit,
    onAddPhotos: (bucketId: Long, name: String) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val oldestFirst by viewModel.oldestFirst.collectAsStateWithLifecycle()
    val bucketId by viewModel.currentBucket.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    // Delete album: everything in it goes to Trash, then back to Albums (the album is gone).
    val deleter = rememberAlbumDeleter(onDeleted = onBack)
    // Remove album: the album goes, its photos stay (moved back where they came from); then back to Albums.
    val remover = rememberAlbumRemover(onRemoved = onBack)
    val title = items?.firstOrNull()?.bucketName?.ifBlank { null } ?: "Album"
    // Swipe right goes back (iOS-style) unless the album strip is out: then it closes the strip.
    var stripOpen by remember { mutableStateOf(false) }
    MediaListScreen(
        title = title,
        emptyText = "This album is empty",
        items = items,
        selection = viewModel.selection,
        selectionActions = viewModel.selectionActions,
        onBack = onBack,
        onOpenMedia = { onOpenMedia(it, bucketId, oldestFirst) },
        titleActions = {
            // Move photos from anywhere in the library into this album (a real move of the files).
            // Only once the album is loaded, so the picker knows the album's real name.
            IconButton(onClick = { onAddPhotos(bucketId, title) }, enabled = !items.isNullOrEmpty()) {
                Icon(Icons.Filled.Add, contentDescription = "Move photos here")
            }
            OrderMenu(
                oldestFirst = oldestFirst,
                onChange = viewModel::setOldestFirst,
                onDelete = { deleter.delete(items.orEmpty(), albums = 1) },
                onRemove = {
                    val list = items.orEmpty()
                    list.firstOrNull()?.let { cover -> remover.remove(listOf(Album(bucketId, title, list.size, cover))) }
                },
            )
        },
        contentKey = bucketId,
        swipeBack = !stripOpen,
        // Swipe left on the grid: a strip of albums slides in from the right, below the title bar.
        gridWrapper = { grid ->
            AlbumSwitcherDrawer(
                albums = albums,
                currentId = bucketId,
                enabled = !viewModel.selection.isSelecting,
                onPick = { viewModel.switchTo(it.bucketId) },
                onOpenChange = { stripOpen = it },
                content = grid,
            )
        },
    )
}

@Composable
private fun OrderMenu(oldestFirst: Boolean, onChange: (Boolean) -> Unit, onDelete: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Album options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Show",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            listOf(false to "Newest first", true to "Oldest first").forEach { (oldest, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        onChange(oldest)
                    },
                    trailingIcon = { if (oldest == oldestFirst) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Remove album (keep photos)") },
                onClick = {
                    open = false
                    onRemove()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete album") },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}
