package com.rohit.gallery.core.model

import androidx.compose.runtime.Immutable

/** How the Albums tab orders albums that are not pinned. */
enum class AlbumSort(val label: String) {
    Newest("Newest photo first"),
    Name("Name A–Z"),
    MostItems("Most items"),
    Custom("Custom order"),
}

/** The user's choices for one album (app-owned data; the folder itself is never touched). */
data class AlbumPref(
    /** When it was pinned; pins are shown in this order, so a new pin goes after the earlier ones. */
    val pinnedAt: Long? = null,
    /** Place in the custom order, or null if never placed (new albums lead the custom order). */
    val position: Int? = null,
)

/** An album with the user's choices, for the Albums tab and the Edit albums screen. */
@Immutable
data class AlbumEntry(val album: Album, val pinned: Boolean)

/**
 * All albums in display order: [pinned] (in pin order) then [others] (in the chosen sort). [visible] is both, in that order.
 */
@Immutable
data class OrganizedAlbums(val pinned: List<AlbumEntry>, val others: List<AlbumEntry>, val sort: AlbumSort) {
    val visible: List<AlbumEntry> = pinned + others
}

/** Orders [albums] (as [buildAlbums] returns them: newest photo first) by the user's [prefs] and [sort]. */
fun organizeAlbums(albums: List<Album>, prefs: Map<Long, AlbumPref>, sort: AlbumSort): OrganizedAlbums {
    fun entry(album: Album): AlbumEntry {
        val pref = prefs[album.bucketId]
        return AlbumEntry(album, pinned = pref?.pinnedAt != null)
    }

    val (pinned, others) = albums.partition { prefs[it.bucketId]?.pinnedAt != null }
    val pinnedOrdered = pinned.sortedBy { prefs.getValue(it.bucketId).pinnedAt }
    val othersOrdered = when (sort) {
        AlbumSort.Newest -> others
        AlbumSort.Name -> others.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        AlbumSort.MostItems -> others.sortedByDescending { it.count }
        AlbumSort.Custom -> {
            val (placed, unplaced) = others.partition { prefs[it.bucketId]?.position != null }
            unplaced + placed.sortedBy { prefs.getValue(it.bucketId).position }
        }
    }
    return OrganizedAlbums(pinnedOrdered.map(::entry), othersOrdered.map(::entry), sort)
}
