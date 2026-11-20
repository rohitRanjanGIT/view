package com.rohit.gallery

import com.rohit.gallery.core.model.Album
import com.rohit.gallery.feature.selection.createdFolderChoices
import kotlin.test.Test
import kotlin.test.assertEquals

class CreatedFoldersTest {
    private val camera = Album(1, "Camera", 10, null) to "DCIM/Camera/"
    private val trip = Album(2, "Trip", 3, null) to "Pictures/Trip/"
    private val party = Album(3, "Party", 4, null) to "Pictures/Party/"

    @Test
    fun `only folders made through the app are offered`() {
        val shown = createdFolderChoices(listOf(camera, trip, party), setOf("Pictures/Trip/"), currentBucket = null)
        assertEquals(listOf(trip.first), shown)
    }

    @Test
    fun `nothing created gives an empty list`() {
        assertEquals(emptyList(), createdFolderChoices(listOf(camera, trip), emptySet(), currentBucket = null))
    }

    @Test
    fun `the folder the selection is in is left out`() {
        val shown = createdFolderChoices(listOf(trip, party), setOf("Pictures/Trip/", "Pictures/Party/"), currentBucket = 2)
        assertEquals(listOf(party.first), shown)
    }

    @Test
    fun `paths match ignoring case and slashes`() {
        val shown = createdFolderChoices(listOf(trip), setOf("pictures/trip"), currentBucket = null)
        assertEquals(listOf(trip.first), shown)
    }

    @Test
    fun `created folders gone from the library and unreadable folders are skipped`() {
        val unreadable = Album(4, "Mystery", 1, null) to null
        val shown = createdFolderChoices(listOf(unreadable, party), setOf("Pictures/Trip/", "Pictures/Party/"), currentBucket = null)
        assertEquals(listOf(party.first), shown)
    }
}
