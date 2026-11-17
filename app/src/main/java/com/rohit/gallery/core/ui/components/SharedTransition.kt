package com.rohit.gallery.core.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
