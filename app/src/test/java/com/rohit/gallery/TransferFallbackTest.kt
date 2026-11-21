package com.rohit.gallery

import com.rohit.gallery.core.domain.moveSummary
import com.rohit.gallery.core.domain.streamsEqual
import com.rohit.gallery.core.domain.uniqueDisplayName
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferFallbackTest {
    @Test
    fun `a free name is kept`() {
        assertEquals("IMG_1.jpg", uniqueDisplayName("IMG_1.jpg", emptyList()))
        assertEquals("IMG_1.jpg", uniqueDisplayName("IMG_1.jpg", listOf("IMG_2.jpg", "other.png")))
    }

    @Test
    fun `a taken name gets the first free number before the extension`() {
        assertEquals("IMG_1 (1).jpg", uniqueDisplayName("IMG_1.jpg", listOf("IMG_1.jpg")))
        assertEquals("IMG_1 (2).jpg", uniqueDisplayName("IMG_1.jpg", listOf("IMG_1.jpg", "IMG_1 (1).jpg")))
    }

    @Test
    fun `names are compared ignoring case`() {
        assertEquals("Photo (1).JPG", uniqueDisplayName("Photo.JPG", listOf("photo.jpg")))
    }

    @Test
    fun `names without an extension or starting with a dot still work`() {
        assertEquals("notes (1)", uniqueDisplayName("notes", listOf("notes")))
        assertEquals(".hidden (1)", uniqueDisplayName(".hidden", listOf(".hidden")))
    }

    @Test
    fun `identical bytes verify`() {
        val data = ByteArray(300_000) { (it * 31).toByte() }
        assertTrue(streamsEqual(ByteArrayInputStream(data), ByteArrayInputStream(data.copyOf()), chunk = 4096))
        assertTrue(streamsEqual(ByteArrayInputStream(ByteArray(0)), ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun `one changed byte fails the check`() {
        val data = ByteArray(100_000) { it.toByte() }
        val bad = data.copyOf().also { it[99_999] = (it[99_999] + 1).toByte() }
        assertFalse(streamsEqual(ByteArrayInputStream(data), ByteArrayInputStream(bad), chunk = 4096))
    }

    @Test
    fun `a copy that is shorter or longer fails the check`() {
        val data = ByteArray(10_000) { it.toByte() }
        assertFalse(streamsEqual(ByteArrayInputStream(data), ByteArrayInputStream(data.copyOf(9_999)), chunk = 4096))
        assertFalse(streamsEqual(ByteArrayInputStream(data), ByteArrayInputStream(data + 0.toByte()), chunk = 4096))
        // Exactly one chunk shorter.
        assertFalse(streamsEqual(ByteArrayInputStream(data), ByteArrayInputStream(data.copyOf(8192)), chunk = 4096))
    }

    @Test
    fun `streams that hand out bytes in small pieces still compare by content`() {
        val data = ByteArray(50_000) { (it % 251).toByte() }
        // A source that returns at most 7 bytes per read must not look different from one that returns everything.
        val stingy = object : InputStream() {
            private val inner = ByteArrayInputStream(data)
            override fun read(): Int = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, minOf(len, 7))
        }
        assertTrue(streamsEqual(stingy, ByteArrayInputStream(data), chunk = 4096))
    }

    @Test
    fun `summary when everything moved`() {
        assertEquals("Moved 5 items", moveSummary("Moved", 5, moved = 5, failed = 0, declined = 0, note = null))
        assertEquals("Moved 1 item", moveSummary("Moved", 1, moved = 1, failed = 0, declined = 0, note = null))
    }

    @Test
    fun `summary with failures`() {
        assertEquals("Moved 4, 1 failed", moveSummary("Moved", 5, moved = 4, failed = 1, declined = 0, note = null))
        assertEquals("Couldn't complete that", moveSummary("Moved", 2, moved = 0, failed = 2, declined = 0, note = null))
    }

    @Test
    fun `summary when the delete was declined says not moved`() {
        assertEquals(
            "Not moved: the delete wasn't allowed, so nothing changed",
            moveSummary("Moved", 3, moved = 0, failed = 0, declined = 3, note = null),
        )
        assertEquals(
            "Moved 2, 3 not moved (delete wasn't allowed)",
            moveSummary("Moved", 5, moved = 2, failed = 0, declined = 3, note = null),
        )
        assertEquals(
            "Moved 2, 2 not moved (delete wasn't allowed), 1 failed. 1 skipped: Android doesn't allow photos in Movies",
            moveSummary("Moved", 5, moved = 2, failed = 1, declined = 2, note = "1 skipped: Android doesn't allow photos in Movies"),
        )
    }
}
