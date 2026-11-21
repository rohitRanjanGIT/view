package com.rohit.gallery.core.data.vault

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.rohit.gallery.core.data.db.VaultDao
import com.rohit.gallery.core.data.db.VaultItemEntity
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.domain.MediaTransfer
import com.rohit.gallery.core.domain.TransferResult
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.model.MediaType
import com.rohit.gallery.core.model.mediaContentUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Maps vault files to the content URIs the viewer, thumbnails and share use (served by our FileProvider). */
@Singleton
class VaultFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storage: VaultStorage,
) {
    private val authority = "${context.packageName}.vault"

    fun uriFor(fileName: String): Uri = FileProvider.getUriForFile(context, authority, storage.file(fileName))

    /** The vault file behind [uri], or null if it isn't one of ours. */
    fun fileFor(uri: Uri): File? =
        if (uri.authority == authority) uri.lastPathSegment?.let(storage::file)?.takeIf { it.isFile } else null
}

/**
 * Locked photos and videos. Locking *moves* them into the vault: each file is copied into app-private storage and the
 * copy verified byte for byte (see [VaultStorage]); only then is the original deleted from shared storage, through
 * Android's own delete confirmation. Unlocking copies a file back into its original folder (verified again) before
 * the vault copy is removed. At no point is the only copy of a photo deleted.
 */
@Singleton
class VaultRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: VaultDao,
    private val storage: VaultStorage,
    private val files: VaultFiles,
    private val transfer: MediaTransfer,
    private val media: MediaRepository,
) {
    private val resolver get() = context.contentResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Rows of a lock that is still waiting for its delete confirmation; recovery leaves them alone. */
    private val activeLocks: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())

    /** Everything locked, newest first, as index items; null until first read. */
    val items: StateFlow<List<MediaItem>?> = flow {
        recoverPending()
        emitAll(dao.observeCommitted())
    }
        .map { rows -> rows.map(::toMediaItem) }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** A lock in progress: the vault rows written so far and the originals still to delete. */
    class LockBatch internal constructor(val entityIds: List<Long>, val originals: List<MediaItem>, val failed: Int)

    /**
     * Step 1 of locking: copies [items] into the vault and verifies each copy. Nothing is deleted yet. Items that
     * can't be copied are skipped (and counted in [LockBatch.failed]). Cancelled midway, the copies made are removed.
     */
    suspend fun prepareLock(items: List<MediaItem>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): LockBatch =
        withContext(Dispatchers.IO) {
            val ids = mutableListOf<Long>()
            val originals = mutableListOf<MediaItem>()
            var failed = 0
            try {
                items.forEachIndexed { index, item ->
                    val id = runCatching { copyIn(item) }.getOrNull()
                    if (id != null) {
                        activeLocks += id
                        ids += id
                        originals += item
                    } else {
                        failed++
                    }
                    onProgress(index + 1, items.size)
                }
            } catch (e: Throwable) {
                withContext(NonCancellable) { rollBack(dao.get(ids)) }
                activeLocks -= ids.toSet()
                throw e
            }
            LockBatch(ids, originals, failed)
        }

    /** Step 2: Android's confirmation to delete the originals of [batch] (they are safely in the vault by now). */
    suspend fun deleteOriginalsRequest(batch: LockBatch): IntentSender = withContext(Dispatchers.IO) {
        MediaStore.createDeleteRequest(resolver, batch.originals.map { it.uri }).intentSender
    }

    /**
     * Step 3, after the confirmation (accepted or not): each item whose original is gone is locked; where the original
     * is still there (declined), the vault copy is removed again. Returns how many were locked.
     */
    suspend fun finishLock(batch: LockBatch): Int = withContext(NonCancellable + Dispatchers.IO) {
        val locked = settle(dao.get(batch.entityIds))
        activeLocks -= batch.entityIds.toSet()
        media.refresh()
        locked
    }

    /** Puts [items] (vault items) back into their original folders, verified, then removes them from the vault. */
    suspend fun unlock(items: List<MediaItem>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): TransferResult =
        withContext(Dispatchers.IO) {
            val rows = dao.get(items.mapNotNull { VaultIds.entityIdOf(it.id) })
            var done = 0
            rows.forEachIndexed { index, row ->
                if (withContext(NonCancellable) { restore(row) }) {
                    storage.delete(row.fileName)
                    dao.delete(listOf(row.id))
                    done++
                }
                onProgress(index + 1, rows.size)
            }
            media.refresh()
            TransferResult(done, items.size - done)
        }

    /** Everything in the vault, back to shared storage. */
    suspend fun unlockAll(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): TransferResult =
        unlock(dao.committed().map(::toMediaItem), onProgress)

    /** Deletes [items] from the vault for good (the screen confirms first). */
    suspend fun deleteForever(items: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        val rows = dao.get(items.mapNotNull { VaultIds.entityIdOf(it.id) })
        rows.forEach { storage.delete(it.fileName) }
        dao.delete(rows.map { it.id })
        rows.size
    }

    private suspend fun copyIn(item: MediaItem): Long? {
        if (item.isExternal || item.isTrashed) return null
        val relativePath = transfer.destinationOf(item)?.relativePath ?: return null
        val displayName = item.displayName.ifBlank { queryDisplayName(item.uri) ?: "${item.id}" }
        val input = resolver.openInputStream(item.uri) ?: return null
        val stored = storage.store(input, extensionFor(displayName, item.mimeType))
        // A size that disagrees with the index means the file changed under us: don't take it.
        if (item.sizeBytes > 0 && stored.sizeBytes != item.sizeBytes) {
            storage.delete(stored.fileName)
            return null
        }
        return dao.insert(
            VaultItemEntity(
                fileName = stored.fileName,
                displayName = displayName,
                mimeType = item.mimeType,
                isVideo = item.isVideo,
                dateTakenMs = item.dateTakenMs,
                dateModifiedMs = item.dateModifiedMs,
                width = item.width,
                height = item.height,
                durationMs = item.durationMs,
                sizeBytes = stored.sizeBytes,
                sha256 = stored.sha256,
                originalRelativePath = relativePath,
                albumName = item.bucketName,
                originalMediaId = item.id,
                lockedAtMs = System.currentTimeMillis(),
                pending = true,
            ),
        )
    }

    /** Settles interrupted locks (the app died before or during the delete confirmation). */
    private suspend fun recoverPending() {
        storage.clearPartials()
        settle(dao.pending().filter { it.id !in activeLocks })
    }

    private suspend fun settle(rows: List<VaultItemEntity>): Int {
        var committed = 0
        for (row in rows) {
            when (pendingOutcome(originalExists(row))) {
                PendingOutcome.Commit -> {
                    dao.commit(listOf(row.id))
                    committed++
                }
                PendingOutcome.RollBack -> rollBack(listOf(row))
                PendingOutcome.Wait -> Unit
            }
        }
        return committed
    }

    private suspend fun rollBack(rows: List<VaultItemEntity>) {
        rows.forEach { storage.delete(it.fileName) }
        dao.delete(rows.map { it.id })
    }

    /** Whether the original of [row] is still in shared storage; null if that can't be checked right now. */
    private fun originalExists(row: VaultItemEntity): Boolean? = runCatching {
        resolver.query(mediaContentUri(row.originalMediaId, row.isVideo), arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.count > 0 }
    }.getOrNull()

    /** Writes [row]'s file into MediaStore (original folder first), checks it, and publishes it. */
    private fun restore(row: VaultItemEntity): Boolean {
        val source = storage.file(row.fileName)
        if (!storage.verify(row.fileName, row.sha256)) return false
        val collection = if (row.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val fallback = if (row.isVideo) "Movies/Restored/" else "Pictures/Restored/"
        return listOf(row.originalRelativePath, fallback).filter { it.isNotBlank() }.distinct().any { path ->
            runCatching { restoreTo(row, source, collection, path) }.getOrDefault(false)
        }
    }

    private fun restoreTo(row: VaultItemEntity, source: File, collection: Uri, relativePath: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, row.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, row.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            if (row.dateTakenMs > 0) put(MediaStore.MediaColumns.DATE_TAKEN, row.dateTakenMs)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(collection, values) ?: return false
        try {
            source.inputStream().use { src ->
                (resolver.openOutputStream(target) ?: error("no output")).use { dst -> src.copyTo(dst) }
            }
            val written = (resolver.openInputStream(target) ?: error("no input")).use(VaultStorage::sha256)
            if (written != row.sha256) error("The restored copy doesn't match")
            // MediaProvider ignores later changes to the date columns and re-reads the file's own time when the file
            // is published, so the original time goes on the pending file first (a WhatsApp photo, which has no
            // capture date, would otherwise come back dated "now").
            if (row.dateModifiedMs > 0) runCatching {
                val path = resolver.query(target, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
                if (path != null) File(path).setLastModified(row.dateModifiedMs)
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return true
        } catch (e: Exception) {
            // Never leave a half-written file behind; the vault copy is untouched.
            runCatching { resolver.delete(target, null, null) }
            throw e
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun toMediaItem(row: VaultItemEntity) = MediaItem(
        id = VaultIds.mediaIdOf(row.id),
        type = if (row.isVideo) MediaType.Video else MediaType.Image,
        mimeType = row.mimeType,
        dateTakenMs = row.dateTakenMs,
        dateModifiedMs = row.dateModifiedMs,
        width = row.width,
        height = row.height,
        durationMs = row.durationMs,
        bucketId = VaultIds.albumIdOf(row.albumName),
        bucketName = row.albumName,
        isFavorite = false,
        sizeBytes = row.sizeBytes,
        displayName = row.displayName,
        externalUri = files.uriFor(row.fileName).toString(),
    )

    private fun extensionFor(displayName: String, mimeType: String): String {
        val fromName = displayName.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 }
        val ext = fromName ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
        return ".$ext"
    }
}
