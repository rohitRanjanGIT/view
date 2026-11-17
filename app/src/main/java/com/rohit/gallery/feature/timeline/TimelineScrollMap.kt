package com.rohit.gallery.feature.timeline

/**
 * Pixel geometry of the timeline list, for the fast-scroller. The list is, per day, one header
 * ([headerPx] tall) followed by that day's rows ([rowPx] tall each), so the offset of any position
 * can be computed exactly without measuring off-screen items. Pure Kotlin, so it is unit-tested.
 */
class TimelineScrollMap(days: List<TimelineDay>, private val headerPx: Int, private val rowPx: Int) {
    private val firstIndex = IntArray(days.size)
    private val startPx = LongArray(days.size)
    private val epochDays = LongArray(days.size)

    /** Height of the whole list content, without padding. */
    val totalPx: Long

    init {
        var index = 0
        var px = 0L
        days.forEachIndexed { d, day ->
            firstIndex[d] = index
            startPx[d] = px
            epochDays[d] = day.epochDay
            index += 1 + day.rows.size
            px += headerPx + day.rows.size.toLong() * rowPx
        }
        totalPx = px
    }

    /** Content offset of the top of the viewport, given the list's first visible item and its scroll offset. */
    fun offsetOf(lazyIndex: Int, itemScrollOffset: Int): Long {
        if (firstIndex.isEmpty()) return 0L
        val d = dayOfLazyIndex(lazyIndex)
        val k = lazyIndex - firstIndex[d]
        val itemStart = startPx[d] + if (k == 0) 0L else headerPx + (k - 1).toLong() * rowPx
        return itemStart + itemScrollOffset
    }

    /** The list position (lazy item index, offset inside it) that shows content offset [px] at the top. */
    fun positionAt(px: Long): Pair<Int, Int> {
        if (firstIndex.isEmpty()) return 0 to 0
        val target = px.coerceIn(0L, (totalPx - 1).coerceAtLeast(0L))
        val d = dayAtOffset(target)
        val inDay = target - startPx[d]
        if (inDay < headerPx) return firstIndex[d] to inDay.toInt()
        val afterHeader = inDay - headerPx
        val row = (afterHeader / rowPx).toInt()
        return firstIndex[d] + 1 + row to (afterHeader - row.toLong() * rowPx).toInt()
    }

    /** The epoch day shown at content offset [px]. */
    fun epochDayAt(px: Long): Long? =
        if (epochDays.isEmpty()) null else epochDays[dayAtOffset(px.coerceAtLeast(0L))]

    private fun dayAtOffset(px: Long): Int = lastAtOrBefore(startPx.size) { startPx[it] <= px }

    private fun dayOfLazyIndex(index: Int): Int = lastAtOrBefore(firstIndex.size) { firstIndex[it] <= index }

    /** Binary search: the last position in 0 until [size] for which [fits] holds (0 if none). */
    private inline fun lastAtOrBefore(size: Int, fits: (Int) -> Boolean): Int {
        var lo = 0
        var hi = size - 1
        var best = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (fits(mid)) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best
    }
}
