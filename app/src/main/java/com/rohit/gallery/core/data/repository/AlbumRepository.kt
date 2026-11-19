package com.rohit.gallery.core.data.repository

import com.rohit.gallery.core.data.db.AlbumPrefDao
import com.rohit.gallery.core.data.db.AlbumPrefEntity
import com.rohit.gallery.core.data.prefs.AppPreferences
import com.rohit.gallery.core.di.ApplicationScope
import com.rohit.gallery.core.model.AlbumPref
import com.rohit.gallery.core.model.AlbumSort
import com.rohit.gallery.core.model.OrganizedAlbums
import com.rohit.gallery.core.model.buildAlbums
import com.rohit.gallery.core.model.organizeAlbums
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/**
 * Albums derived from the in-memory media index (so always in step with the timeline), ordered and filtered
 * by the user's choices: pins, sort and custom order (Room + DataStore).
 */
@Singleton
class AlbumRepository @Inject constructor(
    repository: MediaRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val dao: AlbumPrefDao,
    private val prefs: AppPreferences,
) {
    /** Albums in display order with the user's choices; null while the index is loading. */
    val organized: Flow<OrganizedAlbums?> = combine(repository.state, dao.observeAll(), prefs.albumSort) { state, rows, sort ->
        if (state !is MediaIndexState.Ready) {
            null
        } else {
            val byBucket = rows.associate { it.bucketId to AlbumPref(it.pinnedAt, it.position) }
            organizeAlbums(buildAlbums(state.items), byBucket, sort)
        }
    }.flowOn(Dispatchers.Default)

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

    /**
     * Drops the pins and custom places of deleted albums, so a new album that later gets the same folder (same
     * bucket id) starts fresh. Returns what was dropped, for [restore] on Undo.
     */
    suspend fun forget(bucketIds: Collection<Long>): List<AlbumPrefEntity> {
        val ids = bucketIds.toSet()
        val dropped = dao.getAll().filter { it.bucketId in ids }
        if (dropped.isNotEmpty()) dao.deleteAll(dropped.map { it.bucketId })
        return dropped
    }

    /** Puts back what [forget] dropped (the delete was undone). */
    suspend fun restore(prefs: List<AlbumPrefEntity>) {
        if (prefs.isNotEmpty()) dao.upsertAll(prefs)
    }

    /** [forget] in the app's scope, for callers whose screen is closing. */
    fun forgetAsync(bucketIds: Collection<Long>): Deferred<List<AlbumPrefEntity>> = scope.async { forget(bucketIds) }

    /** [restore] what [forgetAsync] dropped, in the app's scope. */
    fun restoreAsync(dropped: Deferred<List<AlbumPrefEntity>>) {
        scope.launch { restore(dropped.await()) }
    }

    /** Applies [change] to each album in order, in one write. */
    private suspend fun updateAll(bucketIds: Collection<Long>, change: (AlbumPrefEntity) -> AlbumPrefEntity) {
        val existing = dao.getAll().associateBy { it.bucketId }
        dao.upsertAll(bucketIds.map { id -> change(existing[id] ?: AlbumPrefEntity(id)) })
    }
}
