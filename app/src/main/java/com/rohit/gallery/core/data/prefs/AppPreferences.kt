package com.rohit.gallery.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
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
        .map { prefs -> prefs[ALBUM_COLUMNS] ?: 3 }
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

    /** Look and viewer options from the Settings screen. */
    val settings: Flow<AppSettings> = context.dataStore.data
        .map { prefs ->
            AppSettings(
                theme = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
                wallpaperColors = prefs[WALLPAPER_COLORS] ?: true,
                startMuted = prefs[START_MUTED] ?: false,
                motionAutoplay = prefs[MOTION_AUTOPLAY] ?: false,
                ultraHdr = prefs[ULTRA_HDR] ?: true,
                skipSeconds = prefs[SKIP_SECONDS]?.takeIf { it in SKIP_SECONDS_CHOICES } ?: DEFAULT_SKIP_SECONDS,
            )
        }
        .distinctUntilChanged()

    suspend fun update(change: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val old = AppSettings(
                theme = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
                wallpaperColors = prefs[WALLPAPER_COLORS] ?: true,
                startMuted = prefs[START_MUTED] ?: false,
                motionAutoplay = prefs[MOTION_AUTOPLAY] ?: false,
                ultraHdr = prefs[ULTRA_HDR] ?: true,
                skipSeconds = prefs[SKIP_SECONDS]?.takeIf { it in SKIP_SECONDS_CHOICES } ?: DEFAULT_SKIP_SECONDS,
            )
            val new = change(old)
            prefs[THEME] = new.theme.name
            prefs[WALLPAPER_COLORS] = new.wallpaperColors
            prefs[START_MUTED] = new.startMuted
            prefs[MOTION_AUTOPLAY] = new.motionAutoplay
            prefs[ULTRA_HDR] = new.ultraHdr
            prefs[SKIP_SECONDS] = new.skipSeconds
        }
    }

    /** Grid sizes, by name: the Pictures timeline, the album / Favorites / Videos timelines, and Trash. */
    fun gridSize(key: GridSizeKey): Flow<String?> = context.dataStore.data.map { it[key.prefKey] }.distinctUntilChanged()

    suspend fun setGridSize(key: GridSizeKey, value: String) {
        context.dataStore.edit { it[key.prefKey] = value }
    }

    /** Where videos were left, as `id:positionMs` pairs (most recent first). */
    val videoPositions: Flow<Map<Long, Long>> = context.dataStore.data
        .map { prefs ->
            prefs[VIDEO_POSITIONS].orEmpty().split(';').mapNotNull { pair ->
                val (id, pos) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                (id.toLongOrNull() ?: return@mapNotNull null) to (pos.toLongOrNull() ?: return@mapNotNull null)
            }.toMap()
        }

    suspend fun setVideoPositions(positions: Map<Long, Long>) {
        context.dataStore.edit { prefs ->
            prefs[VIDEO_POSITIONS] = positions.entries.joinToString(";") { "${it.key}:${it.value}" }
        }
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

    /** True once the "lock without the delete pop-up" hint has been shown (it is shown only once). */
    val lockHintShown: Flow<Boolean> = context.dataStore.data.map { it[LOCK_HINT_SHOWN] ?: false }

    suspend fun setLockHintShown() {
        context.dataStore.edit { it[LOCK_HINT_SHOWN] = true }
    }

    /** Locked (the private folder) is on: its Lock actions and its hidden way in exist. On by default. */
    val lockedEnabled: Flow<Boolean> = context.dataStore.data.map { it[LOCKED_ENABLED] ?: true }.distinctUntilChanged()

    suspend fun setLockedEnabled(enabled: Boolean) {
        context.dataStore.edit { it[LOCKED_ENABLED] = enabled }
    }

    private companion object {
        val ALBUM_SORT = stringPreferencesKey("album_sort")
        val ALBUM_OLDEST_FIRST = booleanPreferencesKey("album_oldest_first")
        val ALBUM_COLUMNS = intPreferencesKey("album_columns")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val THEME = stringPreferencesKey("theme")
        val WALLPAPER_COLORS = booleanPreferencesKey("wallpaper_colors")
        val START_MUTED = booleanPreferencesKey("start_muted")
        val MOTION_AUTOPLAY = booleanPreferencesKey("motion_autoplay")
        val ULTRA_HDR = booleanPreferencesKey("ultra_hdr")
        val SKIP_SECONDS = intPreferencesKey("skip_seconds")
        val VIDEO_POSITIONS = stringPreferencesKey("video_positions")
        val LOCK_HINT_SHOWN = booleanPreferencesKey("lock_hint_shown")
        val LOCKED_ENABLED = booleanPreferencesKey("locked_enabled")
        /** Unit separator: cannot be typed, so it never appears inside a search. */
        const val SEARCH_SEPARATOR = "\u001F"
        const val MAX_RECENT_SEARCHES = 8
    }
}

enum class ThemeMode(val label: String) { System("System default"), Light("Light"), Dark("Dark") }

/** The Settings screen's look and viewer options. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    /** Material You colours from the wallpaper; off uses the app's own palette. */
    val wallpaperColors: Boolean = true,
    val startMuted: Boolean = false,
    /** Play a motion photo's clip once as soon as it is opened. */
    val motionAutoplay: Boolean = false,
    /** Show Ultra HDR photos with their extra brightness. */
    val ultraHdr: Boolean = true,
    /** How far a double-tap on the left or right third of a video skips, in seconds (one of [SKIP_SECONDS_CHOICES]). */
    val skipSeconds: Int = DEFAULT_SKIP_SECONDS,
)

/** Skip lengths offered in Settings for double-tapping a video. */
val SKIP_SECONDS_CHOICES = listOf(5, 10, 15, 30)
const val DEFAULT_SKIP_SECONDS = 10

/** Grids whose size (column count or level) is remembered across launches. */
enum class GridSizeKey(internal val prefKey: Preferences.Key<String>) {
    Pictures(stringPreferencesKey("grid_pictures")),
    Lists(stringPreferencesKey("grid_lists")),
    Trash(stringPreferencesKey("grid_trash")),
}
