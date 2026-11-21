package com.rohit.gallery.feature.viewer

import androidx.media3.common.Player

/**
 * Whether a video counts as "playing" when a film strip scrub starts, so the scrub leaves it playing.
 *
 * Decided on [playWhenReady] (the intent to play), not `isPlaying`: a seek on a playing player makes
 * `isPlaying` drop to false while it buffers. A video that already reached its end is treated as paused:
 * seeking away from the end with the intent still set would start it by itself.
 */
internal fun scrubKeepsPlaying(playWhenReady: Boolean, playbackState: Int): Boolean =
    playWhenReady && playbackState != Player.STATE_ENDED
