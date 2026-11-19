package com.rohit.gallery.core.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import com.rohit.gallery.core.ui.theme.GalleryMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * The photo a grid screen's viewer shows, for the grid underneath it. The viewer is drawn as a layer over the grid
 * (the grid stays composed, so closing never has to rebuild it); this is how the two find each other.
 */
interface SharedPhotoState {
    /** The photo the viewer is showing or flying back with; null when there is no viewer. */
    val activeId: Long?

    /** True while the viewer is open (not while it closes): the grid then leaves a gap where [activeId] sits. */
    val viewerShowing: Boolean

    /**
     * True once the open flight has landed, until the viewer starts closing. The grid cell is not a shared element
     * meanwhile: with both sides matched, the library keeps the photo's bounds from the end of the open flight and
     * ignores the drag-down, so the close flight jumped back to full screen before gliding. Re-attached on close,
     * the cell makes a fresh match that starts from where the finger let go.
     */
    val viewerSettled: Boolean

    /** How fast (px/s) the finger was moving when a swipe-down closed the viewer; the close flight starts at it. */
    val closeVelocity: Offset get() = Offset.Zero
}

/** The viewer state of the screen this grid is on; null where no viewer can open (or the viewer itself). */
val LocalSharedPhoto = staticCompositionLocalOf<SharedPhotoState?> { null }

/**
 * Marks this composable as the shared element for [mediaId] while a viewer shows it. On the grid side ([viewerSide]
 * false) only the cell of the viewer's photo carries the modifier, so the other ~40 visible cells stay as cheap as
 * before; that cell is visible while the viewer is not, and the photo flies between the two when this flips.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.mediaSharedElement(mediaId: Long, viewerSide: Boolean = false): Modifier {
    val photo = LocalSharedPhoto.current ?: return this
    val transitionScope = LocalSharedTransitionScope.current ?: return this
    // Only the matching cell recomposes when the viewer moves to another photo.
    val shared by remember(photo, mediaId) { derivedStateOf { photo.activeId == mediaId } }
    if (!viewerSide && !shared) return this
    val visible = if (viewerSide) photo.viewerShowing else !photo.viewerShowing
    val base = if (viewerSide) this else this.graphicsLayer { alpha = if (photo.viewerShowing) 0f else 1f }
    if (!viewerSide && photo.viewerSettled) return base
    return with(transitionScope) {
        base.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = "media-$mediaId"),
            visible = visible,
            // Shrinking (viewer -> grid) is the close; it glides a little longer than the open.
            boundsTransform = { initial, target ->
                if (initial.width > target.width) GalleryMotion.sharedBoundsClosing(photo.closeVelocity) else GalleryMotion.sharedBounds()
            },
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
    return remember(scope) { { scope?.isTransitionActive == true } }
}

/** How long the viewer must rest on a photo before the grid underneath scrolls to it. */
private const val FOLLOW_DELAY_MS = 350L

/**
 * Keeps the viewer's photo in view in the grid underneath, so a close always has a cell to fly into and a drag-down
 * shows the right part of the grid. [scrollTo] should scroll only when the photo is not already fully visible.
 * Waits until the viewer rests on a photo (paging stays smooth); on close it runs at once.
 */
@Composable
fun FollowViewerPhoto(scrollTo: suspend (mediaId: Long) -> Unit) {
    val photo = LocalSharedPhoto.current ?: return
    val latest by rememberUpdatedState(scrollTo)
    LaunchedEffect(photo) {
        snapshotFlow { photo.activeId to photo.viewerShowing }.collectLatest { (id, showing) ->
            if (id == null) return@collectLatest
            if (showing) delay(FOLLOW_DELAY_MS)
            latest(id)
        }
    }
}
