package com.rohit.gallery.core.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** Shared spring specs so motion feels consistent across the app. */
object GalleryMotion {
    fun <T> standard() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    fun <T> snappy() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    /**
     * Grid <-> viewer flight: a fixed ease-in-out matched to Google Photos (~7 frames at 10x animator scale,
     * i.e. ~170 ms, measured on the phone). A stiff spring read as the photo vanishing (it had no visible
     * path); a soft one (~300 ms) crept slowly into the cell at the end.
     */
    fun sharedBounds() = tween<androidx.compose.ui.geometry.Rect>(SHARED_FLIGHT_MS, easing = FastOutSlowInEasing)

    /**
     * Viewer -> grid (closing): a little longer and decelerating, so the photo visibly glides from where the finger
     * let go into its cell instead of seeming to vanish. A tween, not a spring, so it has no slow creeping tail.
     */
    fun sharedBoundsClosing() = tween<androidx.compose.ui.geometry.Rect>(SHARED_CLOSE_MS, easing = LinearOutSlowInEasing)

    const val SHARED_FLIGHT_MS = 180
    const val SHARED_CLOSE_MS = 250

    const val FADE_MS = 140
}
