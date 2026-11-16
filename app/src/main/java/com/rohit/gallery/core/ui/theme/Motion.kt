package com.rohit.gallery.core.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/** Shared spring specs so motion feels consistent across the app. */
object GalleryMotion {
    fun <T> standard() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    fun <T> snappy() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
}
