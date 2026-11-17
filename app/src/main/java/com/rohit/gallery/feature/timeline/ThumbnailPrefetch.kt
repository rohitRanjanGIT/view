package com.rohit.gallery.feature.timeline

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.thumbnailRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private const val ROW_KEY_PREFIX = "row-"
private const val PREFETCH_ROWS = 10

fun rowKey(row: List<MediaItem>): String = ROW_KEY_PREFIX + row.first().id

/**
 * Warms the image memory cache a few rows beyond the visible ones, in the direction the user
 * is scrolling, so thumbnails are already there when the rows scroll into view.
 */
@Composable
fun PrefetchThumbnails(
    listState: LazyListState,
    days: List<TimelineDay>,
    cellSizePx: Int,
) {
    val context = LocalContext.current
    LaunchedEffect(days, cellSizePx) {
        val (flatRows, indexByFirstId) = withContext(Dispatchers.Default) {
            val rows = days.flatMap { it.rows }
            rows to HashMap<Long, Int>(rows.size * 2).also { map ->
                rows.forEachIndexed { index, row -> map[row.first().id] = index }
            }
        }
        val imageLoader = SingletonImageLoader.get(context)

        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
                .mapNotNull { (it.key as? String)?.takeIf { k -> k.startsWith(ROW_KEY_PREFIX) } }
                .mapNotNull { indexByFirstId[it.removePrefix(ROW_KEY_PREFIX).toLongOrNull()] }
            if (visible.isEmpty()) null else Triple(visible.min(), visible.max(), listState.lastScrolledForward)
        }
            .distinctUntilChanged()
            .collect { range ->
                range ?: return@collect
                val (first, last, forward) = range
                val target = if (forward) {
                    (last + 1)..minOf(last + PREFETCH_ROWS, flatRows.lastIndex)
                } else {
                    maxOf(first - PREFETCH_ROWS, 0) until first
                }
                for (rowIndex in target) {
                    for (item in flatRows[rowIndex]) {
                        imageLoader.enqueue(thumbnailRequest(context, item, cellSizePx))
                    }
                }
            }
    }
}
