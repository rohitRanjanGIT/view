package com.rohit.gallery.core.domain

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.provider.MediaStore
import com.rohit.gallery.core.data.db.MediaOriginDao
import com.rohit.gallery.core.data.db.MediaOriginEntity
import com.rohit.gallery.core.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Where moved or copied media goes: a folder path relative to the storage root, like `Pictures/Trip/`. */
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
 * Moves and copies media between albums (folders).
 *
 * Originals are never modified in place: a *move* only changes the item's folder (after the system
 * has asked for write access), a *copy* creates a brand new MediaStore entry and streams the bytes
 * into it, leaving the source untouched.
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
     * are handled so far. Cancelling stops before the next item (each move is a single quick update).
     */
    suspend fun move(
        items: List<MediaItem>,
        destination: Destination,
        onProgress: (handled: Int, done: Int) -> Unit = { _, _ -> },
    ): TransferResult = moveEach(items.map { it to destination }, rememberOrigins = true, onProgress)

    /**
     * Moves each item into its own destination (Remove album sends photos back where each came from). With
     * [rememberOrigins] the folder each photo leaves is saved, so a later Remove album can send it back there;
     * without, any saved origin of a moved photo is dropped (it has gone back).
     */
    suspend fun moveEach(
        moves: List<Pair<MediaItem, Destination>>,
        rememberOrigins: Boolean,
        onProgress: (handled: Int, done: Int) -> Unit = { _, _ -> },
    ): TransferResult = withContext(Dispatchers.IO) {
        var done = 0
        val newOrigins = ArrayList<MediaOriginEntity>()
        val returned = ArrayList<Long>()
        val now = System.currentTimeMillis()
        for ((i, move) in moves.withIndex()) {
            ensureActive()
            val (item, destination) = move
            val from = if (rememberOrigins) currentFolder(item) else null
            val ok = candidatePaths(item, destination).any { path ->
                runCatching {
                    val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, path) }
                    resolver.update(item.uri, values, null, null) > 0
                }.getOrDefault(false)
            }
            if (ok) {
                done++
                when {
                    !rememberOrigins -> returned += item.id
                    from != null && from != normalizeRelativePath(destination.relativePath) ->
                        newOrigins += MediaOriginEntity(item.id, from, now)
                }
            }
            onProgress(i + 1, done)
        }
        // Bookkeeping only: a failure here never undoes a move.
        withContext(NonCancellable) {
            runCatching {
                if (newOrigins.isNotEmpty()) origins.upsertAll(newOrigins)
                returned.chunked(500).forEach { origins.deleteAll(it) }
            }
        }
        TransferResult(done, moves.size - done)
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

    /**
     * Copies [items] into [destination] as new files; the originals stay where they are. Cancelling stops mid-file
     * too (a big video), and the half-written copy is removed.
     */
    suspend fun copy(
        items: List<MediaItem>,
        destination: Destination,
        onProgress: (handled: Int, done: Int) -> Unit = { _, _ -> },
    ): TransferResult = withContext(Dispatchers.IO) {
        val job = coroutineContext[Job]
        var done = 0
        for ((i, item) in items.withIndex()) {
            ensureActive()
            val ok = candidatePaths(item, destination).any { path ->
                try {
                    copyOne(item, path) { job?.isActive != false }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
            }
            if (ok) done++
            onProgress(i + 1, done)
        }
        TransferResult(done, items.size - done)
    }

    private fun copyOne(item: MediaItem, relativePath: String, active: () -> Boolean): Boolean {
        val name = resolver.query(item.uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: return false

        val collection = if (item.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            if (item.dateTakenMs > 0) put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTakenMs)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(collection, values) ?: return false
        try {
            val input = resolver.openInputStream(item.uri) ?: error("no input")
            val output = resolver.openOutputStream(target) ?: error("no output")
            input.use { src ->
                output.use { dst ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        if (!active()) throw CancellationException("Copy stopped")
                        val read = src.read(buffer)
                        if (read < 0) break
                        dst.write(buffer, 0, read)
                    }
                }
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return true
        } catch (e: Exception) {
            // Never leave a half-written file behind.
            runCatching { resolver.delete(target, null, null) }
            throw e
        }
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
