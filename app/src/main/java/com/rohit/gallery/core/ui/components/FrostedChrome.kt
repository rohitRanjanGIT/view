package com.rohit.gallery.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.rememberHazeState

/**
 * Frosted bars (Apple's translucent "materials"): a bar floats over the content, which keeps scrolling underneath
 * and shows through blurred, instead of the bar taking a solid strip of the screen.
 *
 * A screen with such bars calls [rememberChromeHaze], marks its scrolling content with `Modifier.hazeSource(state)`
 * and provides the state as [LocalChromeHaze]; bars inside then use [frostedChrome]. A bar with nothing provided
 * (or over nothing) looks like the plain `surfaceContainer` bar it replaced.
 */
val LocalChromeHaze = staticCompositionLocalOf<HazeState?> { null }

/** minSdk 33 always has RenderEffect blur, so blur is always on. */
@Composable
fun rememberChromeHaze(): HazeState = rememberHazeState(blurEnabled = true)

/** The bar material: heavy blur, then the bar colour at 72 %, so icons and labels stay legible over any photo. */
@Composable
fun chromeHazeStyle(): HazeStyle {
    val colors = MaterialTheme.colorScheme
    return HazeStyle(
        backgroundColor = colors.surfaceContainer,
        tint = HazeTint(colors.surfaceContainer.copy(alpha = CHROME_TINT_ALPHA)),
        blurRadius = 28.dp,
        noiseFactor = 0f,
    )
}

/**
 * A thicker material for small floating surfaces (the tab capsule): a much stronger blur and a near-solid tint, so a
 * bright photo underneath only tints it instead of showing through with edges.
 */
@Composable
fun floatingHazeStyle(): HazeStyle {
    val colors = MaterialTheme.colorScheme
    return HazeStyle(
        backgroundColor = colors.surfaceContainerHigh,
        tint = HazeTint(colors.surfaceContainerHigh.copy(alpha = FLOATING_TINT_ALPHA)),
        blurRadius = 48.dp,
        noiseFactor = 0f,
    )
}

private const val FLOATING_TINT_ALPHA = 0.86f

/**
 * Draws this bar as frosted glass over [state]'s content. Edge bars get a faint hairline on the edge that meets the
 * content ([edgeAtTop]: a bottom bar's top edge; false for a top bar's bottom edge). A [capsule] (a floating pill)
 * is clipped round and gets a hairline all round instead.
 */
@Composable
fun Modifier.frostedChrome(
    state: HazeState? = LocalChromeHaze.current,
    edgeAtTop: Boolean = true,
    capsule: Boolean = false,
    style: HazeStyle = chromeHazeStyle(),
    /** A floating card instead of a capsule: rounded by this much, with the hairline all round. */
    corner: Dp? = null,
): Modifier {
    val hairline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val rounded = capsule || corner != null
    val clipped = when {
        corner != null -> clip(RoundedCornerShape(corner))
        capsule -> clip(RoundedCornerShape(percent = 50))
        else -> this
    }
    val material = if (state != null) clipped.hazeEffect(state, style) else clipped
    return material.drawWithContent {
        val radius = CornerRadius(corner?.toPx() ?: (size.height / 2))
        if (state == null) {
            if (rounded) drawRoundRect(style.backgroundColor, cornerRadius = radius) else drawRect(style.backgroundColor)
        }
        drawContent()
        if (rounded) {
            drawRoundRect(hairline, cornerRadius = radius, style = Stroke(width = 1f))
        } else {
            val y = if (edgeAtTop) 0f else size.height
            drawLine(hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
    }
}

private const val CHROME_TINT_ALPHA = 0.72f
