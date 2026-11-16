package com.rohit.gallery.core.data.mediastore

import com.rohit.gallery.core.model.MediaItem
import kotlinx.coroutines.flow.Flow

/** Where the media index is read from. MediaStore in production, a fake in tests. */
interface MediaSource {
    /** Reads every image and video row. Safe to call from any dispatcher. */
    suspend fun queryAll(): List<MediaItem>

    /** Emits whenever the underlying media collection may have changed. */
    fun changes(): Flow<Unit>
}
