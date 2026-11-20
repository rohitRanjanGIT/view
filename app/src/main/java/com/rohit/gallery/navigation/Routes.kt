package com.rohit.gallery.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.rohit.gallery.core.ui.icons.GalleryIcons
import kotlinx.serialization.Serializable

/** The home screen: Pictures and Albums, side by side in one pager. */
@Serializable data object HomeRoute

/** A bottom-bar tab, in pager order. */
data class HomeTab(val label: String, val icon: ImageVector)

val homeTabs: List<HomeTab> = listOf(
    HomeTab("Pictures", GalleryIcons.PicturesTab),
    HomeTab("Albums", GalleryIcons.AlbumsTab),
)

/** The Albums tab's page. */
const val ALBUMS_PAGE = 1

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
    /** Page through Locked (the vault) instead; only reachable after unlocking it. */
    val vault: Boolean = false,
    /** With [vault]: only the photos locked from this album. */
    val vaultAlbum: String? = null,
    /** Page through this user album (see UserAlbumRepository) instead; [oldestFirst] applies as for a folder. */
    val userAlbumId: Long? = null,
)

/** The recycle bin, opened from the Menu tab. */
@Serializable data object TrashRoute

/** Favorites list, opened from the Menu tab. */
@Serializable data object FavoritesRoute

/** All videos, opened from the Menu tab. */
@Serializable data object VideosRoute

/** One device folder, opened from the Albums tab's "On this device". */
@Serializable data class AlbumDetailRoute(val bucketId: Long)

/** One user album (a list kept by the app, not a folder), opened from the Albums tab's "Albums". */
@Serializable data class UserAlbumRoute(val albumId: Long)

/** Search the library, opened from the Pictures tab. */
@Serializable data object SearchRoute

/** Settings: swipe left past the edge of Albums and hold (no tab, no button). */
@Serializable data object SettingsRoute

/** App settings, opened from the Menu tab. */

/** Locked photos and videos (the vault), opened from the Menu tab. */
@Serializable data object VaultRoute

/** Pin, hide and reorder albums, opened from the Albums tab menu. */
@Serializable data object EditAlbumsRoute

/**
 * Picking photos to move into the existing device folder [bucketId] (the + inside a folder). [name] is the folder's name.
 */
@Serializable data class NewAlbumRoute(val name: String, val bucketId: Long? = null)

/**
 * Picking photos for a user album: a new one named [name] (made when Add is tapped, so Cancel leaves nothing behind;
 * Albums tab, +), or the existing album [albumId] (Add photos inside it). Only memberships change, never files.
 */
@Serializable data class PickForAlbumRoute(val name: String, val albumId: Long? = null)
