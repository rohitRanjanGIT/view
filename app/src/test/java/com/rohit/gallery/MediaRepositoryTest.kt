package com.rohit.gallery

import com.rohit.gallery.core.data.repository.MediaIndexState
import com.rohit.gallery.core.data.repository.MediaRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class MediaRepositoryTest {

    private val fake = FakeMediaSource()

    @Test
    fun `starts loading and does not query before a refresh`() = runTest {
        val repo = MediaRepository(fake, backgroundScope)
        runCurrent()
        assertEquals(MediaIndexState.Loading, repo.state.value)
        assertEquals(0, fake.queryCount)
    }

    @Test
    fun `refresh loads items sorted newest first`() = runTest {
        fake.items = listOf(
            testItem(1, dateTakenMs = 1_000),
            testItem(2, dateTakenMs = 3_000),
            testItem(3, dateTakenMs = 0, dateModifiedMs = 2_000), // falls back to modified date
        )
        val repo = MediaRepository(fake, backgroundScope)
        runCurrent()

        repo.refresh()
        runCurrent()

        val ready = assertIs<MediaIndexState.Ready>(repo.state.value)
        assertEquals(listOf(2L, 3L, 1L), ready.items.map { it.id })
    }

    @Test
    fun `media changes are debounced into a single query`() = runTest {
        val repo = MediaRepository(fake, backgroundScope)
        runCurrent()

        repeat(5) { fake.changeEvents.emit(Unit) }
        advanceTimeBy(MediaRepository.OBSERVER_DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals(0, fake.queryCount)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, fake.queryCount)
        assertIs<MediaIndexState.Ready>(repo.state.value)
    }

    @Test
    fun `new camera photo appears after a change notification`() = runTest {
        fake.items = listOf(testItem(1, dateTakenMs = 1_000))
        val repo = MediaRepository(fake, backgroundScope)
        runCurrent()
        repo.refresh()
        runCurrent()

        fake.items = fake.items + testItem(2, dateTakenMs = 5_000)
        fake.changeEvents.emit(Unit)
        advanceTimeBy(MediaRepository.OBSERVER_DEBOUNCE_MS + 1)
        runCurrent()

        val ready = assertIs<MediaIndexState.Ready>(repo.state.value)
        assertEquals(listOf(2L, 1L), ready.items.map { it.id })
    }

    @Test
    fun `failed first query shows empty instead of loading forever`() = runTest {
        fake.failure = SecurityException("no permission")
        val repo = MediaRepository(fake, backgroundScope)
        runCurrent()

        repo.refresh()
        runCurrent()

        assertEquals(MediaIndexState.Ready(emptyList()), repo.state.value)
    }

    @Test
    fun `failed refresh keeps the last good index`() = runTest {
        fake.items = listOf(testItem(1, dateTakenMs = 1_000))
        val repo = MediaRepository(fake, backgroundScope)
        runCurrent()
        repo.refresh()
        runCurrent()

        fake.failure = SecurityException("revoked")
        repo.refresh()
        runCurrent()

        val ready = assertIs<MediaIndexState.Ready>(repo.state.value)
        assertEquals(listOf(1L), ready.items.map { it.id })
    }

}
