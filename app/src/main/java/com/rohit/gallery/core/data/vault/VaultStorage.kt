package com.rohit.gallery.core.data.vault

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * The vault's files: plain copies in the app's private storage, which Android doesn't let other apps or file
 * managers read. Pure file work (no Android types), so it is unit-tested on the JVM.
 *
 * A copy is written to a `.part` file, flushed to disk, read back and checked against the hash of the bytes that went
 * in, and only then renamed to its final name. A copy that can't be verified is removed, so a vault file always
 * matches what was copied.
 */
class VaultStorage(private val dir: File) {

    /** A verified copy: its file name in the vault, size and SHA-256 (hex). */
    data class Stored(val fileName: String, val sizeBytes: Long, val sha256: String)

    /** Copies [input] into the vault as a new file ending in [extension] (e.g. ".jpg"), and verifies the copy. */
    fun store(input: InputStream, extension: String): Stored {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Can't create the vault folder")
        val name = UUID.randomUUID().toString() + extension.lowercase()
        val part = File(dir, "$name$PART")
        try {
            val digest = MessageDigest.getInstance(SHA_256)
            var size = 0L
            input.use { src ->
                FileOutputStream(part).use { out ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val read = src.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                        size += read
                    }
                    out.flush()
                    out.fd.sync()
                }
            }
            val expected = digest.digest().toHex()
            if (sha256(part) != expected) throw IOException("The vault copy doesn't match the original")
            val target = File(dir, name)
            if (!part.renameTo(target)) throw IOException("Couldn't finish the vault copy")
            return Stored(name, size, expected)
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
    }

    fun file(fileName: String): File = File(dir, fileName)

    /** True if [fileName] is in the vault and still has the content it was stored with. */
    fun verify(fileName: String, sha256: String): Boolean {
        val f = file(fileName)
        return f.isFile && sha256(f) == sha256
    }

    fun delete(fileName: String): Boolean = file(fileName).let { !it.exists() || it.delete() }

    /** Removes copies left half-written by an interrupted lock. */
    fun clearPartials() {
        dir.listFiles { f -> f.name.endsWith(PART) }?.forEach { it.delete() }
    }

    companion object {
        private const val PART = ".part"
        private const val SHA_256 = "SHA-256"
        private const val BUFFER = 64 * 1024

        fun sha256(file: File): String = FileInputStream(file).use(::sha256)

        fun sha256(input: InputStream): String {
            val digest = MessageDigest.getInstance(SHA_256)
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            return digest.digest().toHex()
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}

/**
 * The index ids of vault items: negative and far from MediaStore's positive ids and from the negative hashes used for
 * files opened from other apps, so thumbnails, resume positions and selection never mix them up.
 */
object VaultIds {
    private const val BASE = 1L shl 40

    fun mediaIdOf(entityId: Long): Long = -(BASE + entityId)

    fun entityIdOf(mediaId: Long): Long? = (-mediaId - BASE).takeIf { mediaId < 0 && it > 0 }

    /** A stable album id for the album a photo was locked from (grouped by name; negative, like vault item ids). */
    fun albumIdOf(albumName: String): Long = -(1L + (albumName.hashCode().toLong() and 0xFFFFFFFFL))
}

/** What to do with a vault copy whose lock was interrupted, given whether the original still exists. */
enum class PendingOutcome { Commit, RollBack, Wait }

/**
 * [originalExists]: true / false, or null when it couldn't be checked. The original gone means the vault copy is now
 * the only one and is kept; the original still there means the lock didn't finish, so the duplicate goes. When it
 * can't be told, nothing is deleted.
 */
fun pendingOutcome(originalExists: Boolean?): PendingOutcome = when (originalExists) {
    false -> PendingOutcome.Commit
    true -> PendingOutcome.RollBack
    null -> PendingOutcome.Wait
}
