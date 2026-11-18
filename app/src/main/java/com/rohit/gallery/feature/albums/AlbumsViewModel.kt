package com.rohit.gallery.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.repository.AlbumRepository
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.model.AlbumSort
import com.rohit.gallery.core.model.OrganizedAlbums
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Albums tab and the Edit albums screen: albums in the user's order, and the actions that change it. */
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val albums: AlbumRepository,
) : ViewModel() {

    /** Albums in display order (hidden ones flagged); null while the index is loading. */
    val organized: StateFlow<OrganizedAlbums?> = albums.organized
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() = repository.refresh()

    fun setSort(sort: AlbumSort) = launch { albums.setSort(sort) }

    fun setPinned(bucketIds: List<Long>, pinned: Boolean) = launch { albums.setPinned(bucketIds, pinned) }

    fun setHidden(bucketIds: List<Long>, hidden: Boolean) = launch { albums.setHidden(bucketIds, hidden) }

    fun saveCustomOrder(bucketIds: List<Long>) = launch { albums.saveCustomOrder(bucketIds) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
