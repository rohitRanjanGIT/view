package com.rohit.gallery.core.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable

enum class MediaType { Image, Video }

/**
 * One lightweight row of the in-memory media index.
 *
 * The content [uri] is derived from [id] on demand instead of being stored, which keeps
 * 50k+ items cheap and this class free of Android framework objects (easy to unit test).
 */
@Immutable
data class MediaItem(
    val id: Long,
    val type: MediaType,
    val mimeType: String,
    val dateTakenMs: Long,
    val dateModifiedMs: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val isFavorite: Boolean,
    val sizeBytes: Long,
    /** True for items listed from the trash; their URI must say so to be readable. */
    val isTrashed: Boolean = false,
    /**
     * Set only for a file opened from another app that is not in the index (ACTION_VIEW, camera
     * review): its URI cannot be derived from [id]. A String keeps this class framework-free.
     */
    val externalUri: String? = null,
) {
    /** Date used for sorting and grouping: when it was taken, else when the file was modified. */
    val sortDateMs: Long get() = if (dateTakenMs > 0) dateTakenMs else dateModifiedMs

    val isVideo: Boolean get() = type == MediaType.Video

    val uri: Uri get() = externalUri?.let(Uri::parse)
        ?: mediaContentUri(id, isVideo).let { if (isTrashed) it.buildUpon().appendQueryParameter("includeTrashed", "1").build() else it }

    /** True for a file from another app that is shown on its own, outside the index. */
    val isExternal: Boolean get() = externalUri != null
}

fun mediaContentUri(id: Long, isVideo: Boolean): Uri = ContentUris.withAppendedId(
    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    id,
)
