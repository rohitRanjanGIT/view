package com.rohit.gallery.feature.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.FloatingActionPill
import com.rohit.gallery.feature.selection.GridSizeViewModel
import com.rohit.gallery.feature.selection.MediaTimeline
import com.rohit.gallery.feature.selection.SelectionState

/**
 * Picking photos for an album: every photo and video, like Pictures (a tap picks, it never opens the viewer), under
 * a bar with [title] and either the count picked or [hint]. [blockedText] replaces the grid when nothing can be
 * picked here. [actions] fill the floating pill at the bottom; their argument is whether anything is picked.
 */
@Composable
internal fun PhotoPickerLayout(
    title: String,
    hint: String,
    items: List<MediaItem>?,
    selection: SelectionState,
    onClose: () -> Unit,
    blockedText: String? = null,
    actions: @Composable RowScope.(enabled: Boolean) -> Unit,
) {
    val sizes: GridSizeViewModel = hiltViewModel()
    val level by sizes.level.collectAsStateWithLifecycle()
    // Back with photos picked clears the picks first, so a stray swipe doesn't lose them.
    BackHandler(enabled = selection.ids.isNotEmpty()) { selection.clear() }
    val count = selection.ids.size
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (count > 0) "$count selected" else hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
            when {
                blockedText != null -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text(blockedText, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
                items == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> level?.let { MediaTimeline(items, selection, it, sizes::setLevel, onOpenMedia = { item -> selection.toggle(item.id) }) }
            }
        }
        if (blockedText == null) FloatingActionPill(Modifier.align(Alignment.BottomCenter)) { actions(count > 0) }
    }
}
