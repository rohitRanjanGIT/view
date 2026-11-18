package com.rohit.gallery.feature.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.LocalThumbnailCache
import com.rohit.gallery.core.ui.components.mediaSharedElement
import com.rohit.gallery.core.ui.components.thumbKey

private const val THUMBNAIL_FALLBACK_PX = 512

/** The cached grid thumbnail for [item], loading it in the background if it is not cached yet. */
@Composable
fun rememberThumbnail(item: MediaItem): ImageBitmap? {
    val cache = LocalThumbnailCache.current
    var thumbnail by remember(item.id) { mutableStateOf(cache.get(item.thumbKey)?.asImageBitmap()) }
    if (thumbnail == null) {
        LaunchedEffect(item.id) {
            thumbnail = runCatching { cache.load(item, THUMBNAIL_FALLBACK_PX) }.getOrNull()?.asImageBitmap()
        }
    }
    return thumbnail
}

/**
 * The shared element for the grid <-> viewer flight: the thumbnail, sized exactly like the photo is
 * shown on screen (fitted, centered). The flight therefore starts and ends on the photo's real
 * outline instead of the full-screen box, which made the cropped thumbnail look like it was
 * opening full screen before shrinking. Invisible when no transition is running.
 */
@Composable
fun SharedFlightFrame(
    item: MediaItem,
    thumbnail: ImageBitmap?,
    transitionActive: () -> Boolean,
    modifier: Modifier = Modifier,
    placement: (() -> FlightPlacement)? = null,
) {
    val bitmap = thumbnail ?: return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                .then(if (placement != null) Modifier.flightPlacement(placement) else Modifier)
                .mediaSharedElement(item.id, force = true)
                .graphicsLayer { alpha = if (transitionActive()) 1f else 0f },
        )
    }
}

/** Where the drag-to-dismiss gesture has moved the photo: offset from its resting place, and scale. */
class FlightPlacement(val x: Float, val y: Float, val scale: Float)

/**
 * Applies [placement] as layout (size and position), not as a graphics layer: the shared-element
 * flight starts from layout bounds, so this makes a swipe-down close start exactly where the finger
 * left the photo. Scaling keeps the centre, like the drag's graphics layer does.
 */
private fun Modifier.flightPlacement(placement: () -> FlightPlacement): Modifier = layout { measurable, constraints ->
    val p = placement()
    val w = (constraints.maxWidth * p.scale).roundToInt().coerceAtLeast(1)
    val h = (constraints.maxHeight * p.scale).roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(w, h) { placeable.place(p.x.roundToInt(), p.y.roundToInt()) }
}
