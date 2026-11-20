package com.rohit.gallery.core.domain

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.provider.MediaStore
import com.rohit.gallery.core.data.db.CreatedFolderDao
import com.rohit.gallery.core.data.db.CreatedFolderEntity
import com.rohit.gallery.core.data.db.MediaOriginDao
import com.rohit.gallery.core.data.db.MediaOriginEntity
import com.rohit.gallery.core.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Where moved media goes: a folder path relative to the storage root, like `Pictures/Trip/`. */
/** [isNew]: this move makes the folder (from [MediaTransfer.newAlbum]); it is then remembered as one the user created. */
data class Destination(val relativePath: String, val isNew: Boolean = false)

data class TransferResult(val done: Int, val failed: Int)

/** Normalises a folder path to the form MediaStore wants: no leading slash, one trailing slash. */
fun normalizeRelativePath(path: String): String = path.trim('/').let { if (it.isEmpty()) "" else "$it/" }

/**
 * Why a typed album name can't be used, or null if it is fine. [existingNames] are the albums already there:
 * a new album with one of those names (folders ignore upper / lower case) would quietly pour into that folder.
 * [folder] false checks a user album's name (only a label in the app's database): any characters are fine there,
 * and a name another user album has is refused the same way, ignoring case, so two albums never look alike.
 */
fun albumNameError(name: String, existingNames: Collection<String> = emptyList(), folder: Boolean = true): String? {
    val n = name.trim()
    return when {
        n.isEmpty() -> "Enter a name"
        n.length > 60 -> "Name is too long"
        folder && n.startsWith(".") -> "Name can't start with a dot"
        folder && n.any { it == '/' || it == '\\' || it == ':' || it == '*' || it == '?' || it == '"' || it == '<' || it == '>' || it == '|' } ->
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
 * What a move into [destPath] can take, and a note on what it leaves out (null when it takes everything):
 * - photos in an app's own folder (WhatsApp Images...) never move: Android refuses ("changing ownership not allowed"),
 *   so they are skipped up front, with a pointer to albums (adding to an album never moves a file);
 * - photos or videos the destination folder doesn't accept (see [folderAccepts]).
 */
fun movable(items: List<MediaItem>, destPath: String): Pair<List<MediaItem>, String?> {
    val (inApp, rest) = items.partition { it.isInAppFolder }
    val (ok, refused) = rest.partition { folderAccepts(destPath, it.isVideo) }
    val notes = buildList {
        if (inApp.isNotEmpty()) {
            val from = inApp.map { it.bucketName }.distinct().singleOrNull()?.takeIf { it.isNotBlank() } ?: "app folders"
            val them = if (inApp.size == 1) "it" else "them"
            add("${inApp.size} from $from can't be moved (Android keeps them there), add $them to an album instead")
        }
        if (refused.isNotEmpty()) {
            val what = when {
                refused.all { it.isVideo } -> "videos"
                refused.none { it.isVideo } -> "photos"
                else -> "photos or videos"
            }
            add("${refused.size} skipped: Android doesn't allow $what in ${folderLabel(destPath)}")
        }
    }
    return ok to notes.joinToString(". ").ifEmpty { null }
}

/**
 * Moves media between device folders.
 *
 * Originals are never modified in place: a move only changes the item's folder (after the system has asked for write
 * access). User albums never come here: they are lists in the app's database (UserAlbumRepository).
 */
@Singleton
class MediaTransfer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val origins: MediaOriginDao,
    private val createdFolders: CreatedFolderDao,
) {
    private val resolver get() = context.contentResolver

    /** The folder an existing album lives in, read from one of its items. Null if it can't be read. */
    suspend fun destinationOf(sample: MediaItem): Destination? = withContext(Dispatchers.IO) {
        currentFolder(sample)?.let(::Destination)
    }

    /** A new album [name] under Pictures. */
    fun newAlbum(name: String) = Destination(normalizeRelativePath("Pictures/${name.trim()}"), isNew = true)

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
        val created = HashSet<String>()
        val now = System.currentTimeMillis()
        for ((i, move) in moves.withIndex()) {
            ensureActive()
            val (item, destination) = move
            val from = if (rememberOrigins) currentFolder(item) else null
            val landed = candidatePaths(item, destination).firstOrNull { path ->
                runCatching {
                    val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, path) }
                    resolver.update(item.uri, values, null, null) > 0
                }.getOrDefault(false)
            }
            if (landed != null) {
                done++
                if (destination.isNew) created += normalizeRelativePath(landed)
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
                if (created.isNotEmpty()) createdFolders.insertAll(created.map { CreatedFolderEntity(it, now) })
                returned.chunked(500).forEach { origins.deleteAll(it) }
            }
        }
        TransferResult(done, moves.size - done)
    }

    /** Folders made through this app (normalised paths like `Pictures/Trip/`); empty if they can't be read. */
    suspend fun createdFolderPaths(): Set<String> = withContext(Dispatchers.IO) {
        runCatching { createdFolders.getAll().map { it.relativePath }.toSet() }.getOrDefault(emptySet())
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
