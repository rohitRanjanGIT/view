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
    /** Page through the GIFs and motion photos only (the Motion and GIFs collection). */
    val motionOnly: Boolean = false,
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
)

/** The recycle bin, opened from the Menu tab. */
@Serializable data object TrashRoute

/** Favorites list, opened from the Menu tab. */
@Serializable data object FavoritesRoute

/** All videos, opened from the Menu tab. */
@Serializable data object VideosRoute

/** All GIFs and motion photos, opened from the Collections tiles. */
@Serializable data object MotionRoute

/** One album (folder), opened from the Albums tab. */
@Serializable data class AlbumDetailRoute(val bucketId: Long)

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
 * Picking photos to put in an album: a new one named [name] (Albums tab, +), or the existing album [bucketId] (the +
 * inside an album).
 */
@Serializable data class NewAlbumRoute(val name: String, val bucketId: Long? = null)
