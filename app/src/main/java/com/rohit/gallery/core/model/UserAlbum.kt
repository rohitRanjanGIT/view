package com.rohit.gallery.core.model

import androidx.compose.runtime.Immutable

/**
 * An album the user made: a named list of photos kept by the app (no folder on the device). [items] are its members
 * that are in the library now, newest first: photos in Trash or Locked are left out until they come back. [cover] is
 * the chosen cover if it is still among them, else the newest; null when the album shows nothing.
 */
@Immutable
data class UserAlbum(
    val id: Long,
    val name: String,
    val items: List<MediaItem>,
    val cover: MediaItem?,
    val pinned: Boolean,
    val createdAt: Long,
) {
    val count: Int get() = items.size

    /** The same album in the shape the shared album widgets take (grid cells, strip, Edit albums). */
    fun asAlbum(): Album = Album(id, name, items.size, cover)

    fun asEntry(): AlbumEntry = AlbumEntry(asAlbum(), pinned)
}
