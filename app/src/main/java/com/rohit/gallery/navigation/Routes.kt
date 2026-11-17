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

/** Full-screen viewer. [bucketId] limits the pager to one album; null means the whole timeline. */
@Serializable data class ViewerRoute(val mediaId: Long, val bucketId: Long? = null)

/** The recycle bin, opened from the Menu tab. */
@Serializable data object TrashRoute
