package com.rohit.gallery

import com.rohit.gallery.core.model.MediaType
import com.rohit.gallery.feature.search.SearchIndex
import com.rohit.gallery.feature.search.parseSearch
import com.rohit.gallery.feature.search.suggestions
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSearchTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2025, 3, 15) // a Saturday

    private fun at(date: String) = LocalDate.parse(date).atTime(12, 0).toInstant(zone).toEpochMilli()

    // Newest first, like the library.
    private val items = listOf(
        testItem(6, at("2025-03-15")).copy(displayName = "IMG_20250315_120000.jpg"),
        testItem(1, at("2025-03-14")).copy(displayName = "IMG_20250314_090000.jpg"),
        testItem(2, at("2025-03-10"), type = MediaType.Video).copy(displayName = "VID_20250310.mp4"),
        testItem(5, at("2025-02-01"), bucketId = 4, bucketName = "Download")
            .copy(displayName = "funny.gif", mimeType = "image/gif", isFavorite = true),
        testItem(4, at("2024-12-25"), bucketId = 3, bucketName = "Screenshots")
            .copy(displayName = "Screenshot_20241225.png"),
        testItem(3, at("2024-03-05"), bucketId = 2, bucketName = "WhatsApp Images")
            .copy(displayName = "IMG-20240305-WA0001.jpg"),
        testItem(7, at("2023-07-01"), bucketId = 5, bucketName = "May trip").copy(displayName = "DSC0001.jpg"),
    )
    private val index = SearchIndex(items, zone)

    private fun ids(query: String) = index.search(parseSearch(query, today, zone)).map { it.id }

    @Test fun years() {
        assertEquals(listOf(4L, 3L), ids("2024"))
    }

    @Test fun monthOfAnyYearAndShortNames() {
        assertEquals(listOf(6L, 1L, 2L, 3L), ids("march"))
        assertEquals(listOf(6L, 1L, 2L, 3L), ids("Mar"))
    }

    @Test fun wordsCombine() {
        assertEquals(listOf(3L), ids("march 2024"))
        assertEquals(listOf(2L), ids("videos 2025"))
        assertEquals(emptyList<Long>(), ids("videos 2024"))
    }

    @Test fun kinds() {
        assertEquals(listOf(2L), ids("videos"))
        assertEquals(listOf(4L), ids("screenshots"))
        assertEquals(listOf(5L), ids("gif"))
        assertEquals(listOf(5L), ids("favourites"))
        assertEquals(6, ids("photos").size)
    }

    @Test fun albumAndFileNames() {
        assertEquals(listOf(3L), ids("whatsapp"))
        assertEquals(listOf(3L), ids("\"WhatsApp Images\""))
        assertEquals(listOf(1L), ids("IMG_20250314"))
        assertEquals(listOf(4L), ids("screenshot_2024"))
    }

    @Test fun monthWordAlsoMatchesAlbumNames() {
        assertEquals(listOf(7L), ids("may"))
    }

    @Test fun relativeDates() {
        assertEquals(listOf(6L), ids("today"))
        assertEquals(listOf(1L), ids("yesterday"))
        assertEquals(listOf(6L, 1L, 2L), ids("this week")) // Monday 10 March onwards
        assertEquals(emptyList<Long>(), ids("last week"))
        assertEquals(listOf(5L), ids("last month"))
        assertEquals(listOf(6L, 1L, 2L, 5L), ids("this year"))
        assertEquals(listOf(4L, 3L), ids("last year"))
    }

    @Test fun emptyOrUnknown() {
        assertEquals(emptyList<Long>(), ids(""))
        assertEquals(emptyList<Long>(), ids("   "))
        assertEquals(emptyList<Long>(), ids("zebra"))
    }

    @Test fun suggestions() {
        val s = index.suggestions()
        assertEquals(listOf("Videos", "Screenshots", "GIFs", "Favourites"), s.kinds.map { it.label })
        assertEquals("Camera", s.albums.first().label)
        assertEquals("\"Camera\"", s.albums.first().query)
        assertEquals(3, s.albums.first().count)
        assertEquals(listOf("2025", "2024", "2023"), s.years.map { it.label })
    }
}
