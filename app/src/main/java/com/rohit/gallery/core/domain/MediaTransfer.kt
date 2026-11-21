package com.rohit.gallery.core.domain

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import com.rohit.gallery.core.data.db.MediaOriginDao
import com.rohit.gallery.core.data.db.MediaOriginEntity
import com.rohit.gallery.core.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Where moved media goes: a folder path relative to the storage root, like `Pictures/Trip/`. */
data class Destination(val relativePath: String)

data class TransferResult(val done: Int, val failed: Int)

/** Normalises a folder path to the form MediaStore wants: no leading slash, one trailing slash. */
fun normalizeRelativePath(path: String): String = path.trim('/').let { if (it.isEmpty()) "" else "$it/" }

/**
 * Why a typed album name can't be used, or null if it is fine. [existingNames] are the albums already there:
 * a new album with one of those names (folders ignore upper / lower case) would quietly pour into that folder.
 */
fun albumNameError(name: String, existingNames: Collection<String> = emptyList()): String? {
    val n = name.trim()
    return when {
        n.isEmpty() -> "Enter a name"
        n.length > 60 -> "Name is too long"
        n.startsWith(".") -> "Name can't start with a dot"
        n.any { it == '/' || it == '\\' || it == ':' || it == '*' || it == '?' || it == '"' || it == '<' || it == '>' || it == '|' } ->
            "Name can't contain / \\ : * ? \" < > |"
        existingNames.any { it.trim().equals(n, ignoreCase = true) } -> "An album with this name already exists"
        else -> null
    }
}

/**
 * Whether Android lets an app put a photo ([isVideo] false) or video into [relativePath]. MediaStore only takes
 * photos under DCIM/ or Pictures/, and videos under DCIM/, Movies/ or Pictures/; anything else (Download/,
 * Android/media/..., Music/) is refused.
 */
fun folderAccepts(relativePath: String, isVideo: Boolean): Boolean {
    val top = relativePath.trim('/').substringBefore('/')
    val allowed = if (isVideo) VIDEO_FOLDERS else IMAGE_FOLDERS
    return allowed.any { it.equals(top, ignoreCase = true) }
}

private val IMAGE_FOLDERS = listOf("DCIM", "Pictures")
private val VIDEO_FOLDERS = listOf("DCIM", "Movies", "Pictures")

/** "Download" for `Download/`, "WhatsApp Images" for `Android/media/.../WhatsApp Images/`: for messages. */
fun folderLabel(relativePath: String): String = relativePath.trim('/').substringAfterLast('/').ifEmpty { relativePath }

/**
 * What a move into [destPath] can take, and a note on what it leaves out (null when it takes everything): photos or
 * videos the destination folder doesn't accept (see [folderAccepts]). Photos in another app's folder (WhatsApp Images...)
 * are taken too: they move by copy, verify, delete (see [MediaTransfer.moveEach]).
 */
fun movable(items: List<MediaItem>, destPath: String): Pair<List<MediaItem>, String?> {
    val (ok, refused) = items.partition { folderAccepts(destPath, it.isVideo) }
    if (refused.isEmpty()) return ok to null
    val what = when {
        refused.all { it.isVideo } -> "videos"
        refused.none { it.isVideo } -> "photos"
        else -> "photos or videos"
    }
    return ok to "${refused.size} skipped: Android doesn't allow $what in ${folderLabel(destPath)}"
}

/** One item that was copied into its destination and verified byte for byte; its original still waits to be deleted. */
class FallbackCopy internal constructor(
    val original: MediaItem,
    internal val copyUri: Uri,
    internal val copyId: Long,
    /** The folder the original was in, and the folder the copy landed in (normalised), for the origin bookkeeping. */
    internal val from: String?,
    internal val to: String,
)

/**
 * Verified copies whose originals are still to be deleted. Nothing is deleted yet: ask Android with
 * [MediaTransfer.deleteRequest], then settle with [MediaTransfer.finishFallback] (or [MediaTransfer.discard]).
 */
class FallbackBatch internal constructor(val copies: List<FallbackCopy>, internal val rememberOrigins: Boolean)

/**
 * What [MediaTransfer.moveEach] did: [done] items moved in place, [failed] that neither moved nor copied, and the
 * copies [fallback] that still wait for their originals to be deleted (null when there are none).
 */
class MoveOutcome(val done: Int, val failed: Int, val fallback: FallbackBatch?)

/** After the delete: [moved] items are in the new folder for good, [notMoved] stayed where they were. */
class FallbackResult(val moved: Int, val notMoved: Int)

/**
 * Moves media between device folders.
 *
 * Originals are never modified in place. A move first tries the normal way: change the item's folder (after the system
 * has asked for write access). Android refuses that for photos another app owns (WhatsApp Images...: "changing
 * ownership not allowed"); those are copied into the destination instead, the copy is verified byte for byte, and only
 * then is the original deleted, with one `createDeleteRequest` for all of them (no Trash; by the owner's decision, see
 * DECISIONS "Move to album"). A copy whose original is still there afterwards (delete declined) is removed again.
 */
@Singleton
class MediaTransfer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val origins: MediaOriginDao,
) {
    private val resolver get() = context.contentResolver

    /** The folder an existing album lives in, read from one of its items. Null if it can't be read. */
    suspend fun destinationOf(sample: MediaItem): Destination? = withContext(Dispatchers.IO) {
        currentFolder(sample)?.let(::Destination)
    }

    /** A new album [name] under Pictures. */
    fun newAlbum(name: String) = Destination(normalizeRelativePath("Pictures/${name.trim()}"))

    /** System confirmation to let this app change [items] (needed before a move). */
    suspend fun writeRequest(items: List<MediaItem>): IntentSender = withContext(Dispatchers.IO) {
        MediaStore.createWriteRequest(resolver, items.map { it.uri }).intentSender
    }

    /**
     * Moves [items] into [destination]. Call only after the write request was accepted. [onProgress] gets how many
     * are handled so far. Cancelling stops before the next item (a copy also stops mid-file).
     */
    suspend fun move(
        items: List<MediaItem>,
        destination: Destination,
        onProgress: (handled: Int, done: Int) -> Unit = { _, _ -> },
    ): MoveOutcome = moveEach(items.map { it to destination }, rememberOrigins = true, onProgress)

    /**
     * Moves each item into its own destination (Remove album sends photos back where each came from). With
     * [rememberOrigins] the folder each photo leaves is saved, so a later Remove album can send it back there;
     * without, any saved origin of a moved photo is dropped (it has gone back).
     *
     * Each item is tried in place first; if Android refuses, it is copied and verified (see [FallbackBatch]), and the
     * originals of all such items are deleted afterwards in one go. Cancelling removes the copies made so far.
     */
    suspend fun moveEach(
        moves: List<Pair<MediaItem, Destination>>,
        rememberOrigins: Boolean,
        onProgress: (handled: Int, done: Int) -> Unit = { _, _ -> },
    ): MoveOutcome = withContext(Dispatchers.IO) {
        val job = coroutineContext[Job]
        var done = 0
        val newOrigins = ArrayList<MediaOriginEntity>()
        val returned = ArrayList<Long>()
        val copies = ArrayList<FallbackCopy>()
        val now = System.currentTimeMillis()
        var finished = false
        try {
            for ((i, move) in moves.withIndex()) {
                ensureActive()
                val (item, destination) = move
                val from = currentFolder(item)
                val landed = candidatePaths(item, destination).firstOrNull { path ->
                    runCatching {
                        val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, path) }
                        resolver.update(item.uri, values, null, null) > 0
                    }.getOrDefault(false)
                }
                if (landed != null) {
                    done++
                    when {
                        !rememberOrigins -> returned += item.id
                        from != null && from != normalizeRelativePath(destination.relativePath) ->
                            newOrigins += MediaOriginEntity(item.id, from, now)
                    }
                } else {
                    copyInstead(item, destination, from) { job?.isActive != false }?.let { copies += it }
                }
                onProgress(i + 1, done + copies.size)
            }
            finished = true
        } finally {
            withContext(NonCancellable) {
                // Stopped or failed midway: the copies made so far go (their originals were never touched).
                if (!finished) copies.forEach(::removeCopy)
                // Bookkeeping only: a failure here never undoes a move.
                runCatching {
                    if (newOrigins.isNotEmpty()) origins.upsertAll(newOrigins)
                    returned.chunked(500).forEach { origins.deleteAll(it) }
                }
            }
        }
        MoveOutcome(done, moves.size - done - copies.size, copies.takeIf { it.isNotEmpty() }?.let { FallbackBatch(it, rememberOrigins) })
    }

    /** Android's confirmation to delete the originals of [batch] (their verified copies are in place already). */
    suspend fun deleteRequest(batch: FallbackBatch): IntentSender = withContext(Dispatchers.IO) {
        MediaStore.createDeleteRequest(resolver, batch.copies.map { it.original.uri }).intentSender
    }

    /**
     * After the delete confirmation (accepted or not): an item whose original is gone is moved; where the original is
     * still there (declined, or the delete failed) its copy is removed again, so no duplicate is left. A copy is
     * only ever removed when its original is certainly still there, never on a doubt.
     */
    suspend fun finishFallback(batch: FallbackBatch): FallbackResult = withContext(NonCancellable + Dispatchers.IO) {
        var moved = 0
        var notMoved = 0
        val newOrigins = ArrayList<MediaOriginEntity>()
        val returned = ArrayList<Long>()
        val now = System.currentTimeMillis()
        for (copy in batch.copies) {
            if (originalExists(copy.original) == true) {
                removeCopy(copy)
                notMoved++
            } else {
                moved++
                when {
                    !batch.rememberOrigins -> returned += copy.original.id
                    copy.from != null && copy.from != copy.to -> newOrigins += MediaOriginEntity(copy.copyId, copy.from, now)
                }
            }
        }
        runCatching {
            if (newOrigins.isNotEmpty()) origins.upsertAll(newOrigins)
            returned.chunked(500).forEach { origins.deleteAll(it) }
        }
        FallbackResult(moved, notMoved)
    }

    /** Removes every copy of [batch] (the delete request couldn't be made): the originals stay, nothing is moved. */
    suspend fun discard(batch: FallbackBatch) = withContext(NonCancellable + Dispatchers.IO) {
        batch.copies.forEach(::removeCopy)
    }

    /** Where each of [items] came from, by media id (only photos this app moved). */
    suspend fun originsOf(items: List<MediaItem>): Map<Long, String> = withContext(Dispatchers.IO) {
        runCatching {
            items.map { it.id }.chunked(500).flatMap { origins.getAll(it) }.associate { it.mediaId to it.relativePath }
        }.getOrDefault(emptyMap())
    }

    /** The folder [item] is in now, like `Pictures/Trip/`. */
    private fun currentFolder(item: MediaItem): String? = runCatching {
        resolver.query(item.uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let(::normalizeRelativePath)?.takeIf { p -> p.isNotEmpty() } else null
        }
    }.getOrNull()

    /** Whether [item] is still in MediaStore; null if that can't be checked right now. */
    private fun originalExists(item: MediaItem): Boolean? = runCatching {
        resolver.query(item.uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count > 0 }
    }.getOrNull()

    private fun removeCopy(copy: FallbackCopy) {
        runCatching { resolver.delete(copy.copyUri, null, null) }
    }

    /** The copy-and-verify step for one [item] that couldn't move in place; null if it couldn't be copied. */
    private fun copyInstead(item: MediaItem, destination: Destination, from: String?, active: () -> Boolean): FallbackCopy? {
        for (path in candidatePaths(item, destination)) {
            val target = try {
                copyVerified(item, path, active)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: continue
            return FallbackCopy(item, target, ContentUris.parseId(target), from, normalizeRelativePath(path))
        }
        return null
    }

    /**
     * Writes a new MediaStore item for [item] in [relativePath] (hidden as pending until it is complete), checks that
     * it holds exactly the same bytes as the original, then publishes it and carries over the dates and the favourite
     * mark. A copy that doesn't verify (or any failure, or Stop) is deleted: no half-written file is left behind.
     */
    private fun copyVerified(item: MediaItem, relativePath: String, active: () -> Boolean): Uri? {
        val name = item.displayName.ifBlank { queryDisplayName(item.uri) } ?: return null
        val collection = if (item.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueDisplayName(name, namesIn(relativePath)))
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            if (item.dateTakenMs > 0) put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTakenMs)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(collection, values) ?: return null
        try {
            val input = resolver.openInputStream(item.uri) ?: error("no input")
            val output = resolver.openOutputStream(target) ?: error("no output")
            input.use { src ->
                output.use { dst ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        if (!active()) throw CancellationException("Move stopped")
                        val read = src.read(buffer)
                        if (read < 0) break
                        dst.write(buffer, 0, read)
                    }
                }
            }
            // Byte for byte, before anything is published, let alone deleted.
            val same = resolver.openInputStream(item.uri)?.use { a ->
                resolver.openInputStream(target)?.use { b -> streamsEqual(a, b) }
            } ?: false
            check(same) { "The copy doesn't match the original" }
            // MediaProvider ignores any later change to the date columns (even from the app that made the row), and
            // publishing re-reads the file's own time. So the original's file time is put on the pending file first:
            // a WhatsApp photo (no capture date) then keeps the date it was received, not "now".
            if (item.dateModifiedMs > 0) runCatching {
                val path = resolver.query(target, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
                if (path != null) java.io.File(path).setLastModified(item.dateModifiedMs)
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            if (item.isFavorite) runCatching {
                val favorite = ContentValues().apply { put(MediaStore.MediaColumns.IS_FAVORITE, 1) }
                resolver.update(target, favorite, null, null)
            }
            return target
        } catch (e: Throwable) {
            // Never leave a half-written or unverified copy behind; the original is untouched.
            runCatching { resolver.delete(target, null, null) }
            throw e
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    /** The file names already in [relativePath] (photos and videos), so a copy can pick a name that is free. */
    private fun namesIn(relativePath: String): List<String> {
        val names = ArrayList<String>()
        val collections = listOf(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        )
        for (collection in collections) runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(relativePath),
                null,
            )?.use { while (it.moveToNext()) it.getString(0)?.let(names::add) }
        }
        return names
    }

    /**
     * Folders to try for [item]. The album's own folder first; a video can't live under Pictures on
     * every device, so Movies with the same sub-folder is the fallback.
     */
    private fun candidatePaths(item: MediaItem, destination: Destination): List<String> {
        val path = destination.relativePath
        return if (item.isVideo && path.startsWith("Pictures/")) {
            listOf(path, "Movies/" + path.removePrefix("Pictures/"))
        } else {
            listOf(path)
        }
    }
}
