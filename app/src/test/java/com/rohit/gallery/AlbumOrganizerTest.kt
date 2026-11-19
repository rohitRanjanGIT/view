package com.rohit.gallery

import com.rohit.gallery.core.model.AlbumPref
import com.rohit.gallery.core.model.AlbumSort
import com.rohit.gallery.core.model.buildAlbums
import com.rohit.gallery.core.model.organizeAlbums
import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumOrganizerTest {

    // Newest photo first: Camera (3 items), Screenshots (1), WhatsApp (2), apps (1).
    private val albums = buildAlbums(
        listOf(
            testItem(1, dateTakenMs = 9_000, bucketId = 1, bucketName = "Camera"),
            testItem(2, dateTakenMs = 8_000, bucketId = 2, bucketName = "Screenshots"),
            testItem(3, dateTakenMs = 7_000, bucketId = 3, bucketName = "WhatsApp"),
            testItem(4, dateTakenMs = 6_000, bucketId = 1, bucketName = "Camera"),
            testItem(5, dateTakenMs = 5_000, bucketId = 4, bucketName = "apps"),
            testItem(6, dateTakenMs = 4_000, bucketId = 3, bucketName = "WhatsApp"),
            testItem(7, dateTakenMs = 3_000, bucketId = 1, bucketName = "Camera"),
        ),
    )

    private fun ids(prefs: Map<Long, AlbumPref>, sort: AlbumSort) =
        organizeAlbums(albums, prefs, sort).visible.map { it.album.bucketId }

    @Test
    fun `sorts unpinned albums`() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(emptyMap(), AlbumSort.Newest))
        assertEquals(listOf(4L, 1L, 2L, 3L), ids(emptyMap(), AlbumSort.Name)) // case-insensitive
        assertEquals(listOf(1L, 3L, 2L, 4L), ids(emptyMap(), AlbumSort.MostItems))
    }

    @Test
    fun `pins come first in the order they were pinned`() {
        val prefs = mapOf(4L to AlbumPref(pinnedAt = 100), 3L to AlbumPref(pinnedAt = 200))
        assertEquals(listOf(4L, 3L, 1L, 2L), ids(prefs, AlbumSort.Newest))
        assertEquals(listOf(4L, 3L, 1L, 2L), ids(prefs, AlbumSort.Name))
    }

    @Test
    fun `custom order puts never-placed albums first`() {
        val prefs = mapOf(1L to AlbumPref(position = 1), 2L to AlbumPref(position = 0))
        assertEquals(listOf(3L, 4L, 2L, 1L), ids(prefs, AlbumSort.Custom))
    }

}
