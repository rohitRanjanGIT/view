package com.rohit.gallery.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/** The three tabs (Pictures, Albums, Menu), side by side in one pager. */
@Serializable data object HomeRoute

/** A bottom-bar tab, in pager order. */
data class HomeTab(val label: String, val icon: ImageVector)

val homeTabs: List<HomeTab> = listOf(
    HomeTab("Pictures", Icons.Filled.DateRange),
    HomeTab("Albums", Icons.AutoMirrored.Filled.List),
    HomeTab("Menu", Icons.Filled.Menu),
)

/**
 * Full-screen viewer. [bucketId] limits the pager to one album; null means the whole timeline. [trashed] pages
 * through the recycle bin instead. [externalUri] is set when another app asked us to show a file (ACTION_VIEW,
 * camera review); [mediaId] is then unused and [externalMime] is the type the intent declared, if any.
 */
@Serializable data class ViewerRoute(
    val mediaId: Long,
    val bucketId: Long? = null,
    val trashed: Boolean = false,
    val favoritesOnly: Boolean = false,
    val videosOnly: Boolean = false,
    /** With [bucketId]: page oldest first, matching the album screen. */
    val oldestFirst: Boolean = false,
    /** Page through the matches for this search instead (see feature/search). */
    val searchQuery: String? = null,
    val externalUri: String? = null,
    val externalMime: String? = null,
)

/** The recycle bin, opened from the Menu tab. */
@Serializable data object TrashRoute

/** Favorites list, opened from the Menu tab. */
@Serializable data object FavoritesRoute

/** All videos, opened from the Menu tab. */
@Serializable data object VideosRoute

/** One album (folder), opened from the Albums tab. */
@Serializable data class AlbumDetailRoute(val bucketId: Long)

/** Search the library, opened from the Pictures tab. */
@Serializable data object SearchRoute

/** App settings, opened from the Menu tab. */
@Serializable data object SettingsRoute

/** Pin, hide and reorder albums, opened from the Albums tab menu. */
@Serializable data object EditAlbumsRoute
