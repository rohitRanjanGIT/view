package com.rohit.gallery.core.data.repository

import com.rohit.gallery.core.data.db.AlbumPrefDao
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The bucket ids of albums the user hid. A seam so [MediaRepository] can be tested without a database. */
interface HiddenAlbums {
    val bucketIds: Flow<Set<Long>>
}

class RoomHiddenAlbums @Inject constructor(dao: AlbumPrefDao) : HiddenAlbums {
    override val bucketIds: Flow<Set<Long>> = dao.observeAll()
        .map { prefs -> prefs.filter { it.hidden }.mapTo(HashSet()) { it.bucketId } }
        .distinctUntilChanged()
}
