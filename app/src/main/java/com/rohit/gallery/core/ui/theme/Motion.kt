package com.rohit.gallery.core.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/** Shared spring specs so motion feels consistent across the app. */
object GalleryMotion {
    fun <T> standard() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    fun <T> snappy() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    /**
     * Grid <-> viewer flight: no overshoot, ~300 ms so the photo is seen travelling into its cell
     * (at 1200 stiffness it settled in ~130 ms and read as the photo vanishing).
     */
    fun sharedBounds() = spring<androidx.compose.ui.geometry.Rect>(dampingRatio = 0.95f, stiffness = 450f)

    const val FADE_MS = 140
}
