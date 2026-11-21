package com.rohit.gallery.feature.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.repository.AlbumRepository
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.domain.MediaTransfer
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.MediaListScreen
import com.rohit.gallery.feature.selection.SelectionActions
import com.rohit.gallery.feature.selection.SelectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MotionViewModel @Inject constructor(
    repository: MediaRepository,
    actions: MediaActions,
    transfer: MediaTransfer,
    albumRepository: AlbumRepository,
) : ViewModel() {

    val selection = SelectionState()

    /** Every GIF, animated WebP and motion photo, newest first; null while the index is loading. */
    val items: StateFlow<List<MediaItem>?> = repository.state
        .map { state ->
            when (state) {
                MediaIndexState.Loading -> null
                is MediaIndexState.Ready -> state.items.filter { it.isMotionOrGif }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectionActions = SelectionActions(
        scope = viewModelScope,
        selection = selection,
        allItems = { items.value.orEmpty() },
        actions = actions,
        transfer = transfer,
        repository = repository,
        albumRepository = albumRepository,
    )
}

/** All GIFs and motion photos (Albums > Collections), with the usual selection actions. */
@Composable
fun MotionScreen(
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    viewModel: MotionViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    MediaListScreen(
        title = "Motion and GIFs",
        emptyText = "No GIFs or motion photos yet",
        items = items,
        selection = viewModel.selection,
        selectionActions = viewModel.selectionActions,
        onBack = onBack,
        onOpenMedia = onOpenMedia,
        swipeBack = true,
    )
}
