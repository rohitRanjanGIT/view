package com.rohit.gallery

import com.rohit.gallery.feature.timeline.GridLevel
import com.rohit.gallery.feature.timeline.dayLabel
import com.rohit.gallery.feature.timeline.groupByMonth
import com.rohit.gallery.feature.timeline.formatDuration
import com.rohit.gallery.feature.timeline.groupByDay
import com.rohit.gallery.feature.timeline.toTimelineDays
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineGroupingTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private fun ms(date: String, time: String = "12:00") =
        java.time.LocalDateTime.parse("${date}T$time").toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `groups consecutive items by calendar day`() {
        val items = listOf(
            testItem(1, dateTakenMs = ms("2026-03-02", "20:00")),
            testItem(2, dateTakenMs = ms("2026-03-02", "08:00")),
            testItem(3, dateTakenMs = ms("2026-03-01", "23:59")),
        )
        val groups = groupByDay(items, utc)
        assertEquals(2, groups.size)
        assertEquals(listOf(1L, 2L), groups[0].items.map { it.id })
        assertEquals(listOf(3L), groups[1].items.map { it.id })
        assertEquals(LocalDate.of(2026, 3, 2).toEpochDay(), groups[0].epochDay)
    }

    @Test
    fun `day boundary follows the supplied time zone`() {
        val late = testItem(1, dateTakenMs = ms("2026-03-01", "23:30"))
        val plusTwo = ZoneOffset.ofHours(2)
        assertEquals(LocalDate.of(2026, 3, 1).toEpochDay(), groupByDay(listOf(late), utc).single().epochDay)
        assertEquals(LocalDate.of(2026, 3, 2).toEpochDay(), groupByDay(listOf(late), plusTwo).single().epochDay)
    }

    @Test
    fun `empty input gives no groups`() {
        assertEquals(emptyList(), groupByDay(emptyList(), utc))
    }

    @Test
    fun `rows are chunked by column count with a short last row`() {
        val items = (1L..9L).map { testItem(it, dateTakenMs = ms("2026-03-02") - it) }
        val day = groupByDay(items, utc).toTimelineDays(columns = 4).single()
        assertEquals(listOf(4, 4, 1), day.rows.map { it.size })
    }

    @Test
    fun `month view merges days of the same month`() {
        val items = listOf(
            testItem(1, dateTakenMs = ms("2026-03-20")),
            testItem(2, dateTakenMs = ms("2026-03-02")),
            testItem(3, dateTakenMs = ms("2026-02-28")),
            testItem(4, dateTakenMs = ms("2025-03-15")),
        )
        val months = groupByMonth(groupByDay(items, utc))
        assertEquals(listOf(listOf(1L, 2L), listOf(3L), listOf(4L)), months.map { m -> m.items.map { it.id } })
        assertEquals(
            listOf(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2025, 3, 1)).map { it.toEpochDay() },
            months.map { it.epochDay },
        )
    }

    @Test
    fun `grid levels step between 2, 4, 6 columns and the month view`() {
        assertEquals(listOf(2, 4, 6, 8), GridLevel.entries.map { it.columns })
        assertEquals(GridLevel.Medium, GridLevel.Default)
        assertEquals(null, GridLevel.Large.zoomIn())
        assertEquals(GridLevel.Large, GridLevel.Medium.zoomIn())
        assertEquals(GridLevel.Month, GridLevel.Small.zoomOut())
        assertEquals(null, GridLevel.Month.zoomOut())
        assertEquals(listOf(GridLevel.Month), GridLevel.entries.filter { it.byMonth })
    }

    @Test
    fun `day labels`() {
        val today = LocalDate.of(2026, 11, 17)
        val en = Locale.ENGLISH
        assertEquals("Today", dayLabel(today.toEpochDay(), today, en))
        assertEquals("Yesterday", dayLabel(today.minusDays(1).toEpochDay(), today, en))
        assertEquals("Sun, 15 Nov", dayLabel(today.minusDays(2).toEpochDay(), today, en))
        assertEquals("5 Jan 2025", dayLabel(LocalDate.of(2025, 1, 5).toEpochDay(), today, en))
    }

    @Test
    fun `duration formatting`() {
        assertEquals("0:05", formatDuration(5_000))
        assertEquals("1:23", formatDuration(83_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }
}
