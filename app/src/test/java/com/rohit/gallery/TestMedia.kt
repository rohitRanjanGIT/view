package com.rohit.gallery

import com.rohit.gallery.core.data.mediastore.MediaSource
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

fun testItem(
    id: Long,
    dateTakenMs: Long = 0L,
    dateModifiedMs: Long = 0L,
    type: MediaType = MediaType.Image,
    bucketId: Long = 1L,
    bucketName: String = "Camera",
) = MediaItem(
    id = id,
    type = type,
    mimeType = if (type == MediaType.Video) "video/mp4" else "image/jpeg",
    dateTakenMs = dateTakenMs,
    dateModifiedMs = dateModifiedMs,
    width = 100,
    height = 100,
    durationMs = 0L,
    bucketId = bucketId,
    bucketName = bucketName,
    isFavorite = false,
    sizeBytes = 1L,
)

class FakeMediaSource : MediaSource {
    var items: List<MediaItem> = emptyList()
    var failure: Exception? = null
    var queryCount = 0
        private set
    val changeEvents = MutableSharedFlow<Unit>()

    override suspend fun queryAll(): List<MediaItem> {
        queryCount++
        failure?.let { throw it }
        return items
    }

    override fun changes(): Flow<Unit> = changeEvents
}

class FakeHiddenAlbums : com.rohit.gallery.core.data.repository.HiddenAlbums {
    override val bucketIds = kotlinx.coroutines.flow.MutableStateFlow<Set<Long>>(emptySet())
}
