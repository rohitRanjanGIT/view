package com.rohit.gallery.feature.selection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import com.rohit.gallery.feature.timeline.GridLevel
import com.rohit.gallery.feature.timeline.GroupedLibrary
import com.rohit.gallery.feature.timeline.TimelineGrid
import com.rohit.gallery.feature.timeline.TimelineUiState
import com.rohit.gallery.feature.timeline.groupLibrary
import com.rohit.gallery.feature.timeline.toUiState
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.model.MediaItem

/**
 * A secondary screen showing [items] as a selectable grid under a back/title bar, with the shared
 * selection actions (Share, Favorite, Trash, Move/Copy). Used by album detail, Favorites and Videos.
 * [items] null means still loading.
 */
@Composable
fun MediaListScreen(
    title: String,
    emptyText: String,
    items: List<MediaItem>?,
    selection: SelectionState,
    selectionActions: SelectionActions,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
) {
    val allFavorite by remember(items) {
        derivedStateOf {
            val ids = selection.ids
            ids.isNotEmpty() && items.orEmpty().filter { it.id in ids }.let { sel -> sel.isNotEmpty() && sel.all { it.isFavorite } }
        }
    }
    MediaListScreen(title, emptyText, items, selection, onBack, onOpenMedia, asTimeline = true) { modifier ->
        SelectionActionsBar(selectionActions, selection, modifier, allFavorite = allFavorite)
    }
}

/**
 * The same screen with its own bottom bar for a selection (Trash: Restore / Delete). [gridHeader] is a
 * note shown above the flat grid. [asTimeline] shows the items like Pictures (day headers, fast-scroller,
 * month view) instead of a flat grid.
 */
@Composable
fun MediaListScreen(
    title: String,
    emptyText: String,
    items: List<MediaItem>?,
    selection: SelectionState,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    gridHeader: String? = null,
    asTimeline: Boolean = false,
    selectionBar: @Composable (Modifier) -> Unit,
) {
    BackHandler(enabled = selection.isSelecting) { selection.clear() }

    // Kept per screen for now; remembered across launches once Settings / DataStore land.
    var columns by rememberSaveable { mutableIntStateOf(GRID_COLUMNS) }
    var level by rememberSaveable { mutableStateOf(GridLevel.Default) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!selection.isSelecting) {
                TitleBar(title, onBack)
            } else {
                SelectionTopBar(
                    count = selection.ids.size,
                    onClose = selection::clear,
                    onSelectAll = { items?.let(selection::selectAll) },
                )
            }

            when {
                items == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(emptyText, style = MaterialTheme.typography.titleMedium)
                }
                asTimeline -> MediaTimeline(items, selection, level, { level = it }, onOpenMedia)
                else -> SelectableMediaGrid(items, selection, columns, { columns = it }, onOpenMedia, header = gridHeader)
            }
        }

        if (selection.isSelecting) selectionBar(Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * [items] laid out like the Pictures tab. Grouping into days and months runs on a background thread
 * once per list change; a density change only re-chunks rows.
 */
@Composable
private fun MediaTimeline(
    items: List<MediaItem>,
    selection: SelectionState,
    level: GridLevel,
    onLevelChange: (GridLevel) -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
) {
    val grouped by produceState<GroupedLibrary?>(null, items) {
        value = withContext(Dispatchers.Default) { groupLibrary(items, ZoneId.systemDefault()) }
    }
    val state by produceState<TimelineUiState?>(null, grouped, level) {
        val lib = grouped ?: return@produceState
        value = withContext(Dispatchers.Default) { lib.toUiState(level) }
    }
    // Until the first grouping is ready (a few ms) the screen stays blank rather than flashing a spinner.
    val content = state as? TimelineUiState.Content ?: return
    TimelineGrid(
        content = content,
        selection = selection,
        contentBottomPadding = ListBottomPadding,
        onOpenMedia = onOpenMedia,
        onLevelChange = onLevelChange,
        underStatusBar = false,
    )
}

/** Room under the last row for the selection bar. */
private val ListBottomPadding = 96.dp

@Composable
private fun TitleBar(title: String, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
