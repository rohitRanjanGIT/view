package com.rohit.gallery.feature.albums

import android.app.Activity
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.LocalUndoBar
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlbumDeleteEntryPoint {
    fun mediaActions(): MediaActions

    fun mediaRepository(): MediaRepository
}

/**
 * Deletes whole albums: every photo and video in them goes to Trash (kept 30 days, restorable), and an album with
 * nothing left in it disappears. The same rules as trashing photos: with "Skip confirmation pop-ups" on it happens at
 * once and an Undo bar shows; otherwise Android asks first. (The empty folder itself may stay on storage: Android
 * gives a gallery no way to remove folders, but nothing is in it and no gallery lists it.)
 */
@Stable
class AlbumDeleter internal constructor(private val start: (items: List<MediaItem>, albums: Int) -> Unit) {
    /** Trashes [items], everything in [albums] albums (the count is only for the message). */
    fun delete(items: List<MediaItem>, albums: Int) = start(items, albums)
}

/** [onDeleted] runs once the items really went to Trash (not if Android's prompt was declined). */
@Composable
fun rememberAlbumDeleter(onDeleted: () -> Unit = {}): AlbumDeleter {
    val context = LocalContext.current
    val app = context.applicationContext
    val entry = remember(app) { EntryPointAccessors.fromApplication(app, AlbumDeleteEntryPoint::class.java) }
    val actions = remember(entry) { entry.mediaActions() }
    val repository = remember(entry) { entry.mediaRepository() }
    val scope = rememberCoroutineScope()
    val undoBar = LocalUndoBar.current
    val latestOnDeleted by rememberUpdatedState(onDeleted)
    // What is waiting on Android's answer (plain holder: not drawn).
    val pending = remember { object { var items: List<MediaItem> = emptyList(); var albums = 0 } }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val items = pending.items
        val albums = pending.albums
        pending.items = emptyList()
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        repository.refresh()
        latestOnDeleted()
        if (undoBar != null && runCatching { MediaStore.canManageMedia(context) }.getOrDefault(false)) {
            val what = if (albums == 1) "album" else "$albums albums"
            undoBar.show(
                "Moved $what to trash",
                undo = { actions.restoreRequest(items.map { it.copy(isTrashed = true) }) },
                onUndone = repository::refresh,
            )
        }
    }

    return remember(actions) {
        AlbumDeleter { items, albums ->
            if (items.isEmpty()) return@AlbumDeleter
            scope.launch {
                val sender = runCatching { actions.trashRequest(items) }.getOrNull()
                if (sender == null) {
                    Toast.makeText(context, "Couldn't delete that", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                pending.items = items
                pending.albums = albums
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
        }
    }
}
