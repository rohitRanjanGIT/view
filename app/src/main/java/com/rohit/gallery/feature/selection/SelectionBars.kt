package com.rohit.gallery.feature.selection

import com.rohit.gallery.core.ui.icons.GalleryIcons
import com.rohit.gallery.core.domain.copyToClipboard
import com.rohit.gallery.core.model.MediaItem
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.rohit.gallery.core.ui.components.FloatingActionPill
import com.rohit.gallery.core.ui.components.pillSlot
import com.rohit.gallery.core.ui.components.frostedChrome
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Top bar while selecting: close, "N selected", and select all. */
@Composable
fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
    onCopy: (() -> Unit)? = null,
) {
    Surface(color = Color.Transparent, modifier = modifier.fillMaxWidth().frostedChrome(edgeAtTop = false)) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Exit selection") }
            Text(
                if (count == 0) "Select items" else "$count selected",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            // Copy to clipboard works on one photo at a time (most apps paste only the first anyway).
            if (onCopy != null && count == 1) {
                IconButton(onClick = onCopy) { Icon(GalleryIcons.Copy, contentDescription = "Copy to clipboard") }
            }
            TextButton(onClick = onSelectAll) { Text("All") }
        }
    }
}

/** Bottom bar while selecting; takes the place of the navigation bar. */
@Composable
fun SelectionActionBar(
    enabled: Boolean,
    allFavorite: Boolean,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onTrash: () -> Unit,
    onMove: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onLock: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    FloatingActionPill(modifier) {
        ActionItem(Icons.Filled.Share, "Share", enabled, onShare, Modifier.pillSlot())
        ActionItem(
            if (allFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            if (allFavorite) "Unfavourite" else "Favourite",
            enabled,
            onFavorite,
            Modifier.pillSlot(),
        )
        ActionItem(Icons.Filled.Delete, "Trash", enabled, onTrash, Modifier.pillSlot())
        if (onMove != null || onCopy != null || onLock != null) {
            var menuOpen by remember { mutableStateOf(false) }
            Box(Modifier.pillSlot()) {
                ActionItem(Icons.Filled.MoreVert, "More", enabled, { menuOpen = true }, Modifier.fillMaxWidth())
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onMove != null) DropdownMenuItem(text = { Text("Move to album") }, onClick = { menuOpen = false; onMove() })
                    if (onCopy != null) DropdownMenuItem(text = { Text("Copy to album") }, onClick = { menuOpen = false; onCopy() })
                    if (onLock != null) DropdownMenuItem(text = { Text("Lock") }, onClick = { menuOpen = false; onLock() })
                }
            }
        }
    }
}

@Composable
internal fun ActionItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    Column(
        modifier
            // In the capsule, the press ripple is a pill too.
            .clip(RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Round check badge shown in the corner of every cell while selecting. */
@Composable
fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                        .background(Color.Black.copy(alpha = 0.25f), CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * For [SelectionTopBar]'s Copy: copies the one selected item among [items] to the clipboard and ends the selection.
 */
fun copySelected(context: Context, selection: SelectionState, items: List<MediaItem>?) {
    val id = selection.ids.singleOrNull() ?: return
    items?.firstOrNull { it.id == id }?.let { copyToClipboard(context, it) }
    selection.clear()
}
