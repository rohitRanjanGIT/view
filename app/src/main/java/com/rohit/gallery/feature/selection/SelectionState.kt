package com.rohit.gallery.feature.selection

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rohit.gallery.core.model.MediaItem
import kotlin.math.max
import kotlin.math.min

/**
 * Multi-select state for a media grid. Holds only ids, so it stays valid when the index refreshes.
 *
 * Selection mode ([isSelecting]) stays on when the last item is deselected, like One UI; the user
 * leaves it with the close button or Back.
 */
@Stable
class SelectionState {
    var isSelecting by mutableStateOf(false)
        private set

    var ids: Set<Long> by mutableStateOf(emptySet())
        private set

    // Drag-select bookkeeping: the selection before the drag, where it started, and whether the
    // drag adds to or removes from the selection (decided by the item it started on).
    private var base: Set<Long> = emptySet()
    private var anchorIndex = -1
    private var adding = true

    fun toggle(id: Long) {
        isSelecting = true
        ids = if (id in ids) ids - id else ids + id
    }

    /** Starts a long-press drag at [index] in the flat, newest-first item list. */
    fun startDrag(index: Int, id: Long) {
        isSelecting = true
        base = ids
        anchorIndex = index
        adding = id !in base
        ids = if (adding) base + id else base - id
    }

    /** Extends the drag to [index]: everything between the anchor and here is added or removed. */
    fun dragTo(index: Int, items: List<MediaItem>) {
        if (anchorIndex < 0 || items.isEmpty()) return
        val from = max(0, min(anchorIndex, index))
        val to = min(items.lastIndex, max(anchorIndex, index))
        val range = HashSet<Long>(to - from + 1)
        for (i in from..to) range.add(items[i].id)
        ids = if (adding) base + range else base - range
    }

    fun endDrag() {
        anchorIndex = -1
    }

    fun selectAll(items: List<MediaItem>) {
        isSelecting = true
        ids = items.mapTo(HashSet(items.size)) { it.id }
    }

    fun clear() {
        isSelecting = false
        ids = emptySet()
        anchorIndex = -1
    }
}
