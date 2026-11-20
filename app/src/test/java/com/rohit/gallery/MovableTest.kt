package com.rohit.gallery

import com.rohit.gallery.core.domain.movable
import com.rohit.gallery.core.model.MediaType
import com.rohit.gallery.core.model.isAppFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovableTest {
    private val whatsApp = "WhatsApp Images"

    @Test
    fun `app folders are recognised by their path`() {
        assertTrue(isAppFolder("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"))
        assertTrue(isAppFolder("/android/media/org.telegram.messenger/Telegram/"))
        assertFalse(isAppFolder("Pictures/Screenshots/"))
        assertFalse(isAppFolder("DCIM/Camera/"))
        assertFalse(isAppFolder(null))
    }

    @Test
    fun `everything movable gives no note`() {
        val items = listOf(testItem(1), testItem(2))
        val (ok, note) = movable(items, "Pictures/Trip/")
        assertEquals(items, ok)
        assertNull(note)
    }

    @Test
    fun `app folder photos are left out with a pointer to albums`() {
        val wa = listOf(1L, 2L).map { testItem(it, bucketName = whatsApp).copy(isInAppFolder = true) }
        val plain = testItem(3)
        val (ok, note) = movable(wa + plain, "Pictures/Trip/")
        assertEquals(listOf(plain), ok)
        assertEquals("2 from WhatsApp Images can't be moved (Android keeps them there), add them to an album instead", note)
    }

    @Test
    fun `only app folder photos leaves nothing to move`() {
        val wa = testItem(1, bucketName = whatsApp).copy(isInAppFolder = true)
        val (ok, note) = movable(listOf(wa), "Pictures/Trip/")
        assertTrue(ok.isEmpty())
        assertEquals("1 from WhatsApp Images can't be moved (Android keeps them there), add it to an album instead", note)
    }

    @Test
    fun `both reasons are reported together`() {
        val wa = testItem(1, bucketName = whatsApp).copy(isInAppFolder = true)
        val photo = testItem(2)
        val video = testItem(3, type = MediaType.Video)
        val (ok, note) = movable(listOf(wa, photo, video), "Movies/Clips/")
        assertEquals(listOf(video), ok)
        assertEquals(
            "1 from WhatsApp Images can't be moved (Android keeps them there), add it to an album instead. " +
                "1 skipped: Android doesn't allow photos in Clips",
            note,
        )
    }
}
