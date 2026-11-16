package com.rohit.gallery.feature.timeline

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
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

    @Immutable
    data class Content(val days: List<TimelineDay>, val columns: Int) : TimelineUiState
}

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {

    // Fixed for now; pinch-to-change density arrives in Phase 3.
    private val columns = MutableStateFlow(DEFAULT_COLUMNS)

    val uiState: StateFlow<TimelineUiState> = combine(repository.state, columns) { index, cols ->
        when (index) {
            MediaIndexState.Loading -> TimelineUiState.Loading
            is MediaIndexState.Ready ->
                if (index.items.isEmpty()) {
                    TimelineUiState.Empty
                } else {
                    TimelineUiState.Content(
                        days = groupByDay(index.items, ZoneId.systemDefault()).toTimelineDays(cols),
                        columns = cols,
                    )
                }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState.Loading)

    fun refresh() = repository.refresh()

    private companion object {
        const val DEFAULT_COLUMNS = 4
    }
}
