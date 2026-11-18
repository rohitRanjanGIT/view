package com.rohit.gallery.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rohit.gallery.core.model.AlbumSort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Small app settings, kept on the device with DataStore (reads and writes happen off the main thread). */
@Singleton
class AppPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {

    val albumSort: Flow<AlbumSort> = context.dataStore.data
        .map { prefs -> prefs[ALBUM_SORT]?.let { name -> AlbumSort.entries.firstOrNull { it.name == name } } ?: AlbumSort.Newest }
        .distinctUntilChanged()

    /** Inside an album: oldest photos first instead of newest first. */
    val albumOldestFirst: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[ALBUM_OLDEST_FIRST] ?: false }
        .distinctUntilChanged()

    suspend fun setAlbumSort(sort: AlbumSort) {
        context.dataStore.edit { it[ALBUM_SORT] = sort.name }
    }

    suspend fun setAlbumOldestFirst(oldestFirst: Boolean) {
        context.dataStore.edit { it[ALBUM_OLDEST_FIRST] = oldestFirst }
    }

    private companion object {
        val ALBUM_SORT = stringPreferencesKey("album_sort")
        val ALBUM_OLDEST_FIRST = booleanPreferencesKey("album_oldest_first")
    }
}
