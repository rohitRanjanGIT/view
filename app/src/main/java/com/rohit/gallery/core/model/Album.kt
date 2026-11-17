package com.rohit.gallery.core.model

import androidx.compose.runtime.Immutable

/** A folder of media (a MediaStore bucket). [cover] is its newest item. */
@Immutable
data class Album(
    val bucketId: Long,
    val name: String,
    val count: Int,
    val cover: MediaItem,
)

/** Groups [items] (newest first) into albums, ordered by their newest item. */
fun buildAlbums(items: List<MediaItem>): List<Album> {
    val counts = LinkedHashMap<Long, Int>()
    val covers = HashMap<Long, MediaItem>()
    for (item in items) {
        counts[item.bucketId] = (counts[item.bucketId] ?: 0) + 1
        if (item.bucketId !in covers) covers[item.bucketId] = item
    }
    return counts.map { (id, count) ->
        val cover = covers.getValue(id)
        Album(id, cover.bucketName.ifBlank { "Unnamed" }, count, cover)
    }
}
