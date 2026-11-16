package com.rohit.gallery.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.permissions.MediaAccessGate
import com.rohit.gallery.core.ui.components.MediaThumbnail
import java.time.LocalDate

private val CellGap = 2.dp

@Composable
fun TimelineScreen(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    MediaAccessGate(modifier.fillMaxSize()) {
        TimelineContent(viewModel)
    }
}

@Composable
private fun TimelineContent(viewModel: TimelineViewModel) {
    // Also covers first load, permission changes and returning from the photo picker.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        TimelineUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        TimelineUiState.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("No photos or videos yet", style = MaterialTheme.typography.titleMedium)
        }
        is TimelineUiState.Content -> TimelineGrid(s)
    }
}

@Composable
private fun TimelineGrid(content: TimelineUiState.Content) {
    val columns = content.columns
    val density = LocalDensity.current
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val gapPx = with(density) { CellGap.roundToPx() }
    val cellSizePx = (windowWidthPx - gapPx * (columns - 1)) / columns
    val today = remember { LocalDate.now() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        content.days.forEach { day ->
            stickyHeader(key = "day-${day.epochDay}", contentType = "header") {
                DayHeader(dayLabel(day.epochDay, today))
            }
            items(
                items = day.rows,
                key = { row -> "row-${row.first().id}" },
                contentType = { "row" },
            ) { row ->
                ThumbnailRow(row, columns, cellSizePx)
            }
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ThumbnailRow(row: List<MediaItem>, columns: Int, cellSizePx: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = CellGap),
        horizontalArrangement = Arrangement.spacedBy(CellGap),
    ) {
        row.forEach { item ->
            MediaThumbnail(
                item = item,
                cellSizePx = cellSizePx,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
            )
        }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}
