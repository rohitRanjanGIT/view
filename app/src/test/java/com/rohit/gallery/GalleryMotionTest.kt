package com.rohit.gallery

import com.rohit.gallery.core.ui.theme.GalleryMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryMotionTest {
    @Test
    fun rubberBandResistsMoreTheFurtherItGoes() {
        val first = GalleryMotion.rubberBand(100f, 300f)
        val second = GalleryMotion.rubberBand(200f, 300f) - first
        assertTrue(first in 1f..100f)
        assertTrue(second < first)
        assertTrue(GalleryMotion.rubberBand(1_000_000f, 300f) < 300f)
    }

    @Test
    fun rubberBandKeepsDirection() {
        assertEquals(-GalleryMotion.rubberBand(80f, 300f), GalleryMotion.rubberBand(-80f, 300f), 0.001f)
        assertEquals(0f, GalleryMotion.rubberBand(0f, 300f), 0f)
    }

    @Test
    fun rubberBandInverseRoundTrips() {
        for (overshoot in listOf(-250f, -10f, 5f, 120f, 900f)) {
            val shown = GalleryMotion.rubberBand(overshoot, 300f)
            assertEquals(overshoot, GalleryMotion.rubberBandInverse(shown, 300f), 0.01f)
        }
    }

    @Test
    fun projectionMatchesApplesFormula() {
        // 1000 px/s at a scroll's 0.998 deceleration comes to rest 499 px further on.
        assertEquals(499f, GalleryMotion.project(1000f), 0.5f)
        assertEquals(-499f, GalleryMotion.project(-1000f), 0.5f)
    }
}
