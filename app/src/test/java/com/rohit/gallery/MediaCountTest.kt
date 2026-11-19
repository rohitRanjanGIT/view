package com.rohit.gallery

import com.rohit.gallery.core.model.MediaType
import com.rohit.gallery.core.model.albumCountLabel
import com.rohit.gallery.core.model.mediaCountLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaCountTest {
    @Test
    fun `counts photos and videos, leaving out empty parts`() {
        assertEquals("", mediaCountLabel(emptyList()))
        assertEquals("1 photo", mediaCountLabel(listOf(testItem(1))))
        assertEquals("2 videos", mediaCountLabel(listOf(testItem(1, type = MediaType.Video), testItem(2, type = MediaType.Video))))
        assertEquals("2 photos · 1 video", mediaCountLabel(listOf(testItem(1), testItem(2), testItem(3, type = MediaType.Video))))
    }

    @Test
    fun `album count`() {
        assertEquals("1 album", albumCountLabel(1))
        assertEquals("7 albums", albumCountLabel(7))
    }
}
