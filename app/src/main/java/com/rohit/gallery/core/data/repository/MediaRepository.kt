package com.rohit.gallery.core.data.repository

import androidx.compose.runtime.Immutable
import com.rohit.gallery.core.data.mediastore.MediaSource
import com.rohit.gallery.core.di.ApplicationScope
import com.rohit.gallery.core.model.MediaItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface MediaIndexState {
    data object Loading : MediaIndexState

    /** [items] is sorted newest first. */
    @Immutable
    data class Ready(val items: List<MediaItem>) : MediaIndexState
}

/**
 * Holds the whole media timeline in memory as an immutable, newest-first list.
 *
 * MediaStore stays the source of truth: [refresh] re-queries it immediately, while change
 * notifications from the source are debounced so a burst of camera writes costs one query.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Singleton
class MediaRepository @Inject constructor(
    private val source: MediaSource,
    hiddenAlbums: HiddenAlbums,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<MediaIndexState>(MediaIndexState.Loading)

    /** Everything in MediaStore, hidden albums included (for album management and the album picker). */
    val allState: StateFlow<MediaIndexState> = _state.asStateFlow()

    /**
     * What the app shows: the index without the albums the user hid. Waits for the hidden list before the
     * first Ready, so hidden photos never flash on start.
     */
    val state: StateFlow<MediaIndexState> = combine(_state, hiddenAlbums.bucketIds) { index, hidden ->
        when {
            index !is MediaIndexState.Ready || hidden.isEmpty() -> index
            else -> MediaIndexState.Ready(index.items.filterNot { it.bucketId in hidden })
        }
    }.stateIn(scope, SharingStarted.Eagerly, MediaIndexState.Loading)

    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            merge(
                refreshRequests.receiveAsFlow(),
                source.changes().debounce(OBSERVER_DEBOUNCE_MS),
            ).collectLatest { load() }
        }
    }

    /** Re-queries the source now. Call when permission changes or the app comes to the foreground. */
    fun refresh() {
        refreshRequests.trySend(Unit)
    }

    private suspend fun load() {
        try {
            val items = source.queryAll()
                .sortedWith(compareByDescending<MediaItem> { it.sortDateMs }.thenByDescending { it.id })
            _state.value = MediaIndexState.Ready(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Typically a SecurityException before permission is granted. Keep the last good
            // index; if there is none yet, show an empty timeline instead of loading forever.
            if (_state.value is MediaIndexState.Loading) {
                _state.value = MediaIndexState.Ready(emptyList())
            }
        }
    }

    companion object {
        const val OBSERVER_DEBOUNCE_MS = 500L
    }
}
