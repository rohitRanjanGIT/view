package com.rohit.gallery.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.icons.GalleryIcons

/** One entry of the top bar's ⋮ menu. */
data class ViewerMenuEntry(val label: String, val onClick: () -> Unit)

/** One button of the bottom bar. */
data class ViewerAction(val icon: ImageVector, val description: String, val onClick: () -> Unit, val tint: Color? = null)

/** Back, the day ("Today", "Yesterday", "12 November") over the time, and a ⋮ menu. */
@Composable
fun ViewerTopBar(
    item: MediaItem,
    onBack: () -> Unit,
    menu: List<ViewerMenuEntry>,
    modifier: Modifier = Modifier,
    onCopy: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            // Dark enough, and tall enough, that the back arrow, date and menu stay readable on bright photos.
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.75f),
                    0.55f to Color.Black.copy(alpha = 0.45f),
                    1f to Color.Transparent,
                ),
            )
            .statusBarsPadding()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Spacer(Modifier.width(4.dp))
        // Files from other apps may carry no date at all.
        Column(Modifier.weight(1f)) {
            if (item.sortDateMs > 0) {
                Text(dayLabel(item.sortDateMs), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    timeLabel(context, item.sortDateMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy) {
                Icon(GalleryIcons.Copy, contentDescription = "Copy to clipboard", tint = Color.White)
            }
        }
        if (menu.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = Color.White)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(entry.label) },
                            onClick = {
                                open = false
                                entry.onClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Play / pause for a motion photo's clip or a GIF, centred above the filmstrip. */
@Composable
fun PlayPill(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (playing) GalleryIcons.Pause else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(if (playing) "Pause" else "Play", style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/**
 * The bottom bar: [start] in its own circle on the left, [middle] grouped in a pill, [end] in a circle on the right.
 * [onPanel] (0..1) moves the colours from "over the photo" (dark glass, white icons) to the details panel's.
 */
@Composable
fun ViewerActionBar(
    start: ViewerAction?,
    middle: List<ViewerAction>,
    end: ViewerAction?,
    onPanel: Float,
    modifier: Modifier = Modifier,
) {
    val container = lerp(Color(0xFF1C1C1C).copy(alpha = 0.85f), MaterialTheme.colorScheme.surfaceContainerHighest, onPanel)
    val content = lerp(Color.White, MaterialTheme.colorScheme.onSurface, onPanel)
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .height(ActionBarHeight),
    ) {
        start?.let { ActionCircle(it, container, content, Modifier.align(Alignment.CenterStart)) }
        if (middle.isNotEmpty()) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(container)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                middle.forEach { action ->
                    IconButton(onClick = action.onClick, modifier = Modifier.size(ActionBarHeight)) {
                        Icon(action.icon, contentDescription = action.description, tint = action.tint ?: content)
                    }
                }
            }
        }
        end?.let { ActionCircle(it, container, content, Modifier.align(Alignment.CenterEnd)) }
    }
}

@Composable
private fun ActionCircle(action: ViewerAction, container: Color, content: Color, modifier: Modifier) {
    IconButton(
        onClick = action.onClick,
        modifier = modifier
            .size(ActionBarHeight)
            .clip(CircleShape)
            .background(container),
    ) {
        Icon(action.icon, contentDescription = action.description, tint = action.tint ?: content)
    }
}

/** Height of the bottom bar's circles and pill; the details panel leaves this much room under its text. */
val ActionBarHeight = 56.dp
