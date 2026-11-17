package com.rohit.gallery.feature.viewer

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rohit.gallery.core.data.mediastore.MediaDetails
import com.rohit.gallery.core.data.mediastore.MediaInfoLoader
import com.rohit.gallery.core.data.mediastore.MediaStoreDataSource
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.navigation.ViewerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MediaRepository,
    private val source: MediaStoreDataSource,
    private val actions: MediaActions,
    private val infoLoader: MediaInfoLoader,
    val playbackPositions: PlaybackPositionStore,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ViewerRoute>()

    val startId: Long = route.mediaId

    /** True when paging through the recycle bin: the viewer then offers Restore and Delete. */
    val isTrash: Boolean = route.trashed

    private val trashItems = MutableStateFlow<List<MediaItem>?>(null)

    /** Items the pager swipes through, newest first; null until the index is ready. */
    val items: StateFlow<List<MediaItem>?> = if (route.trashed) {
        trashItems
    } else {
        repository.state
            .map { state ->
                when (state) {
                    MediaIndexState.Loading -> null
                    is MediaIndexState.Ready ->
                        route.bucketId?.let { bucket -> state.items.filter { it.bucketId == bucket } } ?: state.items
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    init {
        if (route.trashed) reloadTrash()
    }

    private fun reloadTrash() {
        viewModelScope.launch { trashItems.value = runCatching { source.queryTrashed() }.getOrDefault(emptyList()) }
    }

    /** After a restore or permanent delete went through. */
    fun onTrashChanged() {
        repository.refresh()
        reloadTrash()
    }

    suspend fun restoreRequest(item: MediaItem): IntentSender? =
        runCatching { actions.restoreRequest(listOf(item)) }.getOrNull()

    suspend fun deleteRequest(item: MediaItem): IntentSender? =
        runCatching { actions.deleteRequest(listOf(item)) }.getOrNull()

    suspend fun loadDetails(item: MediaItem): MediaDetails = infoLoader.load(item)
}
