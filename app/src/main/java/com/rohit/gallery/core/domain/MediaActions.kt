package com.rohit.gallery.core.domain

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.provider.MediaStore
import com.rohit.gallery.core.data.db.TrashSnapshotDao
import com.rohit.gallery.core.data.db.TrashSnapshotEntity
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
    private val snapshots: TrashSnapshotDao,
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

    /**
     * Opens [item] (photo or video) in Google Photos' editor, or (if it is not installed) any app that edits it. Read access
     * only: the editor can save its result as a new copy but can never overwrite the original.
     */
    fun openInEditor(item: MediaItem): Boolean {
        fun edit() = Intent(Intent.ACTION_EDIT)
            .setDataAndType(item.uri, item.mimeType.ifBlank { if (item.isVideo) "video/*" else "image/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val launched = runCatching { context.startActivity(edit().setPackage(GOOGLE_PHOTOS)) }.isSuccess
        if (launched) return true
        val chooser = Intent.createChooser(edit(), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(chooser) }.isSuccess
    }

    /**
     * System confirmation to move [items] to the trash (they can be restored from there). Their date and favourite
     * mark are saved first: a restore can lose them, and [RestoreRepair] puts them back.
     */
    suspend fun trashRequest(items: List<MediaItem>): IntentSender = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        runCatching {
            snapshots.deleteOlderThan(now - SNAPSHOT_KEEP_MS)
            snapshots.upsertAll(items.map { TrashSnapshotEntity(it.id, it.isVideo, it.sortDateMs, it.isFavorite, now) })
        }
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

private const val GOOGLE_PHOTOS = "com.google.android.apps.photos"

/** Trash keeps items 30 days; a few more covers a restore on the last day. */
private const val SNAPSHOT_KEEP_MS = 35L * 24 * 60 * 60 * 1000
