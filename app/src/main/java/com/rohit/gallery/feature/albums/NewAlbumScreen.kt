package com.rohit.gallery.feature.albums

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rohit.gallery.core.data.repository.AlbumRepository
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.domain.MediaTransfer
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.FloatingActionPill
import com.rohit.gallery.core.ui.components.pillSlot
import com.rohit.gallery.core.ui.icons.GalleryIcons
import com.rohit.gallery.feature.selection.ActionItem
import com.rohit.gallery.feature.selection.GridSizeViewModel
import com.rohit.gallery.feature.selection.MediaTimeline
import com.rohit.gallery.feature.selection.SelectionActions
import com.rohit.gallery.feature.selection.SelectionState
import com.rohit.gallery.navigation.NewAlbumRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A new album is made by putting photos in it (Android only knows a folder as an album once something is in it):
 * every photo and video, like Pictures; tap or drag to pick, then copy or move them into the album named [name].
 */
@HiltViewModel
class NewAlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: MediaRepository,
    actions: MediaActions,
    transfer: MediaTransfer,
    albumRepository: AlbumRepository,
) : ViewModel() {
    val name: String = savedStateHandle.toRoute<NewAlbumRoute>().name

    val selection = SelectionState()

    val items: StateFlow<List<MediaItem>?> = repository.state
        .map { (it as? MediaIndexState.Ready)?.items }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transferActions = SelectionActions(
        scope = viewModelScope,
        selection = selection,
        allItems = { items.value.orEmpty() },
        actions = actions,
        transfer = transfer,
        repository = repository,
        albumRepository = albumRepository,
    )
}

@Composable
fun NewAlbumScreen(onClose: () -> Unit, viewModel: NewAlbumViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selection = viewModel.selection
    val controller = viewModel.transferActions
    val busy by controller.busy.collectAsStateWithLifecycle()
    val sizes: GridSizeViewModel = hiltViewModel()
    val level by sizes.level.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(controller) { controller.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    // Done once the photos are in: back to Albums, where the new album now shows.
    LaunchedEffect(controller) { controller.finished.collect { if (it.done > 0) onClose() } }

    // Moving needs Android's write confirmation first; the move runs once it is accepted.
    val moveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) controller.finishMove()
    }

    val count = selection.ids.size
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "New album: ${viewModel.name}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (count == 0) "Tap photos to add them" else "$count selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val list = items
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                // A tap picks a photo here (it never opens the viewer).
                else -> level?.let { MediaTimeline(list, selection, it, sizes::setLevel, onOpenMedia = { item -> selection.toggle(item.id) }) }
            }
        }

        FloatingActionPill(Modifier.align(Alignment.BottomCenter)) {
            ActionItem(GalleryIcons.Copy, "Copy here", count > 0, { controller.copyTo(null, viewModel.name) }, Modifier.pillSlot())
            ActionItem(GalleryIcons.Folder, "Move here", count > 0, {
                scope.launch {
                    val sender = controller.moveRequest(null, viewModel.name)
                    if (sender != null) moveLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }, Modifier.pillSlot())
        }
    }

    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Adding to ${viewModel.name}…") },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
        )
    }
}
