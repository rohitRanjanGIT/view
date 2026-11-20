package com.rohit.gallery.feature.selection

import com.rohit.gallery.core.domain.normalizeRelativePath
import com.rohit.gallery.core.model.Album

/**
 * The device folders "Move to folder" offers: only those the user made through the app ([created] holds their
 * relative paths). [folders] pairs each library folder with its path (null when it couldn't be read: left out).
 * Paths match ignoring case; [currentBucket], the folder the selection is in, is no target for its own photos.
 * A created folder that no longer shows in the library (emptied or deleted) simply isn't in [folders].
 */
fun createdFolderChoices(folders: List<Pair<Album, String?>>, created: Set<String>, currentBucket: Long?): List<Album> {
    if (created.isEmpty()) return emptyList()
    val wanted = created.mapTo(HashSet()) { normalizeRelativePath(it).lowercase() }
    return folders.filter { (album, path) ->
        album.bucketId != currentBucket && path != null && normalizeRelativePath(path).lowercase() in wanted
    }.map { it.first }
}
