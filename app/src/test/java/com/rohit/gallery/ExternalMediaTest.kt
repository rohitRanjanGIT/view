package com.rohit.gallery

import com.rohit.gallery.core.data.mediastore.externalMediaId
import com.rohit.gallery.core.data.mediastore.isVisualMime
import com.rohit.gallery.core.data.mediastore.mediaStoreIdOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalMediaTest {

    private fun idOf(uri: String): Long? {
        val parsed = java.net.URI(uri)
        val segments = parsed.path.orEmpty().split('/').filter { it.isNotEmpty() }
        return mediaStoreIdOf(parsed.scheme, parsed.authority, segments)
    }

    @Test
    fun mediaStoreImageVideoAndFileUrisGiveTheirId() {
        assertEquals(42L, idOf("content://media/external/images/media/42"))
        assertEquals(7L, idOf("content://media/external_primary/video/media/7"))
        assertEquals(9L, idOf("content://media/external/file/9"))
    }

    @Test
    fun otherUrisAreNotMediaStoreItems() {
        assertNull(idOf("content://com.example.camera.fileprovider/images/42"))
        assertNull(idOf("file:///sdcard/DCIM/Camera/IMG_1.jpg"))
        assertNull(idOf("content://media/external/images/media"))
        assertNull(idOf("content://media/external/audio/media/5"))
        assertNull(idOf("content://media/external/images/media/abc"))
    }

    @Test
    fun externalIdsAreNegativeAndDifferPerUri() {
        val a = externalMediaId("content://a/1")
        val b = externalMediaId("content://a/2")
        assertTrue(a < 0 && b < 0)
        assertNotEquals(a, b)
        assertEquals(a, externalMediaId("content://a/1"))
    }

    @Test
    fun onlyImageAndVideoMimesAreVisual() {
        assertTrue(isVisualMime("image/jpeg"))
        assertTrue(isVisualMime("video/*"))
        assertFalse(isVisualMime("audio/mpeg"))
        assertFalse(isVisualMime(null))
    }
}
