package com.rohit.gallery.feature.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.MediaListScreen

/** Everything marked as a favorite, with the usual selection actions. */
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    MediaListScreen(
        title = "Favourites",
        emptyText = "No favourites yet",
        items = items,
        selection = viewModel.selection,
        selectionActions = viewModel.selectionActions,
        onBack = onBack,
        onOpenMedia = onOpenMedia,
        swipeBack = true,
    )
}
