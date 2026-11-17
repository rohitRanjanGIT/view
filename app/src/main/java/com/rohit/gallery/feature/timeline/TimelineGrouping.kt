package com.rohit.gallery.feature.timeline

import androidx.compose.runtime.Immutable
import com.rohit.gallery.core.model.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All items from one calendar day, in the order given (newest first). */
@Immutable
data class DayGroup(val epochDay: Long, val items: List<MediaItem>)

/** A day laid out for the grid: [rows] each hold up to the current column count of items. */
@Immutable
data class TimelineDay(val epochDay: Long, val rows: List<List<MediaItem>>)

/**
 * Groups a newest-first list into calendar days in [zone]. Consecutive items on the same day
 * share a group, so the input must already be sorted by date.
 */
fun groupByDay(items: List<MediaItem>, zone: ZoneId): List<DayGroup> {
    val groups = ArrayList<DayGroup>()
    var currentDay = Long.MIN_VALUE
    var current = ArrayList<MediaItem>()
    for (item in items) {
        val day = Instant.ofEpochMilli(item.sortDateMs).atZone(zone).toLocalDate().toEpochDay()
        if (day != currentDay) {
            if (current.isNotEmpty()) groups += DayGroup(currentDay, current)
            currentDay = day
            current = ArrayList()
        }
        current += item
    }
    if (current.isNotEmpty()) groups += DayGroup(currentDay, current)
    return groups
}

fun List<DayGroup>.toTimelineDays(columns: Int): List<TimelineDay> =
    map { TimelineDay(it.epochDay, it.items.chunked(columns)) }

/** "Today", "Yesterday", "Mon, 12 Nov", or "12 Nov 2024" for other years. */
fun dayLabel(epochDay: Long, today: LocalDate, locale: Locale = Locale.getDefault()): String {
    val date = LocalDate.ofEpochDay(epochDay)
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }
}

/** "November 2026": the fast-scroll bubble's label. */
fun monthLabel(epochDay: Long, locale: Locale = Locale.getDefault()): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

/** 83_000 -> "1:23", 3_723_000 -> "1:02:03". */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
