package com.rohit.gallery.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable data object PicturesRoute
@Serializable data object AlbumsRoute
@Serializable data object MenuRoute

/** A bottom-bar destination. */
data class TopLevelDestination<T : Any>(
    val route: T,
    val label: String,
    val icon: ImageVector,
)

val topLevelDestinations: List<TopLevelDestination<*>> = listOf(
    TopLevelDestination(PicturesRoute, "Pictures", Icons.Filled.DateRange),
    TopLevelDestination(AlbumsRoute, "Albums", Icons.AutoMirrored.Filled.List),
    TopLevelDestination(MenuRoute, "Menu", Icons.Filled.Menu),
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
