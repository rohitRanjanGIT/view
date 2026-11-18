package com.rohit.gallery.feature.viewer

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.size.Size
import com.rohit.gallery.core.model.MediaItem

/**
 * Starts decoding [item] for the viewer the moment it is tapped, while the viewer screen is still being
 * built and the open flight runs. The request matches the one Telephoto makes for a full-screen page
 * (same data, viewer size, inexact precision, no bitmap size cap), so its result lands in Coil's memory
 * cache under the key the viewer then looks up, and the sharp photo appears without a second decode.
 */
fun prefetchViewerImage(context: Context, item: MediaItem, widthPx: Int, heightPx: Int) {
    if (item.isVideo || widthPx <= 0 || heightPx <= 0) return
    val request = ImageRequest.Builder(context)
        .data(item.uri)
        .size(widthPx, heightPx)
        .precision(Precision.INEXACT)
        .maxBitmapSize(Size.ORIGINAL)
        .build()
    SingletonImageLoader.get(context).enqueue(request)
}
