package com.rohit.gallery.core.domain

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.provider.MediaStore
import com.rohit.gallery.core.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Actions on a set of media items. Nothing here touches file contents: deletes are only ever
 * *trash* requests (recoverable), and writes go through MediaStore's `create*Request` APIs, which
 * make the system show its own confirmation before anything changes.
 */
@Singleton
class MediaActions @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Chooser for sharing [items] (read access is granted to the receiving app only). */
    fun shareIntent(items: List<MediaItem>): Intent {
        val uris = ArrayList(items.map { it.uri })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = commonMimeType(items)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null)
    }

    /** System confirmation to move [items] to the trash (they can be restored from there). */
    suspend fun trashRequest(items: List<MediaItem>): IntentSender = withContext(Dispatchers.IO) {
        MediaStore.createTrashRequest(context.contentResolver, items.map { it.uri }, true).intentSender
    }

    /** System confirmation to mark [items] as favorite ([favorite] = true) or remove the mark. */
    suspend fun favoriteRequest(items: List<MediaItem>, favorite: Boolean): IntentSender =
        withContext(Dispatchers.IO) {
            MediaStore.createFavoriteRequest(context.contentResolver, items.map { it.uri }, favorite).intentSender
        }

    /** System confirmation to bring [items] back out of the trash. */
    suspend fun restoreRequest(items: List<MediaItem>): IntentSender = withContext(Dispatchers.IO) {
        MediaStore.createTrashRequest(context.contentResolver, items.map { it.uri }, false).intentSender
    }

    /** System confirmation to delete [items] for good. Only the Trash screen offers this. */
    suspend fun deleteRequest(items: List<MediaItem>): IntentSender = withContext(Dispatchers.IO) {
        MediaStore.createDeleteRequest(context.contentResolver, items.map { it.uri }).intentSender
    }
}

/** The mime type to advertise when sharing: exact for one item, image or video wildcard if uniform, else the generic wildcard. */
fun commonMimeType(items: List<MediaItem>): String = when {
    items.isEmpty() -> "*/*"
    items.size == 1 && items.first().mimeType.isNotBlank() -> items.first().mimeType
    items.all { !it.isVideo } -> "image/*"
    items.all { it.isVideo } -> "video/*"
    else -> "*/*"
}
