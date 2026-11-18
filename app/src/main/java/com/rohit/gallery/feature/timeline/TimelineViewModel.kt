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
import kotlinx.coroutines.flow.map
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
        val level: GridLevel,
        val items: List<MediaItem>,
        val indexById: Map<Long, Int>,
        val rowsByFirstId: Map<Long, List<MediaItem>>,
    ) : TimelineUiState {
        val columns: Int get() = level.columns
    }
}

/** The library grouped once per change; switching density only re-chunks rows. */
private class GroupedLibrary(
    val items: List<MediaItem>,
    val days: List<DayGroup>,
    val months: List<DayGroup>,
    val indexById: Map<Long, Int>,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val actions: MediaActions,
    private val transfer: MediaTransfer,
    albumRepository: AlbumRepository,
) : ViewModel() {

    // In memory for now; saved across launches once Settings / DataStore land.
    private val level = MutableStateFlow(GridLevel.Default)

    val selection = SelectionState()

    // null while loading.
    private val library = repository.state
        .map { index ->
            when (index) {
                MediaIndexState.Loading -> null
                is MediaIndexState.Ready -> {
                    val days = groupByDay(index.items, ZoneId.systemDefault())
                    GroupedLibrary(
                        items = index.items,
                        days = days,
                        months = groupByMonth(days),
                        indexById = HashMap<Long, Int>(index.items.size * 2).also { map ->
                            index.items.forEachIndexed { i, item -> map[item.id] = i }
                        },
                    )
                }
            }
        }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<TimelineUiState> = combine(library, level) { lib, lvl ->
        when {
            lib == null -> TimelineUiState.Loading
            lib.items.isEmpty() -> TimelineUiState.Empty
            else -> {
                val sections = (if (lvl.byMonth) lib.months else lib.days).toTimelineDays(lvl.columns)
                TimelineUiState.Content(
                    days = sections,
                    level = lvl,
                    items = lib.items,
                    indexById = lib.indexById,
                    rowsByFirstId = HashMap<Long, List<MediaItem>>().also { map ->
                        sections.forEach { day -> day.rows.forEach { row -> map[row.first().id] = row } }
                    },
                )
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState.Loading)

    fun setLevel(newLevel: GridLevel) {
        level.value = newLevel
    }

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
}

