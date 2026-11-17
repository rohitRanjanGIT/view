package com.rohit.gallery.feature.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.GRID_COLUMNS
import com.rohit.gallery.feature.selection.SelectableMediaGrid
import com.rohit.gallery.feature.selection.SelectionActionsBar
import com.rohit.gallery.feature.selection.SelectionTopBar

/** One album: its photos and videos in a grid, with the full selection actions. */
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onOpenMedia: (MediaItem, bucketId: Long) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selection = viewModel.selection
    BackHandler(enabled = selection.isSelecting) { selection.clear() }

    val cellPx = LocalWindowInfo.current.containerSize.width / GRID_COLUMNS
    val count = selection.ids.size
    val list = items
    val allFavorite by remember(list) {
        derivedStateOf {
            val ids = selection.ids
            ids.isNotEmpty() && list.orEmpty().filter { it.id in ids }.let { sel -> sel.isNotEmpty() && sel.all { it.isFavorite } }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!selection.isSelecting) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            list?.firstOrNull()?.bucketName?.ifBlank { null } ?: "Album",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                SelectionTopBar(
                    count = count,
                    onClose = selection::clear,
                    onSelectAll = { list?.let(selection::selectAll) },
                )
            }

            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("This album is empty", style = MaterialTheme.typography.titleMedium)
                }
                else -> SelectableMediaGrid(list, selection, cellPx, onOpenMedia = { onOpenMedia(it, viewModel.bucketId) })
            }
        }

        if (selection.isSelecting) {
            SelectionActionsBar(
                controller = viewModel.selectionActions,
                selection = selection,
                allFavorite = allFavorite,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
