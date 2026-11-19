package com.rohit.gallery.core.domain

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.provider.MediaStore
import com.rohit.gallery.core.data.db.TrashSnapshotDao
import com.rohit.gallery.core.data.db.TrashSnapshotEntity
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.di.ApplicationScope
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.model.mediaContentUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A restored photo whose date or favourite mark changed on the way through Trash, and what it had before. */
data class Repair(val item: MediaItem, val snapshot: TrashSnapshotEntity)

/** What [findRepairs] found: photos to put right, and snapshots that are no longer needed. */
data class RepairPlan(val repairs: List<Repair>, val settled: List<Long>)

/**
 * Compares the library ([items], not in Trash) with what photos looked like when they went to Trash. A photo back in
 * the library with another date or favourite mark needs repair. One that came back unchanged (or whose trash was
 * declined) no longer needs its snapshot, once the snapshot is older than [settleAfterMs]: until then Android's
 * trash prompt may still be open, and the photo is still in the library only because it hasn't gone yet.
 */
fun findRepairs(items: List<MediaItem>, snapshots: List<TrashSnapshotEntity>, now: Long, settleAfterMs: Long = SETTLE_MS): RepairPlan {
    if (snapshots.isEmpty()) return RepairPlan(emptyList(), emptyList())
    val byId = snapshots.associateBy { it.mediaId }
    val repairs = ArrayList<Repair>()
    val settled = ArrayList<Long>()
    for (item in items) {
        val snap = byId[item.id] ?: continue
        val dateLost = snap.shownDateMs > 0 && item.sortDateMs != snap.shownDateMs
        val favoriteLost = item.isFavorite != snap.isFavorite
        when {
            dateLost || favoriteLost -> repairs += Repair(item, snap)
            now - snap.savedAt > settleAfterMs -> settled += item.id
        }
    }
    return RepairPlan(repairs, settled)
}

private const val SETTLE_MS = 10L * 60 * 1000

/**
 * Puts back what a trip through Trash lost. Android re-reads a file when it is restored, and a photo with no date
 * inside the file (WhatsApp strips it) loses the date it had and jumps to another day. [MediaActions.trashRequest]
 * saves each photo's date and favourite mark; after any restore (Undo, Trash screen, viewer) [plans] spots the
 * difference and [apply] writes the saved values back. Only MediaStore's own columns change, never the file.
 */
@Singleton
class RestoreRepair @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val snapshots: TrashSnapshotDao,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    /** [apply] in the app's scope (from an activity-result callback). */
    fun applyAsync(repairs: List<Repair>) {
        scope.launch { apply(repairs) }
    }

    /** [forget] in the app's scope. */
    fun forgetAsync(ids: List<Long>) {
        scope.launch { forget(ids) }
    }

    private val resolver get() = context.contentResolver

    val plans: Flow<RepairPlan> = combine(repository.state, snapshots.observeAll()) { state, snaps ->
        val items = (state as? MediaIndexState.Ready)?.items.orEmpty()
        findRepairs(items, snaps, System.currentTimeMillis())
    }.flowOn(Dispatchers.Default)

    /** Write access to the photos to repair (silent with "Skip confirmation pop-ups" on). */
    suspend fun writeRequest(repairs: List<Repair>): IntentSender? = withContext(Dispatchers.IO) {
        runCatching { MediaStore.createWriteRequest(resolver, repairs.map { uriOf(it) }).intentSender }.getOrNull()
    }

    /** Writes the saved date and favourite back. Snapshots go either way, so a failure never asks again and again. */
    suspend fun apply(repairs: List<Repair>) = withContext(Dispatchers.IO) {
        for (repair in repairs) {
            val snap = repair.snapshot
            val values = ContentValues().apply {
                if (snap.shownDateMs > 0) put(MediaStore.MediaColumns.DATE_TAKEN, snap.shownDateMs)
                put(MediaStore.MediaColumns.IS_FAVORITE, if (snap.isFavorite) 1 else 0)
            }
            runCatching { resolver.update(uriOf(repair), values, null, null) }
        }
        forget(repairs.map { it.item.id })
        repository.refresh()
    }

    /** Drops snapshots that are done with (repaired, declined, or came back unchanged). */
    suspend fun forget(ids: List<Long>) {
        runCatching { ids.chunked(500).forEach { snapshots.deleteAll(it) } }
    }

    private fun uriOf(repair: Repair) = mediaContentUri(repair.item.id, repair.snapshot.isVideo)
}
