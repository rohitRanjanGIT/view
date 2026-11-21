package com.rohit.gallery

import com.rohit.gallery.core.domain.movable
import com.rohit.gallery.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovableTest {
    @Test
    fun `everything movable gives no note`() {
        val items = listOf(testItem(1), testItem(2))
        val (ok, note) = movable(items, "Pictures/Trip/")
        assertEquals(items, ok)
        assertNull(note)
    }

    @Test
    fun `photos from another app's folder are not left out any more`() {
        // They move by copy, verify, delete: only the destination decides what is refused.
        val whatsApp = listOf(1L, 2L).map { testItem(it, bucketName = "WhatsApp Images") }
        val (ok, note) = movable(whatsApp, "Pictures/Trip/")
        assertEquals(whatsApp, ok)
        assertNull(note)
    }

    @Test
    fun `a folder that refuses the type leaves those out with a note`() {
        val photo = testItem(1)
        val video = testItem(2, type = MediaType.Video)
        val (ok, note) = movable(listOf(photo, video), "Movies/Clips/")
        assertEquals(listOf(video), ok)
        assertEquals("1 skipped: Android doesn't allow photos in Clips", note)
    }

    @Test
    fun `nothing fits a folder that takes neither`() {
        val (ok, note) = movable(listOf(testItem(1), testItem(2, type = MediaType.Video)), "Download/")
        assertTrue(ok.isEmpty())
        assertEquals("2 skipped: Android doesn't allow photos or videos in Download", note)
    }
}
