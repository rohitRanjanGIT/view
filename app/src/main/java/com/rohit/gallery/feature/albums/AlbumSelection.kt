package com.rohit.gallery.feature.albums

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.rohit.gallery.core.ui.components.FloatingActionPill
import com.rohit.gallery.core.ui.components.pillSlot
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.rohit.gallery.core.model.AlbumEntry
import com.rohit.gallery.core.ui.components.OrganizeIcons
import com.rohit.gallery.feature.selection.ActionItem

/** Albums picked for a bulk pin / hide. Selecting stops when the last one is deselected. */
@Stable
class AlbumSelection(initial: Set<Long> = emptySet()) {
    var ids by mutableStateOf(initial)
        private set

    val isSelecting: Boolean get() = ids.isNotEmpty()

    fun toggle(bucketId: Long) {
        ids = if (bucketId in ids) ids - bucketId else ids + bucketId
    }

    fun selectAll(entries: List<AlbumEntry>) {
        ids = entries.mapTo(LinkedHashSet()) { it.album.bucketId }
    }

    fun clear() {
        ids = emptySet()
    }

    companion object {
        val Saver = Saver<AlbumSelection, LongArray>(
            save = { it.ids.toLongArray() },
            restore = { AlbumSelection(it.toSet()) },
        )
    }
}

@Composable
fun rememberAlbumSelection(): AlbumSelection = rememberSaveable(saver = AlbumSelection.Saver) { AlbumSelection() }

/**
 * Bulk actions for the selected albums. Each button reverses when it already applies to all of them:
 * Pin becomes Unpin when every selected album is pinned.
 * [entries] are the selected albums in display order (a bulk pin keeps that order).
 */
@Composable
fun AlbumSelectionBar(
    entries: List<AlbumEntry>,
    onPin: (ids: List<Long>, pinned: Boolean) -> Unit,
    onLock: ((ids: List<Long>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ids = entries.map { it.album.bucketId }
    val enabled = entries.isNotEmpty()
    val allPinned = enabled && entries.all { it.pinned }
    FloatingActionPill(modifier) {
        ActionItem(OrganizeIcons.Pin, if (allPinned) "Unpin" else "Pin", enabled, { onPin(ids, !allPinned) }, Modifier.pillSlot())
        if (onLock != null) ActionItem(Icons.Filled.Lock, "Lock", enabled, { onLock(ids) }, Modifier.pillSlot())
    }
}
