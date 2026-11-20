package com.rohit.gallery.feature.selection

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.rohit.gallery.core.data.repository.CreateAlbumResult
import com.rohit.gallery.core.data.repository.UserAlbumRepository
import com.rohit.gallery.core.data.repository.message
import com.rohit.gallery.core.di.ApplicationScope
import com.rohit.gallery.core.model.MediaItem
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlbumAdderEntryPoint {
    fun userAlbumRepository(): UserAlbumRepository

    @ApplicationScope
    fun applicationScope(): CoroutineScope
}

/**
 * "Add to album" from any selection: records the photos in a user album (or a new one). Nothing is copied or moved.
 * Runs in the app's scope, because the selection (and the bar that asked) ends straight away; the result is a toast.
 */
@Stable
class AlbumAdder internal constructor(
    val repository: UserAlbumRepository,
    private val scope: CoroutineScope,
    private val context: Context,
) {
    /** Adds [items] to the album [albumId] called [name], or, with [albumId] null, to a new album [name]. */
    fun add(items: List<MediaItem>, albumId: Long?, name: String) {
        if (items.isEmpty()) return
        scope.launch {
            val message = runCatching {
                if (albumId != null) {
                    repository.add(albumId, items).message(name)
                } else {
                    when (val result = repository.create(name, items)) {
                        is CreateAlbumResult.Created -> result.added.message(name)
                        is CreateAlbumResult.Invalid -> result.message
                    }
                }
            }.getOrElse { "Couldn't add to $name" }
            withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        }
    }
}

@Composable
fun rememberAlbumAdder(): AlbumAdder {
    val app = LocalContext.current.applicationContext
    return remember(app) {
        val entry = EntryPointAccessors.fromApplication(app, AlbumAdderEntryPoint::class.java)
        AlbumAdder(entry.userAlbumRepository(), entry.applicationScope(), app)
    }
}
