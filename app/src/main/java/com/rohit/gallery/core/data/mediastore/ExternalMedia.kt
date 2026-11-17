package com.rohit.gallery.core.data.mediastore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Authority of MediaStore content URIs, e.g. `content://media/external/images/media/42`. */
const val MEDIA_STORE_AUTHORITY = "media"

/**
 * The MediaStore id of a URI handed to us by another app, or null when it is not a MediaStore item
 * (another app's FileProvider, a file path, ...). Pure string logic so it runs in plain unit tests.
 */
fun mediaStoreIdOf(scheme: String?, authority: String?, pathSegments: List<String>): Long? {
    if (scheme != "content" || authority != MEDIA_STORE_AUTHORITY) return null
    // Shapes: external/images/media/<id>, external_primary/video/media/<id>, external/file/<id>.
    if (pathSegments.size < 3) return null
    val kind = pathSegments[1]
    if (kind != "images" && kind != "video" && kind != "file") return null
    return pathSegments.last().toLongOrNull()?.takeIf { it > 0 }
}

/**
 * A stable id for an item that is not in the index. Negative so it can never collide with a
 * MediaStore id; derived from the URI so caches keyed by id (thumbnails, resume position) do not
 * mix up two different files opened in the same session.
 */
fun externalMediaId(uri: String): Long = -((uri.hashCode().toLong() and 0x7fff_ffffL) + 1)

/** True for image and video MIME types; anything else is not ours to show. */
fun isVisualMime(mime: String?): Boolean =
    mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))

/**
 * Describes a single URI opened from another app (ACTION_VIEW, camera review) as a [MediaItem],
 * using only the read grant that came with the intent: no media permission is needed.
 */
@Singleton
class ExternalMediaResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** True when the app may read at least part of the library (full or user-selected access). */
    fun canReadLibrary(): Boolean = listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    ).any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** Null when the URI cannot be read or is not a photo or video. */
    suspend fun describe(uriString: String, intentMime: String?): MediaItem? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        if (!canOpen(uri)) return@withContext null
        val mime = intentMime?.takeIf { isVisualMime(it) && !it.endsWith("/*") }
            ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()?.takeIf(::isVisualMime)
            ?: guessMimeFromName(uri.lastPathSegment)
            ?: intentMime?.takeIf(::isVisualMime)
            ?: return@withContext null
        val isVideo = mime.startsWith("video/")
        val meta = if (uri.scheme == "file") fileMeta(uri) else queryMeta(uri)
        var width = meta.width
        var height = meta.height
        if (!isVideo && (width <= 0 || height <= 0)) {
            imageBounds(uri)?.let { (w, h) -> width = w; height = h }
        }
        MediaItem(
            id = externalMediaId(uriString),
            type = if (isVideo) MediaType.Video else MediaType.Image,
            mimeType = mime,
            dateTakenMs = meta.dateTakenMs,
            dateModifiedMs = meta.dateModifiedMs,
            width = width,
            height = height,
            durationMs = meta.durationMs,
            bucketId = 0L,
            bucketName = "",
            isFavorite = false,
            sizeBytes = meta.sizeBytes,
            externalUri = uriString,
        )
    }

    private fun canOpen(uri: Uri): Boolean = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    private data class Meta(
        val dateTakenMs: Long = 0L,
        val dateModifiedMs: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
        val durationMs: Long = 0L,
        val sizeBytes: Long = 0L,
    )

    /** MediaStore URIs answer with their own columns; other providers usually only OpenableColumns. */
    private fun queryMeta(uri: Uri): Meta = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            Meta(
                dateTakenMs = c.long(MediaStore.MediaColumns.DATE_TAKEN),
                dateModifiedMs = c.long(MediaStore.MediaColumns.DATE_MODIFIED) * 1000,
                width = c.long(MediaStore.MediaColumns.WIDTH).toInt(),
                height = c.long(MediaStore.MediaColumns.HEIGHT).toInt(),
                durationMs = c.long(MediaStore.MediaColumns.DURATION),
                sizeBytes = c.long(OpenableColumns.SIZE),
            )
        }
    }.getOrNull() ?: Meta()

    private fun fileMeta(uri: Uri): Meta = runCatching {
        val file = File(requireNotNull(uri.path))
        Meta(dateModifiedMs = file.lastModified(), sizeBytes = file.length())
    }.getOrDefault(Meta())

    private fun imageBounds(uri: Uri): Pair<Int, Int>? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
        }
    }.getOrNull()

    private fun guessMimeFromName(name: String?): String? {
        val ext = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.takeIf(::isVisualMime)
    }
}

private fun Cursor.long(column: String): Long {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getLong(index) else 0L
}
