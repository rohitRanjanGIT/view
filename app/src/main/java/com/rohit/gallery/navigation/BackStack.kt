package com.rohit.gallery.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Length of the back animation. A back swipe seeks through it by the finger (and after release by a spring, see
 * swipeBack), so this only sets the speed of a back without a swipe (the system gesture's own finish).
 */
internal const val BACK_MS = 320

/** How dark the screen underneath is at the start of a back swipe; it brightens as the top one slides off. */
private const val UNDER_DIM = 0.18f
private val ShadowWidth = 18.dp

/**
 * The iOS-style stack look of a back swipe (see the predictive transitions in [GalleryNavHost]): the leaving screen
 * slides off at full size with a soft shadow along its edge, over the screen underneath, which is dimmed at first and
 * brightens as it is uncovered. Both are drawn by the leaving screen, just outside its own edges (the screen is on
 * top while it leaves), so the screen underneath needs nothing. Off-screen sides draw nothing visible. Read in the
 * draw phase only, so the swipe never recomposes either screen.
 */
@Composable
fun Modifier.backStack(scope: AnimatedVisibilityScope): Modifier {
    // 0 at rest, 1 fully gone: in step with the slide, which uses the same linear timing.
    val gone = scope.transition.animateFloat(
        transitionSpec = { tween(BACK_MS, easing = LinearEasing) },
        label = "backStack",
    ) { state -> if (state == EnterExitState.PostExit) 1f else 0f }
    return drawWithContent {
        val p = gone.value
        if (p > 0f && p < 1f) {
            val dim = Color.Black.copy(alpha = UNDER_DIM * (1f - p))
            val shadowPx = ShadowWidth.toPx()
            // Appears at once when the swipe starts and stays while the screen moves.
            val shadowAlpha = 0.22f * (p * 20f).coerceAtMost(1f)
            // Left of the screen (it slides right) and right of it (slid left by a swipe from the right edge).
            drawRect(dim, topLeft = Offset(-size.width, 0f), size = size)
            drawRect(dim, topLeft = Offset(size.width, 0f), size = size)
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = shadowAlpha)), startX = -shadowPx, endX = 0f),
                topLeft = Offset(-shadowPx, 0f),
                size = Size(shadowPx, size.height),
            )
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = shadowAlpha), Color.Transparent), startX = size.width, endX = size.width + shadowPx),
                topLeft = Offset(size.width, 0f),
                size = Size(shadowPx, size.height),
            )
        }
        drawContent()
    }
}
