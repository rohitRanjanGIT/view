package com.rohit.gallery.core.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.rohit.gallery.core.ui.theme.GalleryMotion

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * The media id currently taking part in the grid <-> viewer shared-element transition.
 * Only that one cell carries the shared-element modifier, so the other ~40 visible cells
 * stay as cheap as before.
 */
val LocalSharedMediaId = compositionLocalOf<Long?> { null }

/**
 * The last item the viewer showed, for the grid that reappears when it closes: that grid scrolls the
 * item into view before its first frame, so you land on the photo you ended on (and the close flight
 * has a cell to fly to). A plain holder, not state: written by the viewer, taken once by the grid.
 */
class ViewerReturnTarget {
    var mediaId: Long? = null

    /** Returns the id and clears it, so only the first grid composed after the viewer uses it. */
    fun take(): Long? = mediaId.also { mediaId = null }
}

val LocalViewerReturnTarget = staticCompositionLocalOf { ViewerReturnTarget() }

/** Marks this composable as the shared element for [mediaId] if a transition is in progress for it. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.mediaSharedElement(mediaId: Long, force: Boolean = false): Modifier {
    if (!force && LocalSharedMediaId.current != mediaId) return this
    val transitionScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalAnimatedVisibilityScope.current ?: return this
    return with(transitionScope) {
        this@mediaSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = "media-$mediaId"),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = { _, _ -> GalleryMotion.sharedBounds() },
        )
    }
}

/**
 * True while a shared-element transition is running. The viewer draws a plain, photo-shaped copy
 * of the image for the flight and hides its (full-screen, interactive) image meanwhile.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun rememberSharedTransitionActive(): () -> Boolean {
    val scope = LocalSharedTransitionScope.current
    return androidx.compose.runtime.remember(scope) { { scope?.isTransitionActive == true } }
}

/**
 * A picture of the screen taken as the viewer opens. The grid itself is not drawn while the viewer is open, so
 * this is what shows behind the photo when a back or drag-down gesture fades the viewer's black background.
 * Cleared once the viewer is gone.
 */
class ViewerBackdrop {
    var image by mutableStateOf<ImageBitmap?>(null)

    /** Where each photo visible in [image] sits on screen (window coordinates), taken together with it. */
    var cells: Map<Long, Rect> = emptyMap()

    /** Set by the grid on screen: reports where its visible photos are, for [cells]. */
    var locator: (() -> Map<Long, Rect>)? = null

    /** Takes the cell positions now; call right before capturing [image]. */
    fun takeCells() {
        cells = locator?.invoke().orEmpty()
    }
}

/**
 * Registers [visibleCells] as the screen's cell locator while this grid is composed, so the viewer can leave a gap
 * where the photo it shows sits in the grid behind it.
 */
@Composable
fun RegisterViewerCells(visibleCells: () -> Map<Long, Rect>) {
    val backdrop = LocalViewerBackdrop.current ?: return
    val latest = androidx.compose.runtime.rememberUpdatedState(visibleCells)
    androidx.compose.runtime.DisposableEffect(backdrop) {
        val locator: () -> Map<Long, Rect> = { latest.value() }
        backdrop.locator = locator
        onDispose { if (backdrop.locator === locator) backdrop.locator = null }
    }
}

val LocalViewerBackdrop = staticCompositionLocalOf<ViewerBackdrop?> { null }
