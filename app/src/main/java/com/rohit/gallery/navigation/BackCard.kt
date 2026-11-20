package com.rohit.gallery.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Length of the back animation. A back swipe seeks through it, so this mostly sets how fast it finishes on release. */
internal const val BACK_MS = 320

/**
 * The card "pop" of a back swipe happens early: the screen reaches its card size (and corners) in the first third of
 * the swipe, then just slides with the finger.
 */
internal val BackCardEasing = Easing { t -> FastOutSlowInEasing.transform((t / 0.35f).coerceAtMost(1f)) }

private val CardCorner = 28.dp

/**
 * Rounds a screen's corners as it leaves, in step with the back swipe's shrink (see the predictive transitions in
 * [GalleryNavHost]): a square screen becomes a card that follows the finger. Read in the graphics layer, so the
 * animation never recomposes the screen.
 */
@Composable
fun Modifier.backCard(scope: AnimatedVisibilityScope): Modifier {
    val corner = scope.transition.animateDp(
        transitionSpec = { tween(BACK_MS, easing = BackCardEasing) },
        label = "backCardCorner",
    ) { state -> if (state == EnterExitState.PostExit) CardCorner else 0.dp }
    return graphicsLayer {
        val radius = corner.value
        clip = radius > 0.dp
        shape = RoundedCornerShape(radius)
    }
}
