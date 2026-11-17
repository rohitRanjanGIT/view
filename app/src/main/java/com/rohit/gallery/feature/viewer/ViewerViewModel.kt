package com.rohit.gallery.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rohit.gallery.core.data.mediastore.MediaDetails
import com.rohit.gallery.core.data.mediastore.MediaInfoLoader
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.navigation.ViewerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: MediaRepository,
    private val infoLoader: MediaInfoLoader,
    val playbackPositions: PlaybackPositionStore,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ViewerRoute>()

    val startId: Long = route.mediaId

    /** Items the pager swipes through, newest first; null until the index is ready. */
    val items: StateFlow<List<MediaItem>?> = repository.state
        .map { state ->
            when (state) {
                MediaIndexState.Loading -> null
                is MediaIndexState.Ready ->
                    route.bucketId?.let { bucket -> state.items.filter { it.bucketId == bucket } } ?: state.items
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun loadDetails(item: MediaItem): MediaDetails = infoLoader.load(item)
}
