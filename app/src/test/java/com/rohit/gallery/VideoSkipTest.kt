package com.rohit.gallery

import com.rohit.gallery.feature.viewer.SKIP_RUN_WINDOW_MS
import com.rohit.gallery.feature.viewer.SkipRun
import com.rohit.gallery.feature.viewer.TapSide
import com.rohit.gallery.feature.viewer.skipTarget
import com.rohit.gallery.feature.viewer.tapSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSkipTest {
    @Test
    fun thirdsOfTheWidthDecideTheSide() {
        assertEquals(TapSide.Back, tapSide(0f, 900f))
        assertEquals(TapSide.Back, tapSide(299f, 900f))
        assertEquals(TapSide.Middle, tapSide(300f, 900f))
        assertEquals(TapSide.Middle, tapSide(600f, 900f))
        assertEquals(TapSide.Forward, tapSide(601f, 900f))
        assertEquals(TapSide.Forward, tapSide(900f, 900f))
    }

    @Test
    fun unknownWidthNeverSkips() {
        assertEquals(TapSide.Middle, tapSide(10f, 0f))
    }

    @Test
    fun targetIsClampedToTheVideo() {
        assertEquals(15_000L, skipTarget(5_000L, 10_000L, 60_000L))
        assertEquals(0L, skipTarget(4_000L, -10_000L, 60_000L))
        assertEquals(60_000L, skipTarget(55_000L, 10_000L, 60_000L))
    }

    @Test
    fun unknownDurationOnlyClampsTheStart() {
        assertEquals(25_000L, skipTarget(15_000L, 10_000L, 0L))
        assertEquals(0L, skipTarget(3_000L, -10_000L, 0L))
    }

    @Test
    fun tapsOnTheSameSideWithinTheWindowAddUp() {
        val run = SkipRun()
        run.start(TapSide.Forward, 1_000L)
        assertEquals(1, run.steps)
        assertTrue(run.continuesWith(TapSide.Forward, 1_000L + SKIP_RUN_WINDOW_MS))
        run.step(1_500L)
        run.step(2_100L)
        assertEquals(3, run.steps)
        // The window counts from the last step, not from the double-tap.
        assertTrue(run.continuesWith(TapSide.Forward, 2_100L + SKIP_RUN_WINDOW_MS))
    }

    @Test
    fun waitingOrTheOtherSideEndsTheRun() {
        val run = SkipRun()
        run.start(TapSide.Back, 1_000L)
        assertFalse(run.continuesWith(TapSide.Back, 1_000L + SKIP_RUN_WINDOW_MS + 1))
        assertFalse(run.continuesWith(TapSide.Forward, 1_100L))
        assertFalse(run.continuesWith(TapSide.Middle, 1_100L))
    }

    @Test
    fun noRunMeansNoFollowUps() {
        val run = SkipRun()
        assertFalse(run.continuesWith(TapSide.Forward, 0L))
        run.start(TapSide.Forward, 0L)
        run.end()
        assertNull(run.side)
        assertFalse(run.continuesWith(TapSide.Forward, 10L))
    }

    @Test
    fun aNewDoubleTapStartsOverAtOneStep() {
        val run = SkipRun()
        run.start(TapSide.Forward, 0L)
        run.step(300L)
        run.start(TapSide.Back, 5_000L)
        assertEquals(TapSide.Back, run.side)
        assertEquals(1, run.steps)
    }

    @Test(expected = IllegalArgumentException::class)
    fun theMiddleNeverStartsARun() {
        SkipRun().start(TapSide.Middle, 0L)
    }
}
