package com.rohit.gallery.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.vault.VaultRepository
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.domain.TransferResult
import com.rohit.gallery.core.model.AlbumEntry
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.model.buildAlbums
import com.rohit.gallery.feature.selection.SelectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val session: VaultSession,
    private val actions: MediaActions,
) : ViewModel() {
    val unlocked: StateFlow<Boolean> = session.unlocked
    val items: StateFlow<List<MediaItem>?> = vault.items

    /** Locked items grouped by the album they were locked from, newest album first. */
    val albums: StateFlow<List<AlbumEntry>?> = vault.items
        .map { list -> list?.let { buildAlbums(it).map { album -> AlbumEntry(album, pinned = false) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Photo selection (the Photos page and an album's page share it; it is cleared when switching). */
    val selection = SelectionState()

    private val _progress = MutableStateFlow<LockProgress?>(null)

    /** Progress while unlocking (copying files back out takes a moment for videos). */
    val progress: StateFlow<LockProgress?> = _progress.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun onUnlocked() = session.unlock()

    fun lockNow() = session.lock()

    fun selectedItems(): List<MediaItem> {
        val ids = selection.ids
        return items.value.orEmpty().filter { it.id in ids }
    }

    fun itemsInAlbums(albumIds: Set<Long>): List<MediaItem> = items.value.orEmpty().filter { it.bucketId in albumIds }

    fun shareIntent(items: List<MediaItem>) = actions.shareIntent(items)

    /** Puts [chosen] back where they were locked from. */
    fun unlock(chosen: List<MediaItem>) = runUnlock(chosen) { vault.unlock(it, ::onProgress) }

    /** Everything back to shared storage (the escape hatch before uninstalling or clearing app data). */
    fun unlockAll() = runUnlock(items.value.orEmpty()) { vault.unlockAll(::onProgress) }

    /** Deletes [chosen] for good: they are only in Locked, so the screen confirms first. */
    fun deleteForever(chosen: List<MediaItem>) {
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            val deleted = vault.deleteForever(chosen)
            selection.clear()
            _messages.tryEmit("Deleted ${plural(deleted)} for good")
        }
    }

    private fun onProgress(done: Int, total: Int) {
        _progress.value = LockProgress(done, total)
    }

    private fun runUnlock(chosen: List<MediaItem>, work: suspend (List<MediaItem>) -> TransferResult) {
        if (chosen.isEmpty() || _progress.value != null) return
        viewModelScope.launch {
            _progress.value = LockProgress(0, chosen.size)
            val result = try {
                work(chosen)
            } finally {
                _progress.value = null
            }
            selection.clear()
            _messages.tryEmit(
                when {
                    result.failed == 0 -> "Moved ${plural(result.done)} back to their albums"
                    result.done == 0 -> "Couldn't unlock that; it's still in Locked"
                    else -> "Moved ${plural(result.done)} back; ${result.failed} stayed in Locked"
                },
            )
        }
    }
}
