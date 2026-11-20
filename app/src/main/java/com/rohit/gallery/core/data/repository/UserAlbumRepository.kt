package com.rohit.gallery.core.data.repository

import com.rohit.gallery.core.data.db.UserAlbumDao
import com.rohit.gallery.core.data.db.UserAlbumEntity
import com.rohit.gallery.core.data.db.UserAlbumMemberEntity
import com.rohit.gallery.core.data.prefs.AppPreferences
import com.rohit.gallery.core.di.ApplicationScope
import com.rohit.gallery.core.domain.albumNameError
import com.rohit.gallery.core.model.AlbumSort
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.model.UserAlbum
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What [UserAlbumRepository.create] did. */
sealed interface CreateAlbumResult {
    data class Created(val id: Long, val added: AddResult) : CreateAlbumResult

    /** The name can't be used; [message] says why (shown under the name field). */
    data class Invalid(val message: String) : CreateAlbumResult
}

/** [added] photos went in; [alreadyThere] were in the album before (nothing changes for them). */
data class AddResult(val added: Int, val alreadyThere: Int)

/** The toast after adding to [album]: "Added 3 to Trip · 2 were already there". */
fun AddResult.message(album: String): String {
    val main = when (added) {
        0 -> "Nothing new for $album"
        else -> "Added $added to $album"
    }
    val already = when (alreadyThere) {
        0 -> null
        1 -> "1 was already there"
        else -> "$alreadyThere were already there"
    }
    return if (already == null) main else "$main · $already"
}

/**
 * The user's own albums (Google Photos style): named lists of media ids in Room, resolved against the in-memory
 * media index. Adding a photo never copies or moves a file, a photo can be in any number of albums, and removing
 * or deleting only edits the lists.
 *
 * Members that are not in the index (in Trash, Locked, or gone) are left out of [albums] but never pruned, so a photo
 * restored from Trash shows up in its albums again.
 */
@Singleton
class UserAlbumRepository internal constructor(
    private val dao: UserAlbumDao,
    index: Flow<MediaIndexState>,
    sort: Flow<AlbumSort>,
    scope: CoroutineScope,
    private val clock: () -> Long,
    computeDispatcher: CoroutineDispatcher,
) {
    @Inject constructor(
        dao: UserAlbumDao,
        media: MediaRepository,
        prefs: AppPreferences,
        @ApplicationScope scope: CoroutineScope,
    ) : this(dao, media.state, prefs.albumSort, scope, System::currentTimeMillis, Dispatchers.Default)

    /** Every user album, pinned first, then in the Albums tab's sort; null while the media index is loading. */
    val albums: StateFlow<List<UserAlbum>?> =
        combine(index, dao.observeAlbums(), dao.observeMembers(), sort) { state, albums, members, order ->
            (state as? MediaIndexState.Ready)?.let { resolveUserAlbums(albums, members, it.items, order) }
        }
            .flowOn(computeDispatcher)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Writes run one at a time, so two quick taps can't create two albums with the same name. */
    private val writes = Mutex()

    /** Makes an album called [name] holding [items]. Refused if the name is empty, too long, or another album's. */
    suspend fun create(name: String, items: List<MediaItem> = emptyList()): CreateAlbumResult = writes.withLock {
        val trimmed = name.trim()
        albumNameError(trimmed, dao.albums().map { it.name }, folder = false)?.let { return CreateAlbumResult.Invalid(it) }
        val id = dao.insertAlbum(UserAlbumEntity(name = trimmed, createdAt = clock()))
        CreateAlbumResult.Created(id, addLocked(id, items))
    }

    /** Null if renamed, else why not (the same rules as a new name; the album's own name is free to reuse). */
    suspend fun rename(albumId: Long, name: String): String? = writes.withLock {
        val all = dao.albums()
        val album = all.firstOrNull { it.id == albumId } ?: return "This album no longer exists"
        val trimmed = name.trim()
        albumNameError(trimmed, all.filter { it.id != albumId }.map { it.name }, folder = false)?.let { return it }
        if (trimmed != album.name) dao.updateAlbums(listOf(album.copy(name = trimmed)))
        null
    }

    /** Deletes the albums (only the lists: every photo stays where it is on the device). */
    suspend fun delete(albumIds: Collection<Long>) = writes.withLock {
        val ids = albumIds.distinct()
        if (ids.isEmpty()) return@withLock
        dao.deleteAlbums(ids)
        dao.deleteMembersOf(ids)
    }

    /** Adds [items] to the album; the ones already in it are counted, not added twice. */
    suspend fun add(albumId: Long, items: List<MediaItem>): AddResult = writes.withLock {
        if (dao.albums().none { it.id == albumId }) return AddResult(0, 0)
        addLocked(albumId, items)
    }

    private suspend fun addLocked(albumId: Long, items: List<MediaItem>): AddResult {
        val unique = items.map { it.id }.distinct()
        if (unique.isEmpty()) return AddResult(0, 0)
        val existing = dao.memberIds(albumId).toHashSet()
        val now = clock()
        val fresh = unique.filter { it !in existing }
        fresh.chunked(CHUNK).forEach { chunk -> dao.insertMembers(chunk.map { UserAlbumMemberEntity(albumId, it, now) }) }
        return AddResult(added = fresh.size, alreadyThere = unique.size - fresh.size)
    }

    /** Takes [mediaIds] out of the album (the photos themselves are untouched). Returns the rows, for [restore] on Undo. */
    suspend fun remove(albumId: Long, mediaIds: Collection<Long>): List<UserAlbumMemberEntity> = writes.withLock {
        val ids = mediaIds.distinct()
        val removed = ids.chunked(CHUNK).flatMap { dao.members(albumId, it) }
        ids.chunked(CHUNK).forEach { dao.deleteMembers(albumId, it) }
        removed
    }

    /** Puts back what [remove] took out, with their first "added" times (unless the album was deleted meanwhile). */
    suspend fun restore(members: List<UserAlbumMemberEntity>) = writes.withLock {
        val existing = dao.albums().mapTo(HashSet()) { it.id }
        members.filter { it.albumId in existing }.chunked(CHUNK).forEach { dao.insertMembers(it) }
    }

    /** Uses [mediaId] as the album's cover; null goes back to its newest photo. */
    suspend fun setCover(albumId: Long, mediaId: Long?) = writes.withLock {
        val album = dao.albums().firstOrNull { it.id == albumId } ?: return@withLock
        if (album.coverMediaId != mediaId) dao.updateAlbums(listOf(album.copy(coverMediaId = mediaId)))
    }

    /** Like device folders: new pins go after the earlier ones, in the order given; albums already pinned stay put. */
    suspend fun setPinned(albumIds: List<Long>, pinned: Boolean) = writes.withLock {
        val byId = dao.albums().associateBy { it.id }
        val now = clock()
        var next = 0L
        val changed = albumIds.mapNotNull { byId[it] }.mapNotNull {
            when {
                !pinned -> if (it.pinnedAt == null) null else it.copy(pinnedAt = null)
                it.pinnedAt != null -> null
                else -> it.copy(pinnedAt = now + next++)
            }
        }
        if (changed.isNotEmpty()) dao.updateAlbums(changed)
    }

    /** Saves [albumIds] (the unpinned albums, in the order dragged) as the custom order. */
    suspend fun saveCustomOrder(albumIds: List<Long>) = writes.withLock {
        val byId = dao.albums().associateBy { it.id }
        dao.updateAlbums(albumIds.mapIndexedNotNull { i, id -> byId[id]?.copy(position = i) })
    }

    private companion object {
        /** Stays well under SQLite's limit on query parameters. */
        const val CHUNK = 500
    }
}

/**
 * Resolves the stored albums against the media [index] (newest first): each album gets its members that are in the
 * index, in index order. Pinned albums lead in pin order, then the rest in [sort] (as device folders are ordered).
 * Single pass over the index, so it stays cheap on a 50k library.
 */
internal fun resolveUserAlbums(
    albums: List<UserAlbumEntity>,
    members: List<UserAlbumMemberEntity>,
    index: List<MediaItem>,
    sort: AlbumSort,
): List<UserAlbum> {
    val albumsOf = HashMap<Long, MutableList<Long>>()
    for (m in members) albumsOf.getOrPut(m.mediaId) { ArrayList(1) }.add(m.albumId)
    val itemsOf = HashMap<Long, ArrayList<MediaItem>>()
    if (albumsOf.isNotEmpty()) {
        for (item in index) albumsOf[item.id]?.forEach { itemsOf.getOrPut(it) { ArrayList() }.add(item) }
    }
    val resolved = albums.map { row ->
        val items: List<MediaItem> = itemsOf[row.id] ?: emptyList()
        row to UserAlbum(
            id = row.id,
            name = row.name,
            items = items,
            cover = row.coverMediaId?.let { id -> items.firstOrNull { it.id == id } } ?: items.firstOrNull(),
            pinned = row.pinnedAt != null,
            createdAt = row.createdAt,
        )
    }
    val (pinned, others) = resolved.partition { it.first.pinnedAt != null }
    val newestFirst = compareByDescending<Pair<UserAlbumEntity, UserAlbum>> { it.second.items.firstOrNull()?.sortDateMs ?: Long.MIN_VALUE }
        .thenByDescending { it.first.createdAt }
    val othersOrdered = when (sort) {
        AlbumSort.Newest -> others.sortedWith(newestFirst)
        AlbumSort.Name -> others.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second.name })
        AlbumSort.MostItems -> others.sortedWith(compareByDescending<Pair<UserAlbumEntity, UserAlbum>> { it.second.count }.then(newestFirst))
        AlbumSort.Custom -> {
            // Albums never placed (new ones) lead, newest first, as for device folders.
            val (placed, unplaced) = others.partition { it.first.position != null }
            unplaced.sortedByDescending { it.first.createdAt } + placed.sortedBy { it.first.position }
        }
    }
    return (pinned.sortedBy { it.first.pinnedAt } + othersOrdered).map { it.second }
}
