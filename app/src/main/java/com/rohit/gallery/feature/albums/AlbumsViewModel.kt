package com.rohit.gallery.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.repository.AlbumRepository
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.model.Album
import com.rohit.gallery.core.model.buildAlbums
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {

    /** Albums, ordered by their newest item; null while the index is loading. */
    val albums: StateFlow<List<Album>?> = repository.state
        .map { state ->
            when (state) {
                MediaIndexState.Loading -> null
                is MediaIndexState.Ready -> buildAlbums(state.items)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() = repository.refresh()
}
