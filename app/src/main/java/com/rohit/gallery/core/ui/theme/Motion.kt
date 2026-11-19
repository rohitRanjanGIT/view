package com.rohit.gallery.core.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
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

    private val SmoothClose = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /**
     * Viewer -> grid (closing): slower than opening, with a gentle start and a soft landing, so the photo visibly
     * glides from where the finger let go into its cell. 250 ms (about 175 ms at the phone's 0.7 animation scale)
     * read as a jump.
     */
    fun sharedBoundsClosing() = tween<androidx.compose.ui.geometry.Rect>(SHARED_CLOSE_MS, easing = SmoothClose)

    const val SHARED_FLIGHT_MS = 180
    const val SHARED_CLOSE_MS = 380

    const val FADE_MS = 140
}
