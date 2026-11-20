package com.rohit.gallery.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.repository.AlbumRepository
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.data.repository.UserAlbumRepository
import com.rohit.gallery.core.model.UserAlbum
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.data.prefs.AppPreferences
import com.rohit.gallery.core.model.AlbumSort
import com.rohit.gallery.core.model.OrganizedAlbums
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Albums tab and the Edit albums screen: folders and user albums in the user's order, and what changes it. */
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val albums: AlbumRepository,
    private val prefs: AppPreferences,
    private val userAlbumRepository: UserAlbumRepository,
) : ViewModel() {

    /** Albums in display order; null while the index is loading. */
    val organized: StateFlow<OrganizedAlbums?> = albums.organized
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The user's own albums (pinned first, then the same sort); null while the index is loading. */
    val userAlbums: StateFlow<List<UserAlbum>?> = userAlbumRepository.albums

    /** Albums per row; null until read from storage (so the grid never jumps from a default). */
    val columns: StateFlow<Int?> = prefs.albumColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setColumns(columns: Int) = launch { prefs.setAlbumColumns(columns) }

    fun refresh() = repository.refresh()

    fun setSort(sort: AlbumSort) = launch { albums.setSort(sort) }

    fun setPinned(bucketIds: List<Long>, pinned: Boolean) = launch { albums.setPinned(bucketIds, pinned) }

    /** Every photo and video in [bucketIds], to lock whole albums. */
    fun itemsIn(bucketIds: List<Long>): List<MediaItem> {
        val ids = bucketIds.toSet()
        return (repository.state.value as? MediaIndexState.Ready)?.items.orEmpty().filter { it.bucketId in ids }
    }

    fun saveCustomOrder(bucketIds: List<Long>) = launch { albums.saveCustomOrder(bucketIds) }

    fun setUserPinned(albumIds: List<Long>, pinned: Boolean) = launch { userAlbumRepository.setPinned(albumIds, pinned) }

    /** Saves the user albums' custom order (the unpinned ones, as dragged) and switches the sort to Custom. */
    fun saveUserOrder(albumIds: List<Long>) = launch {
        userAlbumRepository.saveCustomOrder(albumIds)
        albums.setSort(AlbumSort.Custom)
    }

    /** Deletes the albums' lists only: every photo stays where it is. */
    fun deleteUserAlbums(albumIds: List<Long>) = launch { userAlbumRepository.delete(albumIds) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
