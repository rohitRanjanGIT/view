package com.rohit.gallery

import com.rohit.gallery.core.data.db.UserAlbumDao
import com.rohit.gallery.core.data.db.UserAlbumEntity
import com.rohit.gallery.core.data.db.UserAlbumMemberEntity
import com.rohit.gallery.core.data.repository.AddResult
import com.rohit.gallery.core.data.repository.CreateAlbumResult
import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.UserAlbumRepository
import com.rohit.gallery.core.data.repository.message
import com.rohit.gallery.core.data.repository.resolveUserAlbums
import com.rohit.gallery.core.domain.albumNameError
import com.rohit.gallery.core.model.AlbumSort
import com.rohit.gallery.core.model.UserAlbum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** Room's DAO as two in-memory tables. */
private class FakeUserAlbumDao : UserAlbumDao {
    val albums = MutableStateFlow<List<UserAlbumEntity>>(emptyList())
    val members = MutableStateFlow<List<UserAlbumMemberEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAlbums(): Flow<List<UserAlbumEntity>> = albums
    override fun observeMembers(): Flow<List<UserAlbumMemberEntity>> = members
    override suspend fun albums(): List<UserAlbumEntity> = albums.value

    override suspend fun insertAlbum(album: UserAlbumEntity): Long {
        val id = nextId++
        albums.value += album.copy(id = id)
        return id
    }

    override suspend fun updateAlbums(albums: List<UserAlbumEntity>) {
        val byId = albums.associateBy { it.id }
        this.albums.value = this.albums.value.map { byId[it.id] ?: it }
    }

    override suspend fun deleteAlbums(ids: List<Long>) {
        albums.value = albums.value.filter { it.id !in ids }
    }

    override suspend fun deleteMembersOf(albumIds: List<Long>) {
        members.value = members.value.filter { it.albumId !in albumIds }
    }

    override suspend fun memberIds(albumId: Long): List<Long> = members.value.filter { it.albumId == albumId }.map { it.mediaId }

    override suspend fun insertMembers(members: List<UserAlbumMemberEntity>) {
        val fresh = members.filter { m -> this.members.value.none { it.albumId == m.albumId && it.mediaId == m.mediaId } }
        this.members.value += fresh
    }

    override suspend fun members(albumId: Long, mediaIds: List<Long>): List<UserAlbumMemberEntity> =
        members.value.filter { it.albumId == albumId && it.mediaId in mediaIds }

    override suspend fun deleteMembers(albumId: Long, mediaIds: List<Long>) {
        members.value = members.value.filterNot { it.albumId == albumId && it.mediaId in mediaIds }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UserAlbumRepositoryTest {
    private val dao = FakeUserAlbumDao()
    private val index = MutableStateFlow<MediaIndexState>(MediaIndexState.Loading)
    private val sort = MutableStateFlow(AlbumSort.Newest)
    private var now = 1_000L

    // Newest first, like the real index.
    private val library = listOf(testItem(5, dateTakenMs = 500), testItem(4, dateTakenMs = 400), testItem(3, dateTakenMs = 300))

    private fun TestScope.repo() =
        UserAlbumRepository(dao, index, sort, backgroundScope, { now++ }, UnconfinedTestDispatcher(testScheduler))

    private suspend fun UserAlbumRepository.ready(): List<UserAlbum> = albums.first { it != null }!!

    @Test
    fun `adding photos records memberships only, and counts the ones already there`() = runTest {
        val repo = repo()
        val created = assertIs<CreateAlbumResult.Created>(repo.create("Trip", listOf(library[0])))
        assertEquals(AddResult(1, 0), created.added)

        val result = repo.add(created.id, listOf(library[0], library[1], library[1]))

        assertEquals(AddResult(added = 1, alreadyThere = 1), result)
        assertEquals(setOf(5L, 4L), dao.members.value.map { it.mediaId }.toSet())
    }

    @Test
    fun `names are trimmed and clash with other albums ignoring case`() = runTest {
        val repo = repo()
        assertIs<CreateAlbumResult.Created>(repo.create("  Trip "))
        assertEquals("Trip", dao.albums.value.single().name)
        assertIs<CreateAlbumResult.Invalid>(repo.create("trip"))
        assertIs<CreateAlbumResult.Invalid>(repo.create("   "))
        assertEquals(1, dao.albums.value.size)
    }

    @Test
    fun `a user album name may hold characters a folder can't`() {
        assertNull(albumNameError("Mum / Dad", folder = false))
        assertNotNull(albumNameError("Mum / Dad"))
    }

    @Test
    fun `rename keeps its own name free and refuses another album's`() = runTest {
        val repo = repo()
        val a = assertIs<CreateAlbumResult.Created>(repo.create("Trip")).id
        repo.create("Beach")

        assertNull(repo.rename(a, "TRIP"))
        assertEquals("TRIP", dao.albums.value.first { it.id == a }.name)
        assertNotNull(repo.rename(a, "beach"))
    }

    @Test
    fun `members outside the index are hidden, not pruned, and come back`() = runTest {
        val repo = repo()
        val id = assertIs<CreateAlbumResult.Created>(repo.create("Trip", library)).id
        // Photo 4 went to Trash: the index no longer has it.
        index.value = MediaIndexState.Ready(library.filter { it.id != 4L })
        assertEquals(listOf(5L, 3L), repo.ready().single().items.map { it.id })
        assertEquals(3, dao.members.value.size)

        // Restored from Trash.
        index.value = MediaIndexState.Ready(library)
        assertEquals(listOf(5L, 4L, 3L), repo.albums.first { it?.single()?.count == 3 }!!.single().items.map { it.id })
        assertEquals(id, repo.ready().single().id)
    }

    @Test
    fun `remove takes photos out of the list and restore puts them back`() = runTest {
        val repo = repo()
        val id = assertIs<CreateAlbumResult.Created>(repo.create("Trip", library)).id
        val removed = repo.remove(id, listOf(4L, 3L))
        assertEquals(listOf(5L), dao.members.value.map { it.mediaId })

        repo.restore(removed)
        assertEquals(setOf(5L, 4L, 3L), dao.members.value.map { it.mediaId }.toSet())
    }

    @Test
    fun `deleting an album deletes only its list`() = runTest {
        val repo = repo()
        val trip = assertIs<CreateAlbumResult.Created>(repo.create("Trip", library)).id
        val beach = assertIs<CreateAlbumResult.Created>(repo.create("Beach", library.take(1))).id
        repo.delete(listOf(trip))

        assertEquals(listOf(beach), dao.albums.value.map { it.id })
        assertTrue(dao.members.value.all { it.albumId == beach })
    }

    @Test
    fun `cover is the chosen photo while it is shown, else the newest`() = runTest {
        val repo = repo()
        val id = assertIs<CreateAlbumResult.Created>(repo.create("Trip", library)).id
        repo.setCover(id, 3L)
        index.value = MediaIndexState.Ready(library)
        assertEquals(3L, repo.ready().single().cover?.id)

        index.value = MediaIndexState.Ready(library.filter { it.id != 3L })
        assertEquals(5L, repo.albums.first { it?.single()?.count == 2 }!!.single().cover?.id)
    }

    @Test
    fun `pinned albums lead in pin order, then the chosen sort`() {
        fun album(id: Long, name: String, pinnedAt: Long? = null, position: Int? = null) =
            UserAlbumEntity(id = id, name = name, createdAt = id, pinnedAt = pinnedAt, position = position)
        val albums = listOf(album(1, "b"), album(2, "A", pinnedAt = 20), album(3, "c", position = 0), album(4, "d", pinnedAt = 10))
        val members = listOf(
            UserAlbumMemberEntity(1, 3, 0), // album 1's newest photo is the oldest in the library
            UserAlbumMemberEntity(3, 5, 0),
            UserAlbumMemberEntity(3, 4, 0),
        )

        fun order(sort: AlbumSort) = resolveUserAlbums(albums, members, library, sort).map { it.id }

        assertEquals(listOf(4L, 2L, 3L, 1L), order(AlbumSort.Newest))
        assertEquals(listOf(4L, 2L, 1L, 3L), order(AlbumSort.Name))
        assertEquals(listOf(4L, 2L, 3L, 1L), order(AlbumSort.MostItems))
        // Never placed (album 1) leads the custom order.
        assertEquals(listOf(4L, 2L, 1L, 3L), order(AlbumSort.Custom))
    }

    @Test
    fun `a photo can be in several albums and keeps index order`() {
        val albums = listOf(UserAlbumEntity(1, "a", 0), UserAlbumEntity(2, "b", 0))
        val members = listOf(UserAlbumMemberEntity(1, 3, 0), UserAlbumMemberEntity(1, 5, 9), UserAlbumMemberEntity(2, 5, 0))
        val resolved = resolveUserAlbums(albums, members, library, AlbumSort.Name)
        assertEquals(listOf(5L, 3L), resolved.first { it.id == 1L }.items.map { it.id })
        assertEquals(listOf(5L), resolved.first { it.id == 2L }.items.map { it.id })
    }
}

class AddMessageTest {
    @Test
    fun `the add toast says what went in and what was there already`() {
        assertEquals("Added 3 to Trip", AddResult(3, 0).message("Trip"))
        assertEquals("Added 1 to Trip · 2 were already there", AddResult(1, 2).message("Trip"))
        assertEquals("Nothing new for Trip · 1 was already there", AddResult(0, 1).message("Trip"))
    }
}
