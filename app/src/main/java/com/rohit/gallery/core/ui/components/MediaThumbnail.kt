package com.rohit.gallery.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.timeline.formatDuration

/**
 * Square grid thumbnail. A thumbnail already in [ThumbnailCache] is read during composition,
 * so it is drawn in the very first frame; otherwise it loads in the background.
 */
@Composable
fun MediaThumbnail(
    item: MediaItem,
    cellSizePx: Int,
    modifier: Modifier = Modifier,
    showBadge: Boolean = true,
) {
    val cache = LocalThumbnailCache.current
    val key = item.thumbKey
    var image: ImageBitmap? by remember(key) { mutableStateOf(cache.get(key)?.asImageBitmap()) }

    if (image == null) {
        LaunchedEffect(key, cellSizePx) {
            image = runCatching { cache.load(item, cellSizePx) }.getOrNull()?.asImageBitmap()
        }
    }

    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showBadge && item.isVideo) {
            DurationBadge(
                durationMs = item.durationMs,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun DurationBadge(durationMs: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        Text(
            formatDuration(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}
