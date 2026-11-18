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

/**
 * A section laid out for the grid: one day, or one month (keyed by its first day) in the month view.
 * [rows] each hold up to the current column count of items.
 */
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

/**
 * Merges consecutive day groups of the same calendar month into one group per month, keyed by the
 * month's first day. Input must be newest first, as [groupByDay] returns it.
 */
fun groupByMonth(days: List<DayGroup>): List<DayGroup> {
    val months = ArrayList<DayGroup>()
    var currentMonth = Long.MIN_VALUE
    var current = ArrayList<MediaItem>()
    for (day in days) {
        val month = LocalDate.ofEpochDay(day.epochDay).withDayOfMonth(1).toEpochDay()
        if (month != currentMonth) {
            if (current.isNotEmpty()) months += DayGroup(currentMonth, current)
            currentMonth = month
            current = ArrayList()
        }
        current.addAll(day.items)
    }
    if (current.isNotEmpty()) months += DayGroup(currentMonth, current)
    return months
}

/** Timeline densities, from biggest cells to smallest. Pinching in moves up the list, out moves down. */
enum class GridLevel(val columns: Int, val byMonth: Boolean) {
    Large(2, false),
    Medium(4, false),
    Small(6, false),
    Month(8, true);

    /** One step towards bigger cells, or null at the end. */
    fun zoomIn(): GridLevel? = entries.getOrNull(ordinal - 1)

    /** One step towards smaller cells, or null at the end. */
    fun zoomOut(): GridLevel? = entries.getOrNull(ordinal + 1)

    companion object {
        val Default = Medium
    }
}

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

/** A list grouped once per change into days and months; switching density only re-chunks rows. */
class GroupedLibrary(
    val items: List<MediaItem>,
    val days: List<DayGroup>,
    val months: List<DayGroup>,
    val indexById: Map<Long, Int>,
)

/** Groups a newest-first list for the timeline grid. Runs off the main thread (date math per item). */
fun groupLibrary(items: List<MediaItem>, zone: ZoneId): GroupedLibrary {
    val days = groupByDay(items, zone)
    return GroupedLibrary(
        items = items,
        days = days,
        months = groupByMonth(days),
        indexById = HashMap<Long, Int>(items.size * 2).also { map ->
            items.forEachIndexed { i, item -> map[item.id] = i }
        },
    )
}

/** The grid content at [level]: Empty for an empty list. */
fun GroupedLibrary.toUiState(level: GridLevel): TimelineUiState {
    if (items.isEmpty()) return TimelineUiState.Empty
    val sections = (if (level.byMonth) months else days).toTimelineDays(level.columns)
    return TimelineUiState.Content(
        days = sections,
        level = level,
        items = items,
        indexById = indexById,
        rowsByFirstId = HashMap<Long, List<MediaItem>>().also { map ->
            sections.forEach { day -> day.rows.forEach { row -> map[row.first().id] = row } }
        },
    )
}
