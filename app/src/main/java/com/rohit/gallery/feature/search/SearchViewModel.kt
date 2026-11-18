package com.rohit.gallery.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.prefs.AppPreferences
import com.rohit.gallery.core.data.repository.AlbumRepository
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.domain.MediaTransfer
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.SelectionActions
import com.rohit.gallery.feature.selection.SelectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Loading : SearchUiState

    /** Nothing typed yet: ready-made searches and the user's recent ones. */
    data class Start(val suggestions: SearchSuggestions, val recent: List<String>) : SearchUiState

    /** Matches for [query], newest first (may be empty). */
    data class Results(val query: String, val items: List<MediaItem>) : SearchUiState
}

/** Search over the in-memory library (hidden albums are already left out of it). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val prefs: AppPreferences,
    actions: MediaActions,
    transfer: MediaTransfer,
    albumRepository: AlbumRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val selection = SelectionState()

    /** Rebuilt off the main thread whenever the library changes. */
    private val index: StateFlow<SearchIndex?> = repository.state
        .map { state -> (state as? MediaIndexState.Ready)?.let { SearchIndex(it.items, ZoneId.systemDefault()) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val suggestions = index
        .map { it?.suggestions() }
        .flowOn(Dispatchers.Default)

    /** A new keystroke cancels the search still running for the previous one. */
    private val results = combine(index, query) { idx, q -> idx to q }
        .mapLatest { (idx, q) ->
            if (idx == null || q.isBlank()) null else SearchUiState.Results(q, idx.search(parseSearchNow(q)))
        }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<SearchUiState> = combine(results, suggestions, prefs.recentSearches) { res, sugg, recent ->
        when {
            res != null -> res
            sugg != null -> SearchUiState.Start(sugg, recent)
            else -> SearchUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Loading)

    fun setQuery(text: String) {
        query.value = text
    }

    /** Keeps [text] in the recent searches (called when the user commits to a search). */
    fun saveRecent(text: String) {
        viewModelScope.launch { prefs.addRecentSearch(text) }
    }

    fun clearRecent() {
        viewModelScope.launch { prefs.clearRecentSearches() }
    }

    val selectionActions = SelectionActions(
        scope = viewModelScope,
        selection = selection,
        allItems = { (uiState.value as? SearchUiState.Results)?.items.orEmpty() },
        actions = actions,
        transfer = transfer,
        repository = repository,
        albumRepository = albumRepository,
    )
}
