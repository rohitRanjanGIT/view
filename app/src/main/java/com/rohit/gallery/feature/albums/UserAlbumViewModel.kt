package com.rohit.gallery.feature.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rohit.gallery.core.data.db.UserAlbumMemberEntity
import com.rohit.gallery.core.data.prefs.AppPreferences
import com.rohit.gallery.core.data.repository.AlbumRepository
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.data.repository.UserAlbumRepository
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.domain.MediaTransfer
import com.rohit.gallery.core.model.Album
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.model.UserAlbum
import com.rohit.gallery.feature.selection.SelectionActions
import com.rohit.gallery.feature.selection.SelectionState
import com.rohit.gallery.navigation.UserAlbumRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a user album screen shows: still loading, the album, or nothing (it was deleted). */
sealed interface UserAlbumState {
    data object Loading : UserAlbumState

    data class Shown(val album: UserAlbum) : UserAlbumState

    data object Gone : UserAlbumState
}

/** One user album: its photos (only the list is the album's; the photos stay in their folders), and its actions. */
@HiltViewModel
class UserAlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: MediaRepository,
    private val prefs: AppPreferences,
    actions: MediaActions,
    transfer: MediaTransfer,
    albumRepository: AlbumRepository,
    val userAlbums: UserAlbumRepository,
) : ViewModel() {

    /** The album shown; starts as the route's, and the album strip can change it in place. */
    val currentAlbum: StateFlow<Long> = savedStateHandle.getStateFlow(KEY_ALBUM, savedStateHandle.toRoute<UserAlbumRoute>().albumId)
    private val handle = savedStateHandle

    val selection = SelectionState()

    val state: StateFlow<UserAlbumState> = combine(userAlbums.albums, currentAlbum) { all, id ->
        when (val album = all?.firstOrNull { it.id == id }) {
            null -> if (all == null) UserAlbumState.Loading else UserAlbumState.Gone
            else -> UserAlbumState.Shown(album)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserAlbumState.Loading)

    /** Every user album, in the Albums tab's order, for the album strip. */
    val albums: StateFlow<List<Album>> = userAlbums.albums
        .map { list -> list.orEmpty().map { it.asAlbum() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Oldest photos first instead of newest first (the one setting folders use too). */
    val oldestFirst: StateFlow<Boolean> = prefs.albumOldestFirst
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The album's photos in the chosen order; null while loading (so the grid never flips order). */
    val items: StateFlow<List<MediaItem>?> = combine(state, prefs.albumOldestFirst) { s, oldest ->
        when (s) {
            UserAlbumState.Loading -> null
            UserAlbumState.Gone -> emptyList()
            is UserAlbumState.Shown -> s.album.items.let { if (oldest) it.reversed() else it }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Shows album [id] here instead (no new screen, so Back still goes to the Albums tab). */
    fun switchTo(id: Long) {
        selection.clear()
        handle[KEY_ALBUM] = id
    }

    fun setOldestFirst(oldest: Boolean) {
        viewModelScope.launch { prefs.setAlbumOldestFirst(oldest) }
    }

    /** Takes the selected photos out of this album (only the list changes). Returns what Undo puts back. */
    suspend fun removeSelected(): List<UserAlbumMemberEntity> {
        val ids = selection.ids
        selection.clear()
        return userAlbums.remove(currentAlbum.value, ids)
    }

    /** Null if renamed, else why not. */
    suspend fun rename(name: String): String? = userAlbums.rename(currentAlbum.value, name)

    /** Deletes the album's list; its photos stay where they are. */
    fun delete() {
        val id = currentAlbum.value
        viewModelScope.launch { userAlbums.delete(listOf(id)) }
    }

    /** The one selected photo becomes the cover. */
    fun useSelectedAsCover() {
        val id = selection.ids.singleOrNull() ?: return
        selection.clear()
        val album = currentAlbum.value
        viewModelScope.launch { userAlbums.setCover(album, id) }
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

    private companion object {
        const val KEY_ALBUM = "user_album"
    }
}
