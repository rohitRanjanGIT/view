package com.rohit.gallery.core.ui.components

import android.os.CancellationSignal
import android.util.Size
import coil3.ImageLoader
import coil3.Image
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import com.rohit.gallery.core.model.mediaContentUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coil model for a grid thumbnail. [modifiedMs] is part of the cache key so an edited
 * file never shows a stale thumbnail.
 */
data class MediaThumbData(val id: Long, val isVideo: Boolean, val modifiedMs: Long)

class MediaThumbKeyer : Keyer<MediaThumbData> {
    override fun key(data: MediaThumbData, options: Options): String =
        "thumb:${data.id}:${data.isVideo}:${data.modifiedMs}"
}

/**
 * Loads grid thumbnails through [android.content.ContentResolver.loadThumbnail], which uses
 * MediaStore's own thumbnail cache. That is far cheaper than decoding the original photo or
 * extracting a video frame, and it works the same for images and videos.
 */
class MediaThumbFetcher(
    private val data: MediaThumbData,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val width = options.size.width.pxOrElse { DEFAULT_SIZE_PX }
        val height = options.size.height.pxOrElse { DEFAULT_SIZE_PX }
        val uri = mediaContentUri(data.id, data.isVideo)
        val resolver = options.context.contentResolver

        val bitmap = withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    cont.resume(resolver.loadThumbnail(uri, Size(width, height), signal))
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }
        }
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<MediaThumbData> {
        override fun create(data: MediaThumbData, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbFetcher(data, options)
    }

    private companion object {
        const val DEFAULT_SIZE_PX = 256
    }
}
