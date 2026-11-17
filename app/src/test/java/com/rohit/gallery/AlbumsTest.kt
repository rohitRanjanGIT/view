package com.rohit.gallery

import com.rohit.gallery.core.domain.albumNameError
import com.rohit.gallery.core.domain.normalizeRelativePath
import com.rohit.gallery.core.model.buildAlbums
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AlbumsTest {
    @Test
    fun `albums are counted and ordered by newest item`() {
        val items = listOf(
            testItem(5, bucketId = 2, bucketName = "Trip"),
            testItem(4, bucketId = 1, bucketName = "Camera"),
            testItem(3, bucketId = 2, bucketName = "Trip"),
            testItem(2, bucketId = 1, bucketName = "Camera"),
            testItem(1, bucketId = 1, bucketName = "Camera"),
        )
        val albums = buildAlbums(items)
        assertEquals(listOf("Trip", "Camera"), albums.map { it.name })
        assertEquals(listOf(2, 3), albums.map { it.count })
        assertEquals(5L, albums[0].cover.id)
    }

    @Test
    fun `relative paths end with one slash and never start with one`() {
        assertEquals("Pictures/Trip/", normalizeRelativePath("/Pictures/Trip"))
        assertEquals("Pictures/Trip/", normalizeRelativePath("Pictures/Trip//"))
        assertEquals("", normalizeRelativePath("/"))
    }

    @Test
    fun `album names are validated`() {
        assertNull(albumNameError("Holiday 2026"))
        assertNotNull(albumNameError("  "))
        assertNotNull(albumNameError("a/b"))
        assertNotNull(albumNameError(".hidden"))
        assertNotNull(albumNameError("x".repeat(61)))
    }
}
