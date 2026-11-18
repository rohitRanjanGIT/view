package com.rohit.gallery.feature.albums

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.MediaListScreen

/** One album: its photos and videos in a grid, with the full selection actions and a newest/oldest order. */
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onOpenMedia: (MediaItem, bucketId: Long, oldestFirst: Boolean) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val oldestFirst by viewModel.oldestFirst.collectAsStateWithLifecycle()
    MediaListScreen(
        title = items?.firstOrNull()?.bucketName?.ifBlank { null } ?: "Album",
        emptyText = "This album is empty",
        items = items,
        selection = viewModel.selection,
        selectionActions = viewModel.selectionActions,
        onBack = onBack,
        onOpenMedia = { onOpenMedia(it, viewModel.bucketId, oldestFirst) },
        titleActions = { OrderMenu(oldestFirst, viewModel::setOldestFirst) },
    )
}

@Composable
private fun OrderMenu(oldestFirst: Boolean, onChange: (Boolean) -> Unit) {
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
        }
    }
}
