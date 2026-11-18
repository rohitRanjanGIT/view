package com.rohit.gallery.feature.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rohit.gallery.core.data.prefs.AppPreferences
import com.rohit.gallery.core.data.repository.AlbumRepository
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.domain.MediaTransfer
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.SelectionActions
import com.rohit.gallery.feature.selection.SelectionState
import com.rohit.gallery.navigation.AlbumDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MediaRepository,
    private val prefs: AppPreferences,
    actions: MediaActions,
    transfer: MediaTransfer,
    albumRepository: AlbumRepository,
) : ViewModel() {

    val bucketId: Long = savedStateHandle.toRoute<AlbumDetailRoute>().bucketId

    val selection = SelectionState()

    /** Oldest photos first instead of newest first (one setting for every album). */
    val oldestFirst: StateFlow<Boolean> = prefs.albumOldestFirst
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * The album's items in the chosen order; null while the index or the order setting is loading, so the
     * grid never shows newest first and then flips.
     */
    val items: StateFlow<List<MediaItem>?> = combine(repository.state, prefs.albumOldestFirst) { state, oldest ->
        when (state) {
            MediaIndexState.Loading -> null
            is MediaIndexState.Ready -> state.items.filter { it.bucketId == bucketId }
                .let { if (oldest) it.reversed() else it }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setOldestFirst(oldest: Boolean) {
        viewModelScope.launch { prefs.setAlbumOldestFirst(oldest) }
    }

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
