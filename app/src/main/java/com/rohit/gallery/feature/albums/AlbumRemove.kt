package com.rohit.gallery.feature.albums

import android.app.Activity
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.data.repository.AlbumRepository
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.domain.Destination
import com.rohit.gallery.core.domain.MediaTransfer
import com.rohit.gallery.core.domain.folderAccepts
import com.rohit.gallery.core.domain.normalizeRelativePath
import com.rohit.gallery.core.model.Album
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.AlbumPickerSheet
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Removing albums but keeping their photos: each photo the app moved into the album goes back to the folder it
 * came from; the rest ([others]: copies, or photos moved before origins were kept) need a place picked by the user.
 */
data class RemovalPlan(
    val back: List<Pair<MediaItem, Destination>>,
    val others: List<MediaItem>,
)

/**
 * Sorts [items] (everything in the albums being removed, whose folders are [removedFolders]) by [origins]: an origin
 * counts if it is not one of the folders being removed and Android allows that kind of item there.
 */
fun planRemoval(items: List<MediaItem>, origins: Map<Long, String>, removedFolders: Set<String>): RemovalPlan {
    val removed = removedFolders.map { normalizeRelativePath(it).lowercase() }.toSet()
    val back = ArrayList<Pair<MediaItem, Destination>>()
    val others = ArrayList<MediaItem>()
    for (item in items) {
        val origin = origins[item.id]?.let(::normalizeRelativePath)
        if (origin != null && origin.isNotEmpty() && origin.lowercase() !in removed && folderAccepts(origin, item.isVideo)) {
            back += item to Destination(origin)
        } else {
            others += item
        }
    }
    return RemovalPlan(back, others)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlbumRemoveEntryPoint {
    fun mediaTransfer(): MediaTransfer

    fun mediaRepository(): MediaRepository

    fun albumRepository(): AlbumRepository
}

@Stable
class AlbumRemover internal constructor(private val start: (albums: List<Album>) -> Unit) {
    /** Asks, then empties [albums] into other folders; nothing goes to Trash. */
    fun remove(albums: List<Album>) = start(albums)
}

/** What the remover is doing: asking, waiting for Android, or moving. */
private sealed interface RemoveStep {
    val albums: List<Album>
    val plan: RemovalPlan

    data class Confirm(override val albums: List<Album>, override val plan: RemovalPlan, val removedBuckets: Set<Long>) : RemoveStep

    data class Pick(override val albums: List<Album>, override val plan: RemovalPlan, val removedBuckets: Set<Long>) : RemoveStep
}

/**
 * Remove album (keep the photos). [onRemoved] runs once the albums are empty and so gone (an album screen closes).
 * Android asks first (the photos belong to other apps), silently with "Skip confirmation pop-ups" on.
 */
@Composable
fun rememberAlbumRemover(onRemoved: () -> Unit = {}): AlbumRemover {
    val context = LocalContext.current
    val app = context.applicationContext
    val entry = remember(app) { EntryPointAccessors.fromApplication(app, AlbumRemoveEntryPoint::class.java) }
    val transfer = remember(entry) { entry.mediaTransfer() }
    val repository = remember(entry) { entry.mediaRepository() }
    val albumRepository = remember(entry) { entry.albumRepository() }
    val scope = rememberCoroutineScope()
    val latestOnRemoved by rememberUpdatedState(onRemoved)

    var step by remember { mutableStateOf<RemoveStep?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // The moves waiting on Android's answer (plain holder: not drawn).
    val pending = remember { object { var moves: List<Pair<MediaItem, Destination>> = emptyList(); var albums: List<Album> = emptyList(); var skipped = 0 } }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val moves = pending.moves
        val albums = pending.albums
        val skipped = pending.skipped
        pending.moves = emptyList()
        if (result.resultCode != Activity.RESULT_OK || moves.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            progress = 0 to moves.size
            val outcome = try {
                transfer.moveEach(moves, rememberOrigins = false) { handled, _ -> progress = handled to moves.size }
            } finally {
                progress = null
                repository.refresh()
            }
            val name = if (albums.size == 1) albums.first().name else "${albums.size} albums"
            if (outcome.failed == 0 && skipped == 0) {
                albumRepository.forgetAsync(albums.map { it.bucketId })
                toast("Removed $name. ${photoCount(outcome.done)} kept")
                latestOnRemoved()
            } else {
                toast("Moved ${outcome.done}; ${outcome.failed + skipped} couldn't move, so $name stays")
            }
        }
    }

    /** Asks Android for write access to [moves], then moves them. */
    fun begin(albums: List<Album>, moves: List<Pair<MediaItem, Destination>>, skipped: Int) {
        if (moves.isEmpty()) {
            toast("Android doesn't allow these photos in that folder")
            return
        }
        scope.launch {
            val sender = runCatching { transfer.writeRequest(moves.map { it.first }) }.getOrNull()
            if (sender == null) {
                toast("Couldn't start that")
                return@launch
            }
            pending.moves = moves
            pending.albums = albums
            pending.skipped = skipped
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    when (val s = step) {
        is RemoveStep.Confirm -> AlertDialog(
            onDismissRequest = { step = null },
            title = { Text("Remove ${titleOf(s.albums)}?") },
            text = { Text("${if (s.albums.size == 1) "Its" else "Their"} ${photoCount(s.plan.back.size)} go back to the albums they came from. Nothing is deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    step = null
                    begin(s.albums, s.plan.back, skipped = 0)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { step = null }) { Text("Cancel") } },
        )
        is RemoveStep.Pick -> {
            val all by remember(albumRepository) { albumRepository.organized.map { o -> o?.visible?.map { it.album }.orEmpty() } }
                .collectAsStateWithLifecycle(emptyList())
            val back = s.plan.back.size
            AlbumPickerSheet(
                title = "Remove ${titleOf(s.albums)}: where should ${if (back == 0) "its" else "the other"} ${photoCount(s.plan.others.size)} go?",
                subtitle = if (back > 0) "${photoCount(back)} go back to the albums they came from. Nothing is deleted." else "Nothing is deleted.",
                albums = all.filter { it.bucketId !in s.removedBuckets },
                existingNames = all.map { it.name },
                newLabel = "New folder",
                onDismiss = { step = null },
                onPick = { album, newName ->
                    step = null
                    scope.launch {
                        val dest = when {
                            album != null -> album.cover?.let { transfer.destinationOf(it) }
                            newName != null -> transfer.newAlbum(newName)
                            else -> null
                        }
                        if (dest == null) {
                            toast("Couldn't find that album's folder")
                            return@launch
                        }
                        val (fits, refused) = s.plan.others.partition { folderAccepts(dest.relativePath, it.isVideo) }
                        begin(s.albums, s.plan.back + fits.map { it to dest }, skipped = refused.size)
                    }
                },
            )
        }
        null -> Unit
    }

    progress?.let { (handled, total) ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Moving photos…") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { if (total == 0) 0f else handled.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    Text("$handled of $total", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            },
        )
    }

    return remember(transfer) {
        AlbumRemover { albums ->
            if (albums.isEmpty()) return@AlbumRemover
            scope.launch {
                val ids = albums.map { it.bucketId }.toSet()
                val items = (repository.state.value as? MediaIndexState.Ready)?.items.orEmpty().filter { it.bucketId in ids }
                if (items.isEmpty()) return@launch
                // An app's own folder (WhatsApp Images...) can't be emptied: Android never lets its photos move out.
                val appFolders = items.filter { it.isInAppFolder }.map { it.bucketName }.distinct()
                if (appFolders.isNotEmpty()) {
                    toast("${appFolders.joinToString()} belongs to an app, so Android doesn't let its photos move. Add them to an album instead")
                    return@launch
                }
                val folders = albums.mapNotNull { a -> a.cover?.let { transfer.destinationOf(it) }?.relativePath }.toSet()
                if (folders.size < albums.size) {
                    toast("Couldn't find that album's folder")
                    return@launch
                }
                val plan = planRemoval(items, transfer.originsOf(items), folders)
                step = if (plan.others.isEmpty()) RemoveStep.Confirm(albums, plan, ids) else RemoveStep.Pick(albums, plan, ids)
            }
        }
    }
}

private fun titleOf(albums: List<Album>) = if (albums.size == 1) "“${albums.first().name}”" else "${albums.size} albums"

private fun photoCount(n: Int) = if (n == 1) "1 photo" else "$n photos"
