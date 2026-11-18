package com.rohit.gallery.feature.viewer

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rohit.gallery.core.data.mediastore.ExternalMediaResolver
import com.rohit.gallery.core.data.mediastore.MediaDetails
import com.rohit.gallery.core.data.mediastore.MediaInfoLoader
import com.rohit.gallery.core.data.mediastore.MediaStoreDataSource
import com.rohit.gallery.core.data.mediastore.mediaStoreIdOf
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import com.rohit.gallery.core.domain.MediaActions
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.data.prefs.AppPreferences
import com.rohit.gallery.core.data.prefs.AppSettings
import com.rohit.gallery.core.data.mediastore.MediaTraits
import com.rohit.gallery.core.data.mediastore.MediaTraitsReader
import com.rohit.gallery.feature.search.SearchIndex
import com.rohit.gallery.feature.search.parseSearchNow
import com.rohit.gallery.navigation.ViewerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What an external launch ended up showing; decided once, when the viewer opens. */
private sealed interface ExternalTarget {
    /** The file is in our index: page through the whole timeline, starting at it. */
    data object Indexed : ExternalTarget

    /** Shown on its own; null item = unreadable, the viewer closes. */
    data class Single(val item: MediaItem?) : ExternalTarget
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MediaRepository,
    private val source: MediaStoreDataSource,
    private val traitsReader: MediaTraitsReader,
    prefs: AppPreferences,
    private val actions: MediaActions,
    private val infoLoader: MediaInfoLoader,
    private val externalResolver: ExternalMediaResolver,
    val playbackPositions: PlaybackPositionStore,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ViewerRoute>()

    /** True when another app opened this viewer; closing it finishes the activity. */
    val isExternal: Boolean = route.externalUri != null

    // For external launches the start item is only known once the URI is resolved; it is set
    // before [items] emits, so the screen always reads the right value.
    private var resolvedStartId: Long? = null

    val startId: Long get() = resolvedStartId ?: route.mediaId

    /** True when paging through the recycle bin: the viewer then offers Restore and Delete. */
    val isTrash: Boolean = route.trashed

    /** Hands [item] to Google Photos' editor (or another editor); false if none could be opened. */
    fun openInEditor(item: MediaItem): Boolean = actions.openInEditor(item)

    private val trashItems = MutableStateFlow<List<MediaItem>?>(null)
    private val externalTarget = MutableStateFlow<ExternalTarget?>(null)

    /** Items the pager swipes through, newest first; null until the index is ready. */
    val items: StateFlow<List<MediaItem>?> = when {
        route.trashed -> trashItems
        route.externalUri != null -> externalTarget
            .flatMapLatest { target ->
                when (target) {
                    null -> flowOf(null)
                    ExternalTarget.Indexed -> indexedItems()
                    is ExternalTarget.Single -> flowOf(listOfNotNull(target.item))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        else -> indexedItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialIndexedItems())
    }

    /**
     * The unfiltered timeline is already in memory: hand it over on the first frame, so the pager (and
     * the photo's decode) does not wait a background hop. Filtered views still filter off the main thread.
     */
    private fun initialIndexedItems(): List<MediaItem>? {
        val unfiltered = !route.favoritesOnly && !route.videosOnly && route.bucketId == null && route.searchQuery == null
        return if (unfiltered) (repository.state.value as? MediaIndexState.Ready)?.items else null
    }

    init {
        if (route.trashed) reloadTrash()
        route.externalUri?.let { resolveExternal(it, route.externalMime) }
    }

    private fun indexedItems(): Flow<List<MediaItem>?> = repository.state
        .map { state ->
            when (state) {
                MediaIndexState.Loading -> null
                is MediaIndexState.Ready ->
                    when {
                        route.searchQuery != null ->
                            SearchIndex(state.items, ZoneId.systemDefault()).search(parseSearchNow(route.searchQuery))
                        route.favoritesOnly -> state.items.filter { it.isFavorite }
                        route.videosOnly -> state.items.filter { it.isVideo }
                        route.bucketId != null -> state.items.filter { it.bucketId == route.bucketId }
                            .let { if (route.oldestFirst) it.reversed() else it }
                        else -> state.items
                    }
            }
        }
        .flowOn(Dispatchers.Default)

    /**
     * A MediaStore URI we can see in the index opens the normal pager (camera review: swipe on to older
     * shots). Anything else (no permission, another app's file, not selected under partial access)
     * is shown alone, using only the read grant that came with the intent.
     */
    private fun resolveExternal(uriString: String, mime: String?) {
        viewModelScope.launch {
            val uri = Uri.parse(uriString)
            val mediaId = mediaStoreIdOf(uri.scheme, uri.authority, uri.pathSegments)
            if (mediaId != null && externalResolver.canReadLibrary()) {
                // Nothing else loads the index on an external launch; a just-taken photo may also
                // not be in an older index yet.
                repository.refresh()
                val found = withTimeoutOrNull(INDEX_WAIT_MS) {
                    repository.state.first { state ->
                        state is MediaIndexState.Ready && state.items.any { it.id == mediaId }
                    }
                }
                if (found != null) {
                    resolvedStartId = mediaId
                    externalTarget.value = ExternalTarget.Indexed
                    return@launch
                }
            }
            val item = externalResolver.describe(uriString, mime)
            resolvedStartId = item?.id
            externalTarget.value = ExternalTarget.Single(item)
        }
    }

    private fun reloadTrash() {
        viewModelScope.launch { trashItems.value = runCatching { source.queryTrashed() }.getOrDefault(emptyList()) }
    }

    /** After a restore or permanent delete went through. */
    fun onTrashChanged() {
        repository.refresh()
        reloadTrash()
    }

    suspend fun restoreRequest(item: MediaItem): IntentSender? =
        runCatching { actions.restoreRequest(listOf(item)) }.getOrNull()

    suspend fun deleteRequest(item: MediaItem): IntentSender? =
        runCatching { actions.deleteRequest(listOf(item)) }.getOrNull()

    suspend fun loadDetails(item: MediaItem): MediaDetails = infoLoader.load(item)

    /** Share sheet for the photo on screen. */
    fun shareIntent(item: MediaItem): Intent = actions.shareIntent(listOf(item))

    /** Favourite / trash work only on MediaStore items (not on a file another app handed us). */
    fun canWrite(item: MediaItem): Boolean = item.uri.authority == MediaStore.AUTHORITY

    /** System confirmation to add or remove the favourite mark (skipped by Android with "Skip confirmation"). */
    suspend fun favoriteRequest(item: MediaItem): IntentSender? =
        runCatching { actions.favoriteRequest(listOf(item), favorite = !item.isFavorite) }.getOrNull()

    /** System confirmation to move the photo to the trash (recoverable). */
    suspend fun trashRequest(item: MediaItem): IntentSender? =
        runCatching { actions.trashRequest(listOf(item)) }.getOrNull()

    /** After favourite or trash went through: re-read the library so the change shows. */
    fun onWritten() = repository.refresh()

    /** Viewer options from Settings; null for the few ms until read. */
    val settings: StateFlow<AppSettings?> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Ultra HDR / motion photo, from the file header (cached). */
    suspend fun traits(item: MediaItem): MediaTraits = traitsReader.read(item)

    private companion object {
        /** How long an external MediaStore URI may wait for the index before it is shown on its own. */
        const val INDEX_WAIT_MS = 2_000L
    }
}
