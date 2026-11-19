package com.rohit.gallery.core.model

import java.text.NumberFormat

/** "1,204 photos · 37 videos" (parts with nothing in them left out); empty for an empty list. */
fun mediaCountLabel(items: List<MediaItem>): String {
    val videos = items.count { it.type == MediaType.Video }
    val photos = items.size - videos
    val format = NumberFormat.getIntegerInstance()
    fun part(n: Int, one: String, many: String) = "${format.format(n)} ${if (n == 1) one else many}"
    return listOfNotNull(
        part(photos, "photo", "photos").takeIf { photos > 0 },
        part(videos, "video", "videos").takeIf { videos > 0 },
    ).joinToString(" · ")
}

/** "12 albums" / "1 album". */
fun albumCountLabel(count: Int): String =
    "${NumberFormat.getIntegerInstance().format(count)} ${if (count == 1) "album" else "albums"}"
