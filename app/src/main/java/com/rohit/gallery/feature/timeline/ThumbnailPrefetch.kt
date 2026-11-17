package com.rohit.gallery.feature.timeline

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.LocalThumbnailCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val ROW_KEY_PREFIX = "row-"
private const val PREFETCH_ROWS = 10

fun rowKey(row: List<MediaItem>): String = ROW_KEY_PREFIX + row.first().id

/**
 * Warms the thumbnail cache a few rows beyond the visible ones, in the direction the user is
 * scrolling. When the visible range changes, the previous batch is cancelled so stale work
 * never piles up during a fast fling.
 */
@Composable
fun PrefetchThumbnails(
    listState: LazyListState,
    days: List<TimelineDay>,
    cellSizePx: Int,
) {
    val cache = LocalThumbnailCache.current
    LaunchedEffect(days, cellSizePx) {
        val (flatRows, indexByFirstId) = withContext(Dispatchers.Default) {
            val rows = days.flatMap { it.rows }
            rows to HashMap<Long, Int>(rows.size * 2).also { map ->
                rows.forEachIndexed { index, row -> map[row.first().id] = index }
            }
        }

        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
                .mapNotNull { (it.key as? String)?.takeIf { k -> k.startsWith(ROW_KEY_PREFIX) } }
                .mapNotNull { indexByFirstId[it.removePrefix(ROW_KEY_PREFIX).toLongOrNull()] }
            if (visible.isEmpty()) null else Triple(visible.min(), visible.max(), listState.lastScrolledForward)
        }
            .distinctUntilChanged()
            .collectLatest { range ->
                range ?: return@collectLatest
                val (first, last, forward) = range
                // Closest rows first, so the ones about to appear are ready before the far ones.
                val target = if (forward) {
                    (last + 1)..minOf(last + PREFETCH_ROWS, flatRows.lastIndex)
                } else {
                    (first - 1) downTo maxOf(first - PREFETCH_ROWS, 0)
                }
                coroutineScope {
                    for (rowIndex in target) {
                        for (item in flatRows[rowIndex]) {
                            launch { cache.prefetch(item, cellSizePx) }
                        }
                    }
                }
            }
    }
}
