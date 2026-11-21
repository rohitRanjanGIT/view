package com.rohit.gallery.feature.albums

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rohit.gallery.core.data.repository.AlbumRepository
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.domain.Destination
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.domain.MediaTransfer
import com.rohit.gallery.core.domain.folderAccepts
import com.rohit.gallery.core.domain.folderLabel
import com.rohit.gallery.core.model.Album
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.pillSlot
import com.rohit.gallery.core.ui.icons.GalleryIcons
import com.rohit.gallery.feature.selection.ActionItem
import com.rohit.gallery.feature.selection.SelectionActions
import com.rohit.gallery.feature.selection.SelectionState
import com.rohit.gallery.feature.selection.TransferProgressDialog
import com.rohit.gallery.navigation.NewAlbumRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The folder photos will go into: still being looked up, found, or not found. */
private sealed interface TargetFolder {
    data object Loading : TargetFolder

    data class Found(val destination: Destination) : TargetFolder

    data object Missing : TargetFolder
}

/**
 * A new album is made by putting photos in it (Android only knows a folder as an album once something is in it):
 * every photo and video, like Pictures; tap or drag to pick, then move them into the album named [name].
 * The same screen moves photos into an existing album ([existingBucket]).
 */
@HiltViewModel
class NewAlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: MediaRepository,
    actions: MediaActions,
    private val transfer: MediaTransfer,
    albumRepository: AlbumRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<NewAlbumRoute>()
    val name: String = route.name

    /** Adding to an album that already exists (its own folder), rather than making a new one. */
    val existingBucket: Long? = route.bucketId

    val selection = SelectionState()

    private val folder = MutableStateFlow<TargetFolder>(TargetFolder.Loading)

    init {
        viewModelScope.launch {
            folder.value = if (existingBucket == null) {
                TargetFolder.Found(transfer.newAlbum(name))
            } else {
                // Read the album's folder from one of its items (waits for the index after a restart).
                val ready = repo.state.filterIsInstance<MediaIndexState.Ready>().first()
                ready.items.firstOrNull { it.bucketId == existingBucket }
                    ?.let { transfer.destinationOf(it) }
                    ?.let { TargetFolder.Found(it) }
                    ?: TargetFolder.Missing
            }
        }
    }

    /**
     * Everything that can go into the album: not what is already in it, and only the kinds Android allows in its folder
     * (no photos in Movies/, nothing in Download/). Null while loading.
     */
    val items: StateFlow<List<MediaItem>?> = combine(repo.state, folder) { state, target ->
        val all = (state as? MediaIndexState.Ready)?.items ?: return@combine null
        val path = (target as? TargetFolder.Found)?.destination?.relativePath ?: return@combine if (target == TargetFolder.Missing) emptyList() else null
        all.filter { it.bucketId != existingBucket && folderAccepts(path, it.isVideo) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Why some or all of the library can't be added here, or null if everything can. [blocked] says nothing at all
     * can go in (the grid is replaced by the message).
     */
    data class Notice(val text: String, val blocked: Boolean)

    val notice: StateFlow<Notice?> = folder.map { target ->
        when (target) {
            TargetFolder.Loading -> null
            TargetFolder.Missing -> Notice("Couldn't find this album's folder", blocked = true)
            is TargetFolder.Found -> {
                val path = target.destination.relativePath
                val label = folderLabel(path)
                val photos = folderAccepts(path, isVideo = false)
                val videos = folderAccepts(path, isVideo = true)
                when {
                    photos && videos -> null
                    videos -> Notice("Only videos: Android doesn't allow photos in $label", blocked = false)
                    photos -> Notice("Only photos: Android doesn't allow videos in $label", blocked = false)
                    else -> Notice("Android doesn't let apps add photos or videos to $label", blocked = true)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transferActions = SelectionActions(
        scope = viewModelScope,
        selection = selection,
        allItems = { items.value.orEmpty() },
        actions = actions,
        transfer = transfer,
        repository = repo,
        albumRepository = albumRepository,
    )

    /** The existing album to add to (its folder is read from one of its items); null for a new album. */
    private fun target(): Album? {
        val id = existingBucket ?: return null
        val cover = (repo.state.value as? MediaIndexState.Ready)?.items?.firstOrNull { it.bucketId == id } ?: return null
        return Album(id, name, 0, cover)
    }

    suspend fun moveRequest() = transferActions.moveRequest(target(), if (existingBucket == null) name else null)
}

/**
 * Pick photos from anywhere and move them into an album: a new one (Albums tab, +) or the one the + inside an album was
 * tapped in. A real move between folders, after Android's write confirmation.
 */
@Composable
fun NewAlbumScreen(onClose: () -> Unit, viewModel: NewAlbumViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val controller = viewModel.transferActions
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(controller) { controller.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    // Done once the photos are in: back to where + was tapped, where the album now shows them.
    LaunchedEffect(controller) { controller.finished.collect { if (it.done > 0) onClose() } }

    // Moving needs Android's write confirmation first; the move runs once it is accepted.
    val moveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) controller.finishMove() else controller.cancelMove()
    }
    // Photos Android won't move in place are copied and checked first; their originals are deleted after this confirmation.
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { _ ->
        controller.finishFallback()
    }
    LaunchedEffect(controller) {
        controller.deleteRequests.collect { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }

    val current = notice
    PhotoPickerLayout(
        title = if (viewModel.existingBucket == null) "New album: ${viewModel.name}" else "Move into ${viewModel.name}",
        hint = current?.takeIf { !it.blocked }?.text ?: "Tap photos to move them here",
        items = items,
        selection = viewModel.selection,
        onClose = onClose,
        blockedText = current?.takeIf { it.blocked }?.text,
    ) { enabled ->
        ActionItem(GalleryIcons.FolderMove, "Move here", enabled, {
            scope.launch {
                // Null means nothing to start; the reason (if any) arrives as a toast.
                val sender = viewModel.moveRequest() ?: return@launch
                moveLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
        }, Modifier.pillSlot())
    }

    TransferProgressDialog(controller, title = "Moving into ${viewModel.name}…")
}
