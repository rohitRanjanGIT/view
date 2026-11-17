package com.rohit.gallery

import com.rohit.gallery.feature.timeline.TimelineDay
import com.rohit.gallery.feature.timeline.TimelineScrollMap
import com.rohit.gallery.feature.timeline.lazyRowIndexOf
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineScrollMapTest {

    // Day 30: header + 2 rows, day 20: header + 1 row, day 10: header + 3 rows.
    private val days = listOf(
        TimelineDay(30, List(2) { listOf(testItem(it.toLong())) }),
        TimelineDay(20, List(1) { listOf(testItem(10L + it)) }),
        TimelineDay(10, List(3) { listOf(testItem(20L + it)) }),
    )
    private val map = TimelineScrollMap(days, headerPx = 40, rowPx = 100)

    @Test
    fun totalHeightIsHeadersPlusRows() {
        assertEquals(3L * 40 + 6 * 100, map.totalPx)
    }

    @Test
    fun offsetsOfHeadersAndRows() {
        assertEquals(0L, map.offsetOf(0, 0))
        assertEquals(40L, map.offsetOf(1, 0)) // first row of day 30
        assertEquals(165L, map.offsetOf(2, 25)) // second row + 25
        assertEquals(240L, map.offsetOf(3, 0)) // header of day 20
        assertEquals(380L, map.offsetOf(5, 0)) // header of day 10
    }

    @Test
    fun positionAtIsTheInverseOfOffsetOf() {
        for (index in 0 until 9) {
            for (inside in listOf(0, 10, 39)) {
                val px = map.offsetOf(index, inside)
                assertEquals(index to inside, map.positionAt(px))
            }
        }
    }

    @Test
    fun positionsAreClampedToTheContent() {
        assertEquals(0 to 0, map.positionAt(-50))
        assertEquals(8 to 99, map.positionAt(10_000))
    }

    @Test
    fun dayAtOffset() {
        assertEquals(30L, map.epochDayAt(0))
        assertEquals(30L, map.epochDayAt(239))
        assertEquals(20L, map.epochDayAt(240))
        assertEquals(10L, map.epochDayAt(9_999))
    }

    @Test
    fun rowIndexOfItem() {
        // 4 columns. Day A: 5 items (rows of 4 + 1), day B: 2 items.
        val a = TimelineDay(2, List(5) { testItem(it.toLong()) }.chunked(4))
        val b = TimelineDay(1, List(2) { testItem(10L + it) }.chunked(4))
        val twoDays = listOf(a, b)
        assertEquals(1, lazyRowIndexOf(twoDays, 0)) // index 0 is A's header
        assertEquals(1, lazyRowIndexOf(twoDays, 3))
        assertEquals(2, lazyRowIndexOf(twoDays, 4))
        assertEquals(4, lazyRowIndexOf(twoDays, 5)) // B's header is 3
        assertEquals(4, lazyRowIndexOf(twoDays, 6))
        assertEquals(null, lazyRowIndexOf(twoDays, 7))
        assertEquals(null, lazyRowIndexOf(twoDays, -1))
    }

    @Test
    fun emptyTimeline() {
        val empty = TimelineScrollMap(emptyList(), 40, 100)
        assertEquals(0L, empty.totalPx)
        assertEquals(0 to 0, empty.positionAt(100))
        assertEquals(null, empty.epochDayAt(0))
    }
}
