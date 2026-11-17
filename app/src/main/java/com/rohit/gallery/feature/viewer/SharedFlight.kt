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
) {
    val bitmap = thumbnail ?: return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                .mediaSharedElement(item.id, force = true)
                .graphicsLayer { alpha = if (transitionActive()) 1f else 0f },
        )
    }
}
