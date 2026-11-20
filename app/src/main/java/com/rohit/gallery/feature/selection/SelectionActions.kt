package com.rohit.gallery.feature.selection

import com.rohit.gallery.core.ui.components.LocalUndoBar
import android.provider.MediaStore
import com.rohit.gallery.feature.vault.rememberVaultLocker
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.domain.movable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
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

/**
 * What the selection action bar does with the selected items: share, favorite, trash, and move to another device
 * folder (a real move of the files; adding to a user album is [AlbumAdder], which moves nothing). Owned by a screen's ViewModel, so the same bar works on every grid.
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
    /** The album this grid shows, if any: it is no target for its own photos. Read when the picker opens. */
    val currentBucket: () -> Long? = { null },
) {
    /** Device folders to offer as a move target, in the Albums tab's order (pins, sort, custom order). */
    val albums: StateFlow<List<Album>> = albumRepository.organized
        .map { organized -> organized?.visible?.map { it.album }.orEmpty() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _progress = MutableStateFlow<TransferProgress?>(null)

    /** The move running now, with how far it got; null when idle. */
    val progress: StateFlow<TransferProgress?> = _progress

    /**
     * True from the tap on Move until the work is over (or Android's prompt was declined). Guards against
     * a second tap starting the same move twice. UI calls only, so main thread only.
     */
    private var working = false
    private var job: Job? = null
    private var stopped = false

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** One-off messages for a toast (results of a move). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var pendingMove: PendingMove? = null

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

    /** Reverses a trash of [items] (silent with "Manage media" access). */
    suspend fun untrashRequest(items: List<MediaItem>): IntentSender? =
        items.takeIf { it.isNotEmpty() }?.let { actions.restoreRequest(it.map { item -> item.copy(isTrashed = true) }) }

    fun refresh() = repository.refresh()

    /** After trash or favorite went through. */
    fun onWritten() {
        selection.clear()
        repository.refresh()
    }

    private suspend fun destination(album: Album?, newName: String?): Destination? =
        when {
            album != null -> album.cover?.let { transfer.destinationOf(it) }
            newName != null -> transfer.newAlbum(newName)
            else -> null
        } ?: run {
            _messages.tryEmit("Couldn't find that album's folder")
            null
        }

    /**
     * The selected items that can move into [dest], plus a note on the rest (see [movable]: app folders like WhatsApp's,
     * or a folder that refuses the type). Null, with a message, when none of them can.
     */
    private fun allowedIn(items: List<MediaItem>, dest: Destination): Pair<List<MediaItem>, String?>? {
        val (ok, note) = movable(items, dest.relativePath)
        if (ok.isEmpty()) {
            _messages.tryEmit(note ?: "Nothing to do")
            return null
        }
        return ok to note
    }

    /**
     * Starts a move: returns the system write confirmation, then [finishMove] does the work once it is accepted
     * (or [cancelMove] if it was declined). Null when there is nothing to start; any reason is in [messages].
     */
    suspend fun moveRequest(album: Album?, newName: String?): IntentSender? {
        if (working) return null
        val selected = selectedItems().takeIf { it.isNotEmpty() } ?: return null
        working = true
        val dest = destination(album, newName)
        val allowed = dest?.let { allowedIn(selected, it) }
        if (dest == null || allowed == null) {
            working = false
            return null
        }
        val (items, note) = allowed
        val sender = runCatching { transfer.writeRequest(items) }.getOrNull()
        if (sender == null) {
            working = false
            _messages.tryEmit("Couldn't start that action")
            return null
        }
        pendingMove = PendingMove(items, dest, note)
        return sender
    }

    fun finishMove() {
        val move = pendingMove ?: run {
            working = false
            return
        }
        pendingMove = null
        runTransfer("Moved", move.items, move.note) { onProgress -> transfer.move(move.items, move.destination, onProgress) }
    }

    /** Android's move prompt was declined: nothing happens, and Move can be tapped again. */
    fun cancelMove() {
        pendingMove = null
        working = false
    }

    /** Stops the running move after the current item. */
    fun stop() {
        stopped = true
        job?.cancel()
    }

    private val _finished = MutableSharedFlow<TransferResult>(extraBufferCapacity = 1)

    /** Each finished move (for a screen that closes once its photos are in place). Not sent when stopped. */
    val finished: SharedFlow<TransferResult> = _finished.asSharedFlow()

    private fun runTransfer(
        verb: String,
        items: List<MediaItem>,
        note: String?,
        work: suspend (onProgress: (handled: Int, done: Int) -> Unit) -> TransferResult,
    ) {
        stopped = false
        var done = 0
        _progress.value = TransferProgress(verb, 0, items.size)
        job = scope.launch {
            var result: TransferResult? = null
            try {
                result = work { handled, ok ->
                    done = ok
                    _progress.value = TransferProgress(verb, handled, items.size)
                }
            } finally {
                _progress.value = null
                job = null
                working = false
                selection.clear()
                repository.refresh()
                val r = result
                when {
                    r != null -> {
                        _finished.tryEmit(r)
                        _messages.tryEmit(transferMessage(verb, items.size, r, note))
                    }
                    stopped -> _messages.tryEmit("Stopped. $verb $done of ${items.size}")
                    else -> _messages.tryEmit("Couldn't complete that")
                }
            }
        }
    }
}

/** A move under way: [handled] of [total] items so far. */
data class TransferProgress(val verb: String, val handled: Int, val total: Int)

private class PendingMove(val items: List<MediaItem>, val destination: Destination, val note: String?)

/** The toast after a move. [note] says what was skipped before it started, if anything. */
internal fun transferMessage(verb: String, count: Int, result: TransferResult, note: String?): String {
    val main = when {
        result.failed == 0 -> "$verb $count ${if (count == 1) "item" else "items"}"
        result.done == 0 -> "Couldn't complete that"
        else -> "$verb ${result.done}, ${result.failed} failed"
    }
    return if (note == null) main else "$main. $note"
}

/**
 * The progress dialog for a running move, with a count and Stop. Back can't dismiss it (a half-done
 * move should be stopped on purpose, not by a stray swipe).
 */
@Composable
fun TransferProgressDialog(controller: SelectionActions, title: String? = null) {
    val progress by controller.progress.collectAsStateWithLifecycle()
    val p = progress ?: return
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        dismissButton = { TextButton(onClick = controller::stop) { Text("Stop") } },
        title = { Text(title ?: "Moving…") },
        text = {
            Column {
                if (p.handled == 0) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { p.handled.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
                }
                Text("${p.handled} of ${p.total}", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}

/** Which album sheet the More card opened. */
private enum class AlbumSheet { MoveToFolder, AddToAlbum }

/**
 * The bottom action bar for a selection, with everything it opens: system confirmations, the album and folder
 * sheets, the progress dialog and result toasts. Inside a user album, [currentUserAlbum] is it (no target for its
 * own photos), [onRemoveFromAlbum] adds Remove (from the album only) and [onSetCover] "Use as cover".
 */
@Composable
fun SelectionActionsBar(
    controller: SelectionActions,
    selection: SelectionState,
    modifier: Modifier = Modifier,
    allFavorite: Boolean,
    allowTransfer: Boolean = true,
    currentUserAlbum: Long? = null,
    onRemoveFromAlbum: (() -> Unit)? = null,
    onSetCover: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The system shows its own confirmation for trash/favorite; we only continue if it was accepted.
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) controller.onWritten()
    }
    // Trash: with "Skip confirmation pop-ups" on, Android trashes at once, so offer Undo instead of a question.
    val undoBar = LocalUndoBar.current
    val trashed = remember { object { var items: List<MediaItem> = emptyList() } }
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val items = trashed.items
        trashed.items = emptyList()
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        controller.onWritten()
        if (undoBar != null && runCatching { MediaStore.canManageMedia(context) }.getOrDefault(false)) {
            val what = if (items.size == 1) "1 item" else "${items.size} items"
            undoBar.show("Moved $what to trash", undo = { controller.untrashRequest(items) }, onUndone = controller::refresh)
        }
    }
    // Moving needs its own write confirmation; the move itself runs once it is accepted.
    val moveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) controller.finishMove() else controller.cancelMove()
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
    val adder = rememberAlbumAdder()
    var sheet by remember { mutableStateOf<AlbumSheet?>(null) }
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
        onTrash = {
            trashed.items = controller.selectedItems()
            launch(trashLauncher) { controller.trashRequest() }
        },
        onRemoveFromAlbum = onRemoveFromAlbum,
        onAddToAlbum = if (allowTransfer) ({ sheet = AlbumSheet.AddToAlbum }) else null,
        onMove = if (allowTransfer) ({ sheet = AlbumSheet.MoveToFolder }) else null,
        onSetCover = onSetCover?.takeIf { count == 1 },
        onLock = if (allowTransfer && locker.enabled) ({ locker.lock(controller.selectedItems()) }) else null,
        modifier = modifier,
    )

    when (sheet) {
        AlbumSheet.MoveToFolder -> {
            val folders by controller.albums.collectAsStateWithLifecycle()
            // The folder shown here is no target for its own photos.
            val current = remember { controller.currentBucket() }
            AlbumPickerSheet(
                title = "Move to folder",
                subtitle = "The files move to that folder on the device",
                albums = folders.filter { it.bucketId != current },
                existingNames = folders.map { it.name },
                newLabel = "New folder",
                onDismiss = { sheet = null },
                onPick = { folder, newName ->
                    sheet = null
                    // Failures explain themselves in a toast (controller.messages).
                    scope.launch {
                        val sender = controller.moveRequest(folder, newName) ?: return@launch
                        moveLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                },
            )
        }
        AlbumSheet.AddToAlbum -> {
            val albums by adder.repository.albums.collectAsStateWithLifecycle()
            val all = albums.orEmpty()
            AlbumPickerSheet(
                title = "Add to album",
                subtitle = "Nothing is copied or moved",
                albums = all.filter { it.id != currentUserAlbum }.map { it.asAlbum() },
                existingNames = all.map { it.name },
                folderNames = false,
                onDismiss = { sheet = null },
                onPick = { album, newName ->
                    sheet = null
                    adder.add(controller.selectedItems(), album?.bucketId, album?.name ?: newName.orEmpty())
                    selection.clear()
                },
            )
        }
        null -> Unit
    }
    TransferProgressDialog(controller)
}
