package com.rohit.gallery.core.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.staticCompositionLocalOf
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.model.mediaContentUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Identity of a cached thumbnail. [modifiedMs] makes an edited file miss the cache. */
data class ThumbKey(val id: Long, val modifiedMs: Long)

val MediaItem.thumbKey: ThumbKey get() = ThumbKey(id, dateModifiedMs)

/**
 * In-memory cache of grid thumbnails, read synchronously at composition time so a cell that
 * has been loaded once shows immediately in its first frame when it scrolls back into view.
 *
 * Thumbnails come from [android.content.ContentResolver.loadThumbnail], i.e. MediaStore's own
 * thumbnail cache, which is much cheaper than decoding originals.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ThumbnailCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val cache = object : LruCache<ThumbKey, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: ThumbKey, value: Bitmap): Int = value.byteCount
    }

    // Visible cells get their own lane so queued prefetch work can never delay them.
    private val visibleDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val prefetchDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

    fun get(key: ThumbKey): Bitmap? = cache.get(key)

    /** Loads (or returns the cached) thumbnail for a cell currently on screen. */
    suspend fun load(item: MediaItem, cellSizePx: Int): Bitmap = load(item, cellSizePx, visibleDispatcher)

    /** Warms the cache for a cell that is about to scroll into view. Failures are ignored. */
    suspend fun prefetch(item: MediaItem, cellSizePx: Int) {
        runCatching { load(item, cellSizePx, prefetchDispatcher) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    private suspend fun load(item: MediaItem, cellSizePx: Int, dispatcher: CoroutineDispatcher): Bitmap {
        val key = item.thumbKey
        cache.get(key)?.let { return it }
        return withContext(dispatcher) {
            // Another request (visible cell vs prefetch) may have finished while this one queued.
            cache.get(key) ?: loadFromMediaStore(item, cellSizePx).also { cache.put(key, it) }
        }
    }

    private suspend fun loadFromMediaStore(item: MediaItem, cellSizePx: Int): Bitmap {
        val uri = mediaContentUri(item.id, item.isVideo)
        val box = boxSize(item, cellSizePx)
        return suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            try {
                cont.resume(context.contentResolver.loadThumbnail(uri, Size(box, box), signal))
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }
    }

    /**
     * loadThumbnail fits the image inside a box, so for a landscape or portrait photo the short
     * side would end up smaller than the (square, cropped) cell. Grow the box by the aspect
     * ratio so the short side still covers the cell and nothing is upscaled.
     */
    private fun boxSize(item: MediaItem, cellSizePx: Int): Int {
        val shortSide = minOf(item.width, item.height)
        val longSide = maxOf(item.width, item.height)
        if (shortSide <= 0) return cellSizePx
        val ratio = (longSide.toFloat() / shortSide).coerceIn(1f, MAX_ASPECT_RATIO)
        return (cellSizePx * ratio).toInt()
    }

    private companion object {
        // About 7 screens of 4-column thumbnails.
        const val MAX_BYTES = 96 * 1024 * 1024
        const val MAX_ASPECT_RATIO = 2.5f
    }
}

val LocalThumbnailCache = staticCompositionLocalOf<ThumbnailCache> {
    error("ThumbnailCache not provided")
}
