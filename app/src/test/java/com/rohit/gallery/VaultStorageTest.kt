package com.rohit.gallery

import com.rohit.gallery.core.data.vault.PendingOutcome
import com.rohit.gallery.core.data.vault.VaultIds
import com.rohit.gallery.core.data.vault.VaultStorage
import com.rohit.gallery.core.data.vault.pendingOutcome
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultStorageTest {
    @get:Rule val temp = TemporaryFolder()

    private fun storage() = VaultStorage(temp.newFolder("vault"))

    private val bytes = ByteArray(300_000) { (it * 31 % 251).toByte() }

    @Test
    fun storedCopyMatchesTheInputByteForByte() {
        val vault = storage()
        val stored = vault.store(ByteArrayInputStream(bytes), ".JPG")

        assertTrue(stored.fileName.endsWith(".jpg"))
        assertEquals(bytes.size.toLong(), stored.sizeBytes)
        assertTrue(vault.file(stored.fileName).readBytes().contentEquals(bytes))
        assertEquals(VaultStorage.sha256(ByteArrayInputStream(bytes)), stored.sha256)
        assertTrue(vault.verify(stored.fileName, stored.sha256))
    }

    @Test
    fun verifyNoticesAChangedOrMissingFile() {
        val vault = storage()
        val stored = vault.store(ByteArrayInputStream(bytes), ".png")

        vault.file(stored.fileName).appendBytes(byteArrayOf(1))
        assertFalse(vault.verify(stored.fileName, stored.sha256))

        vault.delete(stored.fileName)
        assertFalse(vault.verify(stored.fileName, stored.sha256))
    }

    @Test
    fun aFailedCopyLeavesNothingBehind() {
        val dir = temp.newFolder("vault2")
        val vault = VaultStorage(dir)
        val broken = object : InputStream() {
            var left = 100_000
            override fun read(): Int = if (left-- > 0) 7 else throw IOException("read failed")
        }
        try {
            vault.store(broken, ".jpg")
            fail("expected the copy to fail")
        } catch (_: IOException) {
        }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun clearPartialsRemovesOnlyHalfWrittenCopies() {
        val dir = temp.newFolder("vault3")
        val vault = VaultStorage(dir)
        val kept = vault.store(ByteArrayInputStream(bytes), ".jpg")
        dir.resolve("abc.jpg.part").writeBytes(byteArrayOf(1, 2, 3))

        vault.clearPartials()

        assertEquals(listOf(kept.fileName), dir.listFiles()!!.map { it.name })
    }

    @Test
    fun vaultIdsRoundTripAndNeverLookLikeMediaStoreIds() {
        for (entity in listOf(1L, 2L, 999_999L)) {
            val mediaId = VaultIds.mediaIdOf(entity)
            assertTrue(mediaId < 0)
            assertEquals(entity, VaultIds.entityIdOf(mediaId))
        }
        assertNull(VaultIds.entityIdOf(12345L))
        assertNull(VaultIds.entityIdOf(-12345L))
    }

    @Test
    fun interruptedLockKeepsTheOnlyCopyAndDropsDuplicates() {
        // Original gone: the vault copy is the only one, keep it.
        assertEquals(PendingOutcome.Commit, pendingOutcome(originalExists = false))
        // Original still there: the lock didn't finish, drop the duplicate.
        assertEquals(PendingOutcome.RollBack, pendingOutcome(originalExists = true))
        // Unknown (no permission right now): delete nothing.
        assertEquals(PendingOutcome.Wait, pendingOutcome(originalExists = null))
    }
}
