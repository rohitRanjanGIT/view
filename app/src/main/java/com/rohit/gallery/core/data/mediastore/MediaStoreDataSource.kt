package com.rohit.gallery.core.data.mediastore

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

@Singleton
class MediaStoreDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MediaSource {

    override suspend fun queryAll(): List<MediaItem> = query(trashed = false)

    /** Items currently in the system trash (restorable). */
    suspend fun queryTrashed(): List<MediaItem> = query(trashed = true)

    private suspend fun query(trashed: Boolean): List<MediaItem> = withContext(Dispatchers.IO) {
        val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val queryArgs = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)",
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                ),
            )
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_TAKEN} DESC")
            putInt(
                MediaStore.QUERY_ARG_MATCH_TRASHED,
                if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE,
            )
        }
        val cursor = context.contentResolver.query(filesUri, PROJECTION, queryArgs, null)
            ?: return@withContext emptyList()

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val typeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val takenCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val modifiedCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val widthCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val bucketIdCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val favoriteCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

            val out = ArrayList<MediaItem>(it.count)
            while (it.moveToNext()) {
                if (out.size % CANCEL_CHECK_INTERVAL == 0) ensureActive()
                val isVideo = it.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                out += MediaItem(
                    id = it.getLong(idCol),
                    type = if (isVideo) MediaType.Video else MediaType.Image,
                    mimeType = it.getStringOrNull(mimeCol).orEmpty(),
                    dateTakenMs = it.getLongOrNull(takenCol) ?: 0L,
                    // DATE_MODIFIED is stored in seconds.
                    dateModifiedMs = (it.getLongOrNull(modifiedCol) ?: 0L) * 1000L,
                    width = it.getLongOrNull(widthCol)?.toInt() ?: 0,
                    height = it.getLongOrNull(heightCol)?.toInt() ?: 0,
                    durationMs = it.getLongOrNull(durationCol) ?: 0L,
                    bucketId = it.getLongOrNull(bucketIdCol) ?: 0L,
                    bucketName = it.getStringOrNull(bucketNameCol).orEmpty(),
                    isFavorite = it.getLongOrNull(favoriteCol) == 1L,
                    sizeBytes = it.getLongOrNull(sizeCol) ?: 0L,
                    displayName = it.getStringOrNull(nameCol).orEmpty(),
                    isTrashed = trashed,
                )
            }
            out
        }
    }

    override fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    private companion object {
        const val CANCEL_CHECK_INTERVAL = 2000

        val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
    }
}

