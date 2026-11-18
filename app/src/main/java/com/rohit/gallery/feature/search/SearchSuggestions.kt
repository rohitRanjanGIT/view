package com.rohit.gallery.feature.search

/** A ready-made search: tapping it fills in [query]. */
data class Suggestion(val label: String, val query: String, val count: Int)

/** What the search screen offers before anything is typed. Empty lists are left out of the screen. */
data class SearchSuggestions(
    val kinds: List<Suggestion>,
    val albums: List<Suggestion>,
    val years: List<Suggestion>,
)

private val SUGGESTED_KINDS = listOf(MediaKind.Videos, MediaKind.Screenshots, MediaKind.Gifs, MediaKind.Favorites)

/** Kinds the library has, its [maxAlbums] biggest albums, and every year with photos (newest first). */
fun SearchIndex.suggestions(maxAlbums: Int = 8): SearchSuggestions {
    val kindCounts = IntArray(MediaKind.entries.size)
    val albumCounts = HashMap<Long, Int>()
    val albumNames = HashMap<Long, String>()
    val yearCounts = HashMap<Int, Int>()
    items.forEachIndexed { i, item ->
        SUGGESTED_KINDS.forEach { kind -> if (item.isKind(kind)) kindCounts[kind.ordinal]++ }
        if (item.bucketName.isNotBlank()) {
            albumCounts[item.bucketId] = (albumCounts[item.bucketId] ?: 0) + 1
            albumNames.putIfAbsent(item.bucketId, item.bucketName)
        }
        val year = years[i].toInt()
        if (year > 1970) yearCounts[year] = (yearCounts[year] ?: 0) + 1
    }
    return SearchSuggestions(
        kinds = SUGGESTED_KINDS.filter { kindCounts[it.ordinal] > 0 }
            .map { Suggestion(it.label, it.label, kindCounts[it.ordinal]) },
        albums = albumCounts.entries.sortedByDescending { it.value }.take(maxAlbums).map { (id, count) ->
            val name = albumNames.getValue(id)
            // Quoted, so a name like "WhatsApp Images" is looked for whole, not as "WhatsApp" + photos.
            Suggestion(name, "\"$name\"", count)
        },
        years = yearCounts.entries.sortedByDescending { it.key }.map { (year, count) ->
            Suggestion(year.toString(), year.toString(), count)
        },
    )
}
