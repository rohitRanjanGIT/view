package com.rohit.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.video.VideoFrameDecoder
import com.rohit.gallery.core.ui.components.MediaThumbFetcher
import com.rohit.gallery.core.ui.components.MediaThumbKeyer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GalleryApp : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(MediaThumbKeyer())
                add(MediaThumbFetcher.Factory())
                add(VideoFrameDecoder.Factory())
                add(AnimatedImageDecoder.Factory())
            }
            // About 330 thumbnails (~290 KB each at 270px), well over three screens of the grid.
            .memoryCache { MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_BYTES).build() }
            .build()

    private companion object {
        const val MEMORY_CACHE_BYTES = 96L * 1024 * 1024
    }
}
