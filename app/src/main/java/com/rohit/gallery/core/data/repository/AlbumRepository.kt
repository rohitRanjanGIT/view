package com.rohit.gallery.core.data.repository

import com.rohit.gallery.core.data.db.AlbumPrefDao
import com.rohit.gallery.core.data.db.AlbumPrefEntity
import com.rohit.gallery.core.data.prefs.AppPreferences
import com.rohit.gallery.core.model.Album
import com.rohit.gallery.core.model.AlbumPref
import com.rohit.gallery.core.model.AlbumSort
import com.rohit.gallery.core.model.OrganizedAlbums
import com.rohit.gallery.core.model.buildAlbums
import com.rohit.gallery.core.model.organizeAlbums
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Albums derived from the in-memory media index (so always in step with the timeline), ordered and filtered
 * by the user's choices: pins, hidden albums, sort and custom order (Room + DataStore).
 */
@Singleton
class AlbumRepository @Inject constructor(
    repository: MediaRepository,
    private val dao: AlbumPrefDao,
    private val prefs: AppPreferences,
) {
    /** Every album, hidden ones included, newest photo first: the Move/Copy album picker. */
    val albums: Flow<List<Album>> = repository.allState
        .map { state -> if (state is MediaIndexState.Ready) buildAlbums(state.items) else emptyList() }
        .flowOn(Dispatchers.Default)

    /** Albums in display order with the user's choices; null while the index is loading. */
    val organized: Flow<OrganizedAlbums?> = combine(repository.allState, dao.observeAll(), prefs.albumSort) { state, rows, sort ->
        if (state !is MediaIndexState.Ready) {
            null
        } else {
            val byBucket = rows.associate { it.bucketId to AlbumPref(it.hidden, it.pinnedAt, it.position) }
            organizeAlbums(buildAlbums(state.items), byBucket, sort)
        }
    }.flowOn(Dispatchers.Default)

    suspend fun setHidden(bucketIds: Collection<Long>, hidden: Boolean) = updateAll(bucketIds) { it.copy(hidden = hidden) }

    /**
     * New pins get the current time (plus their place in [bucketIds]), so they go after the earlier pins and,
     * when pinned together, keep the order given. Albums already pinned keep their place.
     */
    suspend fun setPinned(bucketIds: List<Long>, pinned: Boolean) {
        val now = System.currentTimeMillis()
        var next = 0L
        updateAll(bucketIds) {
            when {
                !pinned -> it.copy(pinnedAt = null)
                it.pinnedAt != null -> it
                else -> it.copy(pinnedAt = now + next++)
            }
        }
    }

    suspend fun setSort(sort: AlbumSort) = prefs.setAlbumSort(sort)

    /** Saves [bucketIds] (the unpinned albums, in the order dragged) and switches the sort to Custom. */
    suspend fun saveCustomOrder(bucketIds: List<Long>) {
        val existing = dao.getAll().associateBy { it.bucketId }
        dao.upsertAll(bucketIds.mapIndexed { i, id -> (existing[id] ?: AlbumPrefEntity(id)).copy(position = i) })
        prefs.setAlbumSort(AlbumSort.Custom)
    }

    /** Applies [change] to each album in order, in one write. */
    private suspend fun updateAll(bucketIds: Collection<Long>, change: (AlbumPrefEntity) -> AlbumPrefEntity) {
        val existing = dao.getAll().associateBy { it.bucketId }
        dao.upsertAll(bucketIds.map { id -> change(existing[id] ?: AlbumPrefEntity(id)) })
    }
}
