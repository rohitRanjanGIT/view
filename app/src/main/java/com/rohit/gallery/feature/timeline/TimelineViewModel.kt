package com.rohit.gallery.feature.timeline

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.data.repository.AlbumRepository
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.domain.MediaTransfer
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.SelectionState
import com.rohit.gallery.feature.selection.SelectionActions
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

    val selectionActions = SelectionActions(
        scope = viewModelScope,
        selection = selection,
        allItems = { (uiState.value as? TimelineUiState.Content)?.items.orEmpty() },
        actions = actions,
        transfer = transfer,
        repository = repository,
        albumRepository = albumRepository,
    )

    fun refresh() = repository.refresh()

    private companion object {
        const val DEFAULT_COLUMNS = 4
    }
}

