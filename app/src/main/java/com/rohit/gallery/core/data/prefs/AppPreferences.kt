package com.rohit.gallery.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    /** Albums per row in the Albums tab (2, 3 or 4). */
    val albumColumns: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[ALBUM_COLUMNS] ?: 2 }
        .distinctUntilChanged()

    /** Searches the user ran, newest first (at most [MAX_RECENT_SEARCHES]). */
    val recentSearches: Flow<List<String>> = context.dataStore.data
        .map { prefs -> prefs[RECENT_SEARCHES]?.split(SEARCH_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty() }
        .distinctUntilChanged()

    suspend fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        context.dataStore.edit { prefs ->
            val old = prefs[RECENT_SEARCHES]?.split(SEARCH_SEPARATOR).orEmpty()
            val updated = (listOf(q) + old.filter { it.isNotEmpty() && !it.equals(q, ignoreCase = true) })
                .take(MAX_RECENT_SEARCHES)
            prefs[RECENT_SEARCHES] = updated.joinToString(SEARCH_SEPARATOR)
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { it.remove(RECENT_SEARCHES) }
    }

    suspend fun setAlbumColumns(columns: Int) {
        context.dataStore.edit { it[ALBUM_COLUMNS] = columns }
    }

    suspend fun setAlbumSort(sort: AlbumSort) {
        context.dataStore.edit { it[ALBUM_SORT] = sort.name }
    }

    suspend fun setAlbumOldestFirst(oldestFirst: Boolean) {
        context.dataStore.edit { it[ALBUM_OLDEST_FIRST] = oldestFirst }
    }

    private companion object {
        val ALBUM_SORT = stringPreferencesKey("album_sort")
        val ALBUM_OLDEST_FIRST = booleanPreferencesKey("album_oldest_first")
        val ALBUM_COLUMNS = intPreferencesKey("album_columns")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        /** Unit separator: cannot be typed, so it never appears inside a search. */
        const val SEARCH_SEPARATOR = "\u001F"
        const val MAX_RECENT_SEARCHES = 8
    }
}
