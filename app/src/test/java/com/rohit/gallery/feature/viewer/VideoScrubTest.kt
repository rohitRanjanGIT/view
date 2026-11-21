package com.rohit.gallery.feature.viewer

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoScrubTest {
    @Test
    fun playingVideoKeepsPlaying() {
        assertTrue(scrubKeepsPlaying(playWhenReady = true, playbackState = Player.STATE_READY))
    }

    @Test
    fun bufferingAfterASeekStillCountsAsPlaying() {
        assertTrue(scrubKeepsPlaying(playWhenReady = true, playbackState = Player.STATE_BUFFERING))
    }

    @Test
    fun pausedVideoStaysPaused() {
        assertFalse(scrubKeepsPlaying(playWhenReady = false, playbackState = Player.STATE_READY))
        assertFalse(scrubKeepsPlaying(playWhenReady = false, playbackState = Player.STATE_BUFFERING))
    }

    @Test
    fun endedVideoIsNotRestartedByScrubbing() {
        assertFalse(scrubKeepsPlaying(playWhenReady = true, playbackState = Player.STATE_ENDED))
    }
}
