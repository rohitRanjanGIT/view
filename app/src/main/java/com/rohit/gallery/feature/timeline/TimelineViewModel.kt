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
import com.rohit.gallery.core.data.prefs.AppPreferences
import com.rohit.gallery.core.data.prefs.GridSizeKey
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val actions: MediaActions,
    private val transfer: MediaTransfer,
    albumRepository: AlbumRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    // Changed in memory at once (a pinch switches mid-gesture) and saved in the background; null until the
    // saved level is read, so the grid never opens at the default and then jumps.
    private val level = MutableStateFlow<GridLevel?>(null)

    init {
        viewModelScope.launch {
            val saved = prefs.gridSize(GridSizeKey.Pictures).first()
            level.value = GridLevel.entries.firstOrNull { it.name == saved } ?: GridLevel.Default
        }
    }

    val selection = SelectionState()

    // null while loading.
    private val library = repository.state
        .map { index ->
            when (index) {
                MediaIndexState.Loading -> null
                is MediaIndexState.Ready -> groupLibrary(index.items, ZoneId.systemDefault())
            }
        }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<TimelineUiState> = combine(library, level) { lib, lvl ->
        if (lib == null || lvl == null) TimelineUiState.Loading else lib.toUiState(lvl)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState.Loading)

    fun setLevel(newLevel: GridLevel) {
        level.value = newLevel
        viewModelScope.launch { prefs.setGridSize(GridSizeKey.Pictures, newLevel.name) }
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

