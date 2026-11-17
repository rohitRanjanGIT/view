package com.rohit.gallery.core.ui.components

import android.content.Context
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.timeline.formatDuration

/** The one place grid thumbnail requests are built, so prefetching hits the same cache entries. */
fun thumbnailRequest(context: Context, item: MediaItem, cellSizePx: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(MediaThumbData(item.id, item.isVideo, item.dateModifiedMs))
        .size(cellSizePx)
        .build()

/**
 * Square grid thumbnail. Always requested at [cellSizePx] so a grid cell never decodes a
 * full-size image.
 */
@Composable
fun MediaThumbnail(
    item: MediaItem,
    cellSizePx: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(item.id, item.type, item.dateModifiedMs, cellSizePx) {
        thumbnailRequest(context, item, cellSizePx)
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
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
