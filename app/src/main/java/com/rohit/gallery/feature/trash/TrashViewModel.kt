package com.rohit.gallery.feature.trash

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.mediastore.MediaStoreDataSource
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.SelectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val source: MediaStoreDataSource,
    private val repository: MediaRepository,
    private val actions: MediaActions,
) : ViewModel() {

    /** Null until the first load finishes. */
    private val _items = MutableStateFlow<List<MediaItem>?>(null)
    val items: StateFlow<List<MediaItem>?> = _items

    val selection = SelectionState()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _items.value = runCatching { source.queryTrashed() }.getOrDefault(emptyList())
            // Drop selected ids that no longer exist.
            val present = _items.value.orEmpty().mapTo(HashSet()) { it.id }
            selection.retainOnly(present)
        }
    }

    /** Called after a restore or delete went through; the main timeline changes too. */
    fun onChanged() {
        selection.clear()
        repository.refresh()
        refresh()
    }

    private fun selected(): List<MediaItem> {
        val ids = selection.ids
        return _items.value.orEmpty().filter { it.id in ids }
    }

    suspend fun restoreRequest(): IntentSender? =
        selected().takeIf { it.isNotEmpty() }?.let { actions.restoreRequest(it) }

    suspend fun deleteRequest(): IntentSender? =
        selected().takeIf { it.isNotEmpty() }?.let { actions.deleteRequest(it) }
}
