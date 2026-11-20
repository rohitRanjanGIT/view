package com.rohit.gallery

import com.rohit.gallery.core.domain.commonMimeType
import com.rohit.gallery.core.model.MediaType
import com.rohit.gallery.feature.selection.SelectionState
import com.rohit.gallery.feature.selection.GroupSelection
import com.rohit.gallery.feature.selection.groupSelection
import com.rohit.gallery.feature.selection.toggleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionStateTest {
    // Newest first, like the real index: ids 10, 9, ... 1 at indexes 0..9.
    private val items = (10L downTo 1L).map { testItem(it) }

    @Test
    fun `deselecting the last item ends selection mode`() {
        val s = SelectionState()
        s.toggle(5)
        s.toggle(6)
        assertTrue(s.isSelecting)
        s.toggle(5)
        assertEquals(setOf(6L), s.ids)
        assertTrue(s.isSelecting)
        s.toggle(6)
        assertEquals(emptySet(), s.ids)
        assertFalse(s.isSelecting)
    }

    @Test
    fun `a drag that deselects everything ends selection mode when the finger lifts, not before`() {
        val s = SelectionState()
        s.toggle(items[3].id)
        // A drag that starts on the only selected item removes its range, so nothing is left selected.
        s.startDrag(index = 3, id = items[3].id)
        assertEquals(emptySet(), s.ids)
        assertTrue(s.isSelecting)
        s.dragTo(4, items)
        assertTrue(s.isSelecting)
        s.endDrag()
        assertFalse(s.isSelecting)
    }

    @Test
    fun `selection ends when the selected items disappear`() {
        val s = SelectionState()
        s.toggle(5)
        s.retainOnly(setOf(1L, 2L))
        assertFalse(s.isSelecting)
    }

    @Test
    fun `drag selects the range between anchor and finger in either direction`() {
        val s = SelectionState()
        s.startDrag(index = 3, id = items[3].id)
        s.dragTo(6, items)
        assertEquals(setOf(7L, 6L, 5L, 4L), s.ids)
        s.dragTo(1, items)
        assertEquals(setOf(9L, 8L, 7L), s.ids)
    }

    @Test
    fun `shrinking the drag removes items again`() {
        val s = SelectionState()
        s.startDrag(2, items[2].id)
        s.dragTo(5, items)
        s.dragTo(3, items)
        assertEquals(setOf(8L, 7L), s.ids)
    }

    @Test
    fun `drag keeps the earlier selection`() {
        val s = SelectionState()
        s.toggle(1)
        s.startDrag(0, items[0].id)
        s.dragTo(2, items)
        assertEquals(setOf(1L, 10L, 9L, 8L), s.ids)
    }

    @Test
    fun `dragging from an already selected item deselects the range`() {
        val s = SelectionState()
        s.selectAll(items)
        s.startDrag(0, items[0].id)
        s.dragTo(2, items)
        assertEquals(7, s.ids.size)
        assertFalse(10L in s.ids || 9L in s.ids || 8L in s.ids)
    }

    @Test
    fun `clear leaves selection mode`() {
        val s = SelectionState()
        s.selectAll(items)
        s.clear()
        assertFalse(s.isSelecting)
        assertEquals(emptySet(), s.ids)
    }

    @Test
    fun `share mime type`() {
        val photo = testItem(1)
        val video = testItem(2, type = MediaType.Video)
        assertEquals("image/jpeg", commonMimeType(listOf(photo)))
        assertEquals("image/*", commonMimeType(listOf(photo, testItem(3))))
        assertEquals("video/*", commonMimeType(listOf(video, testItem(4, type = MediaType.Video))))
        assertEquals("*/*", commonMimeType(listOf(photo, video)))
    }
}

class GroupSelectionTest {
    private val day = listOf(1L, 2L, 3L)

    @Test
    fun `none some and all`() {
        assertEquals(GroupSelection.None, groupSelection(day, emptySet()))
        assertEquals(GroupSelection.None, groupSelection(day, setOf(9L)))
        assertEquals(GroupSelection.Some, groupSelection(day, setOf(2L, 9L)))
        assertEquals(GroupSelection.All, groupSelection(day, setOf(1L, 2L, 3L, 9L)))
        assertEquals(GroupSelection.None, groupSelection(emptyList(), setOf(1L)))
    }

    @Test
    fun `toggle selects the rest unless all are selected`() {
        assertEquals(setOf(1L, 2L, 3L), toggleGroup(day, emptySet()))
        assertEquals(setOf(9L, 1L, 2L, 3L), toggleGroup(day, setOf(9L, 2L)))
        assertEquals(setOf(9L), toggleGroup(day, setOf(9L, 1L, 2L, 3L)))
    }

    @Test
    fun `state toggleGroup ends selection mode when it clears the last items`() {
        val s = SelectionState()
        s.toggleGroup(day)
        assertTrue(s.isSelecting)
        assertEquals(setOf(1L, 2L, 3L), s.ids)
        s.toggleGroup(day)
        assertFalse(s.isSelecting)
        assertTrue(s.ids.isEmpty())
    }

    @Test
    fun `second tap goes back to what was picked in the day before`() {
        val s = SelectionState()
        s.toggle(2L)
        s.toggle(9L)
        s.toggleGroup(day)
        assertEquals(setOf(1L, 2L, 3L, 9L), s.ids)
        s.toggleGroup(day)
        assertEquals(setOf(2L, 9L), s.ids)
        // Partial again: the next tap selects the whole day once more.
        s.toggleGroup(day)
        assertEquals(setOf(1L, 2L, 3L, 9L), s.ids)
    }

    @Test
    fun `a day selected whole another way clears on its check`() {
        val s = SelectionState()
        s.toggle(1L)
        s.toggle(2L)
        s.toggle(3L)
        s.toggleGroup(day)
        assertTrue(s.ids.isEmpty())
    }
}
