package com.rohit.gallery.core.domain

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import com.rohit.gallery.core.model.MediaItem

/**
 * Puts [item] itself (not its path) on the clipboard, so it can be pasted into a chat or a note. The app that pastes
 * gets read-only access to that one file. Android 13+ shows its own "Copied" preview, so there is no toast here.
 *
 * The clip is built by hand rather than with ClipData.newUri, which would ask the provider for the type on this
 * (main) thread; the index already knows it.
 */
fun copyToClipboard(context: Context, item: MediaItem) {
    val mime = item.mimeType.ifBlank { if (item.isVideo) "video/*" else "image/*" }
    val label = item.displayName.ifBlank { if (item.isVideo) "Video" else "Photo" }
    val clip = ClipData(ClipDescription(label, arrayOf(mime)), ClipData.Item(item.uri))
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}
