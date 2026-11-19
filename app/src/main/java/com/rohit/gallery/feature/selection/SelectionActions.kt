package com.rohit.gallery.feature.selection

import com.rohit.gallery.feature.vault.rememberVaultLocker
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.data.repository.AlbumRepository
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.domain.Destination
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.domain.MediaTransfer
import com.rohit.gallery.core.domain.TransferResult
import com.rohit.gallery.core.model.Album
import com.rohit.gallery.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TransferKind { Move, Copy }

/**
 * What the selection action bar does with the selected items: share, favorite, trash, and move or
 * copy to an album. Owned by a screen's ViewModel, so the same bar works on every grid.
 *
 * [allItems] returns the items the selection ids refer to.
 */
class SelectionActions(
    private val scope: CoroutineScope,
    private val selection: SelectionState,
    private val allItems: () -> List<MediaItem>,
    private val actions: MediaActions,
    private val transfer: MediaTransfer,
    private val repository: MediaRepository,
    albumRepository: AlbumRepository,
) {
    /** Albums to offer as a move/copy target. */
    val albums: StateFlow<List<Album>> = albumRepository.albums
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)

    /** True while a copy or move is running (copying big videos takes a while). */
    val busy: StateFlow<Boolean> = _busy

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** One-off messages for a toast (results of move/copy). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var pendingMove: Pair<List<MediaItem>, Destination>? = null

    /** The selected items that still exist. */
    fun selectedItems(): List<MediaItem> {
        val ids = selection.ids
        return if (ids.isEmpty()) emptyList() else allItems().filter { it.id in ids }
    }

    fun shareIntent(): Intent? = selectedItems().takeIf { it.isNotEmpty() }?.let(actions::shareIntent)

    suspend fun trashRequest(): IntentSender? =
        selectedItems().takeIf { it.isNotEmpty() }?.let { actions.trashRequest(it) }

    /** Favorites everything selected, or removes the mark if all of it is already a favorite. */
    suspend fun favoriteRequest(): IntentSender? {
        val items = selectedItems().takeIf { it.isNotEmpty() } ?: return null
        return actions.favoriteRequest(items, favorite = !items.all { it.isFavorite })
    }

    /** After trash or favorite went through. */
    fun onWritten() {
        selection.clear()
        repository.refresh()
    }

    private suspend fun destination(album: Album?, newName: String?): Destination? =
        when {
            album != null -> transfer.destinationOf(album.cover)
            newName != null -> transfer.newAlbum(newName)
            else -> null
        } ?: run {
            _messages.tryEmit("Couldn't find that album's folder")
            null
        }

    /** Starts a move: returns the system write confirmation, then [finishMove] does the work once it is accepted. */
    suspend fun moveRequest(album: Album?, newName: String?): IntentSender? {
        val items = selectedItems().takeIf { it.isNotEmpty() } ?: return null
        val dest = destination(album, newName) ?: return null
        pendingMove = items to dest
        return runCatching { transfer.writeRequest(items) }.getOrNull()
    }

    fun finishMove() {
        val (items, dest) = pendingMove ?: return
        pendingMove = null
        runTransfer("Moved", items) { transfer.move(items, dest) }
    }

    fun copyTo(album: Album?, newName: String?) {
        val items = selectedItems().takeIf { it.isNotEmpty() } ?: return
        scope.launch {
            val dest = destination(album, newName) ?: return@launch
            runTransfer("Copied", items) { transfer.copy(items, dest) }
        }
    }

    private fun runTransfer(verb: String, items: List<MediaItem>, work: suspend () -> TransferResult) {
        scope.launch {
            _busy.value = true
            val result = try {
                work()
            } finally {
                _busy.value = false
            }
            selection.clear()
            repository.refresh()
            _messages.tryEmit(
                when {
                    result.failed == 0 -> "$verb ${items.size} ${if (items.size == 1) "item" else "items"}"
                    result.done == 0 -> "Couldn't complete that"
                    else -> "$verb ${result.done}, ${result.failed} failed"
                },
            )
        }
    }
}

/**
 * The bottom action bar for a selection, with everything it opens: system confirmations, the
 * album picker, the progress dialog and result toasts.
 */
@Composable
fun SelectionActionsBar(
    controller: SelectionActions,
    selection: SelectionState,
    modifier: Modifier = Modifier,
    allFavorite: Boolean,
    allowTransfer: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The system shows its own confirmation for trash/favorite; we only continue if it was accepted.
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) controller.onWritten()
    }
    // Moving needs its own write confirmation; the move itself runs once it is accepted.
    val moveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) controller.finishMove()
    }

    fun launch(launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>, request: suspend () -> IntentSender?) {
        scope.launch {
            val sender = runCatching { request() }.getOrNull()
            if (sender == null) {
                Toast.makeText(context, "Couldn't start that action", Toast.LENGTH_SHORT).show()
            } else {
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
        }
    }

    // Lock: copies into Locked, then Android confirms deleting the originals.
    val locker = rememberVaultLocker(onLocked = { if (it > 0) selection.clear() })
    var picker by remember { mutableStateOf<TransferKind?>(null) }
    val albums by controller.albums.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    LaunchedEffect(controller) {
        controller.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val count = selection.ids.size
    SelectionActionBar(
        enabled = count > 0,
        allFavorite = allFavorite,
        onShare = {
            val intent = controller.shareIntent()
            if (intent != null) {
                runCatching { context.startActivity(intent) }.onFailure {
                    Toast.makeText(context, "Couldn't share that many items at once", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onFavorite = { launch(writeLauncher) { controller.favoriteRequest() } },
        onTrash = { launch(writeLauncher) { controller.trashRequest() } },
        onMove = if (allowTransfer) ({ picker = TransferKind.Move }) else null,
        onCopy = if (allowTransfer) ({ picker = TransferKind.Copy }) else null,
        onLock = if (allowTransfer) ({ locker.lock(controller.selectedItems()) }) else null,
        modifier = modifier,
    )

    picker?.let { kind ->
        AlbumPickerSheet(
            title = if (kind == TransferKind.Move) "Move to album" else "Copy to album",
            albums = albums,
            onDismiss = { picker = null },
            onPick = { album, newName ->
                picker = null
                if (kind == TransferKind.Copy) {
                    controller.copyTo(album, newName)
                } else {
                    launch(moveLauncher) { controller.moveRequest(album, newName) }
                }
            },
        )
    }
    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Working…") },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
        )
    }
}
