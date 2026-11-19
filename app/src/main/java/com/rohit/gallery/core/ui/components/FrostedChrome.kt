package com.rohit.gallery.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
 * Draws this bar as frosted glass over [state]'s content, with a faint hairline on the edge that meets the content
 * ([edgeAtTop]: a bottom bar's top edge; false for a top bar's bottom edge) so it reads as a layer, not a cut-out.
 */
@Composable
fun Modifier.frostedChrome(state: HazeState? = LocalChromeHaze.current, edgeAtTop: Boolean = true): Modifier {
    val hairline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val style = chromeHazeStyle()
    val material = if (state != null) hazeEffect(state, style) else this
    return material.drawWithContent {
        if (state == null) drawRect(style.backgroundColor)
        drawContent()
        val y = if (edgeAtTop) 0f else size.height
        drawLine(hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
}

private const val CHROME_TINT_ALPHA = 0.72f
