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
 * Selection mode ([isSelecting]) ends by itself once nothing is selected (the last item tapped off, a drag that
 * deselected everything, or the selected items disappearing), as well as with the close button or Back. During a
 * drag it stays on even if the range briefly covers nothing, so the drag isn't cut short.
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
        if (ids.isEmpty() && anchorIndex < 0) isSelecting = false
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
        if (ids.isEmpty()) isSelecting = false
    }

    // What was picked in a day / month (keyed by its first id) just before its header check selected all of it, so the
    // next tap goes back to that instead of clearing the whole group.
    private val beforeGroup = HashMap<Long, Set<Long>>()

    /**
     * Header check of a day or month: selects all of [groupIds]; when all are selected, goes back to what was picked in
     * the group before that (nothing, if the whole group was selected another way).
     */
    fun toggleGroup(groupIds: List<Long>) {
        val key = groupIds.firstOrNull() ?: return
        isSelecting = true
        val remembered = if (groupSelection(groupIds, ids) == GroupSelection.All) {
            beforeGroup.remove(key)
        } else {
            beforeGroup[key] = groupIds.filterTo(HashSet()) { it in ids }
            null
        }
        ids = toggleGroup(groupIds, ids, remembered)
        if (ids.isEmpty() && anchorIndex < 0) isSelecting = false
    }

    fun selectAll(items: List<MediaItem>) {
        isSelecting = true
        ids = items.mapTo(HashSet(items.size)) { it.id }
    }

    /** Drops selected ids that are not in [present] (items removed elsewhere). */
    fun retainOnly(present: Set<Long>) {
        if (ids.all { it in present }) return
        ids = ids.filterTo(HashSet()) { it in present }
        if (ids.isEmpty() && anchorIndex < 0) isSelecting = false
    }

    fun clear() {
        beforeGroup.clear()
        isSelecting = false
        ids = emptySet()
        anchorIndex = -1
    }
}

/** How much of a group of items (a day or month in the timeline) is selected. */
enum class GroupSelection { None, Some, All }

/** Whether none, some or all of [groupIds] are in [selected]. An empty group counts as [GroupSelection.None]. */
fun groupSelection(groupIds: List<Long>, selected: Set<Long>): GroupSelection {
    if (groupIds.isEmpty() || selected.isEmpty()) return GroupSelection.None
    var hit = 0
    for (id in groupIds) if (id in selected) hit++
    return when (hit) {
        0 -> GroupSelection.None
        groupIds.size -> GroupSelection.All
        else -> GroupSelection.Some
    }
}

/**
 * The selection after tapping a group's header check: when all of it is selected, the group goes back to [before] (what
 * was picked in it before it was selected whole; nothing if unknown); otherwise the rest of the group is selected.
 */
fun toggleGroup(groupIds: List<Long>, selected: Set<Long>, before: Set<Long>? = null): Set<Long> =
    if (groupSelection(groupIds, selected) == GroupSelection.All) {
        selected - groupIds.toSet() + before.orEmpty()
    } else {
        selected + groupIds
    }
