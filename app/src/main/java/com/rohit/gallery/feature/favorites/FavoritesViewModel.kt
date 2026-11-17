package com.rohit.gallery.feature.favorites

import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.SelectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val actions: MediaActions,
) : ViewModel() {

    val selection = SelectionState()

    /** Favorite items, newest first; null while the index is loading. */
    val items: StateFlow<List<MediaItem>?> = repository.state
        .map { state ->
            when (state) {
                MediaIndexState.Loading -> null
                is MediaIndexState.Ready -> state.items.filter { it.isFavorite }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun selected(): List<MediaItem> {
        val ids = selection.ids
        return items.value.orEmpty().filter { it.id in ids }
    }

    fun shareIntent(): Intent? = selected().takeIf { it.isNotEmpty() }?.let(actions::shareIntent)

    suspend fun unfavoriteRequest(): IntentSender? =
        selected().takeIf { it.isNotEmpty() }?.let { actions.favoriteRequest(it, favorite = false) }

    suspend fun trashRequest(): IntentSender? =
        selected().takeIf { it.isNotEmpty() }?.let { actions.trashRequest(it) }

    /** After a favorite change or trash went through. */
    fun onChanged() {
        selection.clear()
        repository.refresh()
    }
}
