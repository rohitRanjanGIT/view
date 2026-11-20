package com.rohit.gallery.feature.albums

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rohit.gallery.core.data.repository.message
import com.rohit.gallery.core.data.repository.CreateAlbumResult
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.data.repository.UserAlbumRepository
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.pillSlot
import com.rohit.gallery.feature.selection.ActionItem
import com.rohit.gallery.feature.selection.SelectionState
import com.rohit.gallery.navigation.PickForAlbumRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How picking for a user album ended. */
sealed interface PickOutcome {
    /** A new album was made (open it). */
    data class Created(val albumId: Long, val message: String) : PickOutcome

    /** Photos went into the existing album (back to it). */
    data class Added(val message: String) : PickOutcome

    /** Nothing happened; [message] says why (stay on the picker). */
    data class Failed(val message: String) : PickOutcome
}

/**
 * Picking photos for a user album (see [PickForAlbumRoute]). Adding only records which photos are in the album:
 * nothing is copied or moved, so any photo can go into any album, whatever folder it is in.
 */
@HiltViewModel
class PickForAlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val media: MediaRepository,
    private val albums: UserAlbumRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<PickForAlbumRoute>()
    val name: String = route.name
    val isNew: Boolean = route.albumId == null

    val selection = SelectionState()

    /** The whole library, minus what the album already shows; null while loading. */
    val items: StateFlow<List<MediaItem>?> = combine(media.state, albums.albums) { state, all ->
        val library = (state as? MediaIndexState.Ready)?.items ?: return@combine null
        val inAlbum = route.albumId?.let { id -> all?.firstOrNull { it.id == id }?.items?.mapTo(HashSet()) { it.id } }
        if (inAlbum.isNullOrEmpty()) library else library.filter { it.id !in inAlbum }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _outcome = MutableSharedFlow<PickOutcome>(extraBufferCapacity = 1)
    val outcome: SharedFlow<PickOutcome> = _outcome.asSharedFlow()

    /** Guards against a second tap on Add while the first is still writing. */
    private var working = false

    fun add() {
        if (working) return
        val ids = selection.ids
        val picked = items.value.orEmpty().filter { it.id in ids }
        if (picked.isEmpty()) return
        working = true
        viewModelScope.launch {
            try {
                val albumId = route.albumId
                _outcome.emit(
                    if (albumId == null) {
                        when (val result = albums.create(name, picked)) {
                            is CreateAlbumResult.Created -> PickOutcome.Created(result.id, result.added.message(name))
                            is CreateAlbumResult.Invalid -> PickOutcome.Failed(result.message)
                        }
                    } else {
                        PickOutcome.Added(albums.add(albumId, picked).message(name))
                    },
                )
            } finally {
                working = false
            }
        }
    }
}

/** Picks photos for a user album; [onCreated] opens a new album once made, [onClose] goes back. */
@Composable
fun PickForAlbumScreen(
    onClose: () -> Unit,
    onCreated: (albumId: Long) -> Unit,
    viewModel: PickForAlbumViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.outcome.collect { outcome ->
            when (outcome) {
                is PickOutcome.Created -> {
                    Toast.makeText(context, outcome.message, Toast.LENGTH_SHORT).show()
                    onCreated(outcome.albumId)
                }
                is PickOutcome.Added -> {
                    Toast.makeText(context, outcome.message, Toast.LENGTH_SHORT).show()
                    onClose()
                }
                is PickOutcome.Failed -> Toast.makeText(context, outcome.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    PhotoPickerLayout(
        title = if (viewModel.isNew) "New album: ${viewModel.name}" else "Add to ${viewModel.name}",
        hint = "Tap photos to add them. Nothing is copied or moved",
        items = items,
        selection = viewModel.selection,
        onClose = onClose,
    ) { enabled ->
        ActionItem(Icons.Filled.Add, "Add", enabled, viewModel::add, Modifier.pillSlot())
    }
}
