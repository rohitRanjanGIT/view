package com.rohit.gallery

import com.rohit.gallery.core.data.db.TrashSnapshotEntity
import com.rohit.gallery.core.domain.Destination
import com.rohit.gallery.core.domain.findRepairs
import com.rohit.gallery.core.model.MediaType
import com.rohit.gallery.feature.albums.planRemoval
import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumRemoveAndRestoreTest {
    private fun snap(id: Long, date: Long, favorite: Boolean = false, savedAt: Long = 0L) =
        TrashSnapshotEntity(id, isVideo = false, shownDateMs = date, isFavorite = favorite, savedAt = savedAt)

    @Test
    fun `a restored photo with another date or favourite mark needs repair`() {
        val items = listOf(
            testItem(1, dateTakenMs = 999), // date reset on restore
            testItem(2, dateTakenMs = 200), // unchanged
            testItem(3, dateTakenMs = 300), // favourite lost
            testItem(4, dateTakenMs = 400), // no snapshot
        )
        val plan = findRepairs(items, listOf(snap(1, 100), snap(2, 200), snap(3, 300, favorite = true)), now = 1_000, settleAfterMs = 10_000)
        assertEquals(listOf(1L, 3L), plan.repairs.map { it.item.id })
        // Snapshot 2 is recent: the trash prompt may still be open, so it is kept for now.
        assertEquals(emptyList(), plan.settled)
    }

    @Test
    fun `unchanged photos drop their snapshot once it is old, still-trashed ones keep it`() {
        val plan = findRepairs(listOf(testItem(2, dateTakenMs = 200)), listOf(snap(2, 200), snap(5, 500)), now = 100_000, settleAfterMs = 10_000)
        assertEquals(emptyList(), plan.repairs)
        assertEquals(listOf(2L), plan.settled)
    }

    @Test
    fun `a photo without a date taken is checked by the file date it showed`() {
        val moved = testItem(1, dateTakenMs = 0, dateModifiedMs = 9_000) // the file date changed on restore
        val same = testItem(2, dateTakenMs = 0, dateModifiedMs = 5_000)
        val plan = findRepairs(listOf(moved, same), listOf(snap(1, 5_000), snap(2, 5_000)), now = 0, settleAfterMs = 10_000)
        assertEquals(listOf(1L), plan.repairs.map { it.item.id })
    }

    @Test
    fun `removing an album sends photos back where they came from`() {
        val items = listOf(
            testItem(1, bucketId = 25),
            testItem(2, bucketId = 25), // copied in: no origin
            testItem(3, bucketId = 25), // came from the other album being removed
            testItem(4, bucketId = 25, type = MediaType.Video),
            testItem(5, bucketId = 25), // origin Android won't take a photo in
        )
        val origins = mapOf(
            1L to "DCIM/Camera/",
            3L to "Pictures/Album 26",
            4L to "Movies/Clips/",
            5L to "Download/",
        )
        val plan = planRemoval(items, origins, removedFolders = setOf("Pictures/Album 25/", "pictures/album 26/"))
        assertEquals(listOf(1L to Destination("DCIM/Camera/"), 4L to Destination("Movies/Clips/")), plan.back.map { it.first.id to it.second })
        assertEquals(listOf(2L, 3L, 5L), plan.others.map { it.id })
    }
}
