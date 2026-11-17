package com.rohit.gallery.feature.timeline

import android.content.Intent
import android.content.IntentSender
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.data.repository.AlbumRepository
import com.rohit.gallery.core.domain.Destination
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.domain.MediaTransfer
import com.rohit.gallery.core.domain.TransferResult
import com.rohit.gallery.core.model.Album
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.SelectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

sealed interface TimelineUiState {
    data object Loading : TimelineUiState
    data object Empty : TimelineUiState

    /**
     * [items] is the whole timeline flattened, newest first. [indexById] maps an id to its position
     * in [items] (for range selection) and [rowsByFirstId] maps the first id of a grid row to that
     * row (for finding the cell under a finger).
     */
    @Immutable
    data class Content(
        val days: List<TimelineDay>,
        val columns: Int,
        val items: List<MediaItem>,
        val indexById: Map<Long, Int>,
        val rowsByFirstId: Map<Long, List<MediaItem>>,
    ) : TimelineUiState
}

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val actions: MediaActions,
    private val transfer: MediaTransfer,
    albumRepository: AlbumRepository,
) : ViewModel() {

    // Fixed for now; pinch-to-change density arrives in Phase 3.
    private val columns = MutableStateFlow(DEFAULT_COLUMNS)

    val selection = SelectionState()

    val uiState: StateFlow<TimelineUiState> = combine(repository.state, columns) { index, cols ->
        when (index) {
            MediaIndexState.Loading -> TimelineUiState.Loading
            is MediaIndexState.Ready ->
                if (index.items.isEmpty()) {
                    TimelineUiState.Empty
                } else {
                    val days = groupByDay(index.items, ZoneId.systemDefault()).toTimelineDays(cols)
                    TimelineUiState.Content(
                        days = days,
                        columns = cols,
                        items = index.items,
                        indexById = HashMap<Long, Int>(index.items.size * 2).also { map ->
                            index.items.forEachIndexed { i, item -> map[item.id] = i }
                        },
                        rowsByFirstId = HashMap<Long, List<MediaItem>>().also { map ->
                            days.forEach { day -> day.rows.forEach { row -> map[row.first().id] = row } }
                        },
                    )
                }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState.Loading)

    fun refresh() = repository.refresh()

    /** The selected items that still exist in the index. */
    fun selectedItems(): List<MediaItem> {
        val content = uiState.value as? TimelineUiState.Content ?: return emptyList()
        return selection.ids.mapNotNull { id -> content.indexById[id]?.let { content.items[it] } }
    }

    fun shareIntent(): Intent? = selectedItems().takeIf { it.isNotEmpty() }?.let(actions::shareIntent)

    suspend fun trashRequest(): IntentSender? =
        selectedItems().takeIf { it.isNotEmpty() }?.let { actions.trashRequest(it) }

    /** Favorites everything selected, or removes the mark if all of it is already a favorite. */
    suspend fun favoriteRequest(): IntentSender? {
        val items = selectedItems().takeIf { it.isNotEmpty() } ?: return null
        return actions.favoriteRequest(items, favorite = !items.all { it.isFavorite })
    }

    /** Albums to offer as a move/copy target. */
    val albums: StateFlow<List<Album>> = albumRepository.albums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)

    /** True while a copy or move is running (copying big videos takes a while). */
    val busy: StateFlow<Boolean> = _busy

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** One-off messages for a toast (results of move/copy). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var pendingMove: Pair<List<MediaItem>, Destination>? = null

    /** The target folder for an existing [album] or a new album called [newName]. Null if it can't be found. */
    private suspend fun destination(album: Album?, newName: String?): Destination? =
        when {
            album != null -> transfer.destinationOf(album.cover)
            newName != null -> transfer.newAlbum(newName)
            else -> null
        } ?: run {
            _messages.tryEmit("Couldn't find that album's folder")
            null
        }

    /** Starts a move: returns the system write confirmation, then [finishMove] does the work once it is accepted. */
    suspend fun moveRequest(album: Album?, newName: String?): IntentSender? {
        val items = selectedItems().takeIf { it.isNotEmpty() } ?: return null
        val dest = destination(album, newName) ?: return null
        pendingMove = items to dest
        return runCatching { transfer.writeRequest(items) }.getOrNull()
    }

    fun finishMove() {
        val (items, dest) = pendingMove ?: return
        pendingMove = null
        runTransfer("Moved", items) { transfer.move(items, dest) }
    }

    fun copyTo(album: Album?, newName: String?) {
        val items = selectedItems().takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            val dest = destination(album, newName) ?: return@launch
            runTransfer("Copied", items) { transfer.copy(items, dest) }
        }
    }

    private fun runTransfer(verb: String, items: List<MediaItem>, work: suspend () -> TransferResult) {
        viewModelScope.launch {
            _busy.value = true
            val result = try {
                work()
            } finally {
                _busy.value = false
            }
            selection.clear()
            repository.refresh()
            _messages.tryEmit(
                when {
                    result.failed == 0 -> "$verb ${items.size} ${if (items.size == 1) "item" else "items"}"
                    result.done == 0 -> "Couldn't complete that"
                    else -> "$verb ${result.done}, ${result.failed} failed"
                },
            )
        }
    }

    private companion object {
        const val DEFAULT_COLUMNS = 4
    }
}

enum class TransferKind { Move, Copy }
