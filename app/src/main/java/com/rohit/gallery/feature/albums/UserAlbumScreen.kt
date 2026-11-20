package com.rohit.gallery.feature.albums

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.model.UserAlbum
import com.rohit.gallery.core.ui.components.AlbumSwitcherDrawer
import com.rohit.gallery.core.ui.components.LocalUndoBar
import com.rohit.gallery.feature.selection.MediaListScreen
import com.rohit.gallery.feature.selection.NewAlbumDialog
import kotlinx.coroutines.launch

/**
 * One user album: its photos in the timeline grid with the usual selection actions, plus Remove (from this album
 * only). The ⋮ menu adds photos, renames, or deletes the album (never its photos).
 */
@Composable
fun UserAlbumScreen(
    onBack: () -> Unit,
    onOpenMedia: (MediaItem, albumId: Long, oldestFirst: Boolean) -> Unit,
    onAddPhotos: (albumId: Long, name: String) -> Unit,
    viewModel: UserAlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val oldestFirst by viewModel.oldestFirst.collectAsStateWithLifecycle()
    val albumId by viewModel.currentAlbum.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val album = (state as? UserAlbumState.Shown)?.album
    // Deleted (here or from another screen): nothing left to show.
    LaunchedEffect(state) { if (state == UserAlbumState.Gone) onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undoBar = LocalUndoBar.current
    var dialog by rememberSaveable { mutableStateOf<AlbumDialog?>(null) }
    var stripOpen by remember { mutableStateOf(false) }
    val title = album?.name ?: "Album"

    MediaListScreen(
        title = title,
        emptyText = "No photos here yet. Tap + to add some",
        items = items,
        selection = viewModel.selection,
        selectionActions = viewModel.selectionActions,
        onBack = onBack,
        onOpenMedia = { onOpenMedia(it, albumId, oldestFirst) },
        titleActions = {
            IconButton(onClick = { album?.let { onAddPhotos(it.id, it.name) } }, enabled = album != null) {
                Icon(Icons.Filled.Add, contentDescription = "Add photos")
            }
            UserAlbumMenu(
                oldestFirst = oldestFirst,
                onOrder = viewModel::setOldestFirst,
                onAddPhotos = { album?.let { onAddPhotos(it.id, it.name) } },
                onRename = { dialog = AlbumDialog.Rename },
                onDelete = { dialog = AlbumDialog.Delete },
            )
        },
        contentKey = albumId,
        // Swipe right goes back (iOS-style) unless the album strip is out: then it closes the strip.
        swipeBack = !stripOpen,
        gridWrapper = { grid ->
            AlbumSwitcherDrawer(
                albums = albums,
                currentId = albumId,
                enabled = !viewModel.selection.isSelecting,
                onPick = { viewModel.switchTo(it.bucketId) },
                onOpenChange = { stripOpen = it },
                content = grid,
            )
        },
        currentUserAlbum = albumId,
        onRemoveFromAlbum = {
            val repository = viewModel.userAlbums
            scope.launch {
                val removed = viewModel.removeSelected()
                if (removed.isEmpty()) return@launch
                val what = if (removed.size == 1) "1 photo" else "${removed.size} photos"
                // Only the album's list changed, so Undo puts the rows straight back.
                undoBar?.show("Removed $what from $title", undo = { repository.restore(removed) })
            }
        },
        onSetCover = viewModel::useSelectedAsCover,
    )

    when (dialog) {
        AlbumDialog.Rename -> album?.let { a ->
            NewAlbumDialog(
                existingNames = albums.filter { it.bucketId != a.id }.map { it.name },
                title = "Rename album",
                initial = a.name,
                confirmLabel = "Rename",
                folder = false,
                onDismiss = { dialog = null },
                onCreate = { name ->
                    dialog = null
                    scope.launch { viewModel.rename(name)?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
                },
            )
        }
        AlbumDialog.Delete -> album?.let { a ->
            DeleteUserAlbumsDialog(
                albums = listOf(a),
                onDismiss = { dialog = null },
                onConfirm = {
                    dialog = null
                    viewModel.delete()
                },
            )
        }
        null -> Unit
    }
}

private enum class AlbumDialog { Rename, Delete }

/** Asks before deleting user albums, and says plainly that their photos stay. */
@Composable
fun DeleteUserAlbumsDialog(albums: List<UserAlbum>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val photos = albums.sumOf { it.count }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (albums.size == 1) "Delete “${albums.first().name}”?" else "Delete ${albums.size} albums?") },
        text = {
            Text(
                when (photos) {
                    0 -> "Only the album goes. Nothing on your device changes."
                    1 -> "Only the album goes. Its photo stays on your device and in Pictures."
                    else -> "Only the album goes. Its $photos photos stay on your device and in Pictures."
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun UserAlbumMenu(
    oldestFirst: Boolean,
    onOrder: (Boolean) -> Unit,
    onAddPhotos: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    fun pick(action: () -> Unit) {
        open = false
        action()
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Album options") }
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
                    onClick = { pick { onOrder(oldest) } },
                    trailingIcon = { if (oldest == oldestFirst) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Add photos") }, onClick = { pick(onAddPhotos) })
            DropdownMenuItem(text = { Text("Rename") }, onClick = { pick(onRename) })
            DropdownMenuItem(text = { Text("Delete album") }, onClick = { pick(onDelete) })
        }
    }
}
