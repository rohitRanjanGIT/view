package com.rohit.gallery

import com.rohit.gallery.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionItemTest {
    private fun item(mime: String = "image/jpeg", name: String = "", special: Int = 0, type: MediaType = MediaType.Image) =
        testItem(1L, type = type).copy(mimeType = mime, displayName = name, specialFormat = special)

    @Test fun gifIsMotion() = assertTrue(item(mime = "image/gif").isMotionOrGif)

    @Test fun flaggedMotionPhotoAndAnimatedWebpAreMotion() {
        assertTrue(item(special = 2).isMotionOrGif)
        assertTrue(item(mime = "image/webp", special = 3).isMotionOrGif)
    }

    @Test fun googleCameraNamesAreMotion() {
        assertTrue(item(name = "MVIMG_20200101_101500.jpg").isMotionOrGif)
        assertTrue(item(name = "PXL_20240101_101500123.MP.jpg").isMotionOrGif)
    }

    @Test fun plainPhotosAndVideosAreNot() {
        assertFalse(item(name = "IMG_20240101.jpg").isMotionOrGif)
        assertFalse(item(name = "Impact.jpg").isMotionOrGif)
        assertFalse(item(mime = "video/mp4", type = MediaType.Video, special = 2).isMotionOrGif)
    }
}
