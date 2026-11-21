package com.rohit.gallery.feature.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Under the albums: Favourites, Videos, Motion and GIFs, and Trash as tiles, two to a row. [enabled] false (while selecting albums)
 * dims them and ignores taps.
 */
@Composable
fun CollectionsSection(
    enabled: Boolean,
    onOpenFavorites: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenMotion: () -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = listOf(
        CollectionTileSpec(Icons.Outlined.FavoriteBorder, "Favourites", onOpenFavorites),
        CollectionTileSpec(Icons.Outlined.PlayArrow, "Videos", onOpenVideos),
        CollectionTileSpec(Icons.Outlined.Refresh, "Motion and GIFs", onOpenMotion),
        CollectionTileSpec(Icons.Outlined.Delete, "Trash", onOpenTrash),
    )
    Column(modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            "Collections",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = TileGap), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                pair.forEach { CollectionTile(it, enabled, Modifier.weight(1f)) }
                // An odd one out keeps its half width, like the others.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private class CollectionTileSpec(val icon: ImageVector, val label: String, val onClick: () -> Unit)

private val TileGap = 8.dp

@Composable
private fun CollectionTile(spec: CollectionTileSpec, enabled: Boolean, modifier: Modifier) {
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = enabled, onClick = spec.onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(spec.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            spec.label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}
