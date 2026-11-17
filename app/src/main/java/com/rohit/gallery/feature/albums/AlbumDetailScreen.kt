package com.rohit.gallery.feature.albums

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.MediaListScreen

/** One album: its photos and videos in a grid, with the full selection actions. */
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onOpenMedia: (MediaItem, bucketId: Long) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    MediaListScreen(
        title = items?.firstOrNull()?.bucketName?.ifBlank { null } ?: "Album",
        emptyText = "This album is empty",
        items = items,
        selection = viewModel.selection,
        selectionActions = viewModel.selectionActions,
        onBack = onBack,
        onOpenMedia = { onOpenMedia(it, viewModel.bucketId) },
    )
}
