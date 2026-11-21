package com.rohit.gallery

import com.rohit.gallery.core.domain.TransferResult
import com.rohit.gallery.core.domain.albumNameError
import com.rohit.gallery.core.domain.folderAccepts
import com.rohit.gallery.core.domain.folderLabel
import com.rohit.gallery.core.domain.normalizeRelativePath
import com.rohit.gallery.core.model.buildAlbums
import com.rohit.gallery.feature.selection.transferMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `a new album can't take an existing album's name, whatever the case`() {
        val existing = listOf("Camera", "Trip ")
        assertNotNull(albumNameError("trip", existing))
        assertNotNull(albumNameError(" CAMERA ", existing))
        assertNull(albumNameError("Trip 2", existing))
    }

    @Test
    fun `photos only go under DCIM or Pictures, videos also under Movies`() {
        assertTrue(folderAccepts("Pictures/Trip/", isVideo = false))
        assertTrue(folderAccepts("DCIM/Camera/", isVideo = false))
        assertTrue(folderAccepts("dcim/Screenshots/", isVideo = false))
        assertFalse(folderAccepts("Movies/Screen recordings/", isVideo = false))
        assertFalse(folderAccepts("Download/", isVideo = false))
        assertFalse(folderAccepts("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", isVideo = false))
        assertTrue(folderAccepts("Movies/Screen recordings/", isVideo = true))
        assertTrue(folderAccepts("Pictures/Trip/", isVideo = true))
        assertFalse(folderAccepts("Download/", isVideo = true))
    }

    @Test
    fun `folder labels name the last folder`() {
        assertEquals("Download", folderLabel("Download/"))
        assertEquals("WhatsApp Images", folderLabel("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"))
    }

    @Test
    fun `transfer messages say what was done and what was skipped`() {
        assertEquals("Moved 3 items", transferMessage("Moved", 3, TransferResult(3, 0), null))
        assertEquals("Moved 1 item", transferMessage("Moved", 1, TransferResult(1, 0), null))
        assertEquals("Moved 2, 1 failed", transferMessage("Moved", 3, TransferResult(2, 1), null))
        assertEquals(
            "Moved 2 items. 1 skipped: Android doesn't allow photos in Movies",
            transferMessage("Moved", 2, TransferResult(2, 0), "1 skipped: Android doesn't allow photos in Movies"),
        )
    }
}
