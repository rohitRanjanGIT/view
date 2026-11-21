package com.rohit.gallery.core.domain

import java.io.InputStream

/*
 * The pure parts of Move to album's fallback (copy, verify, then delete the original). Android refuses to change the
 * folder of a photo another app owns (WhatsApp Images...: "Changing ownership ... not allowed"), so such a photo is
 * copied into the destination, the copy is checked byte for byte, and only then is the original deleted.
 * Nothing here touches Android, so it is unit tested.
 */

/**
 * [name] if no name in [existing] equals it (ignoring case, like the file system on the phone), else the first of
 * `base (1).ext`, `base (2).ext`... that is free. The copy keeps the photo's own name whenever it can.
 */
fun uniqueDisplayName(name: String, existing: Collection<String>): String {
    val taken = existing.mapTo(HashSet()) { it.lowercase() }
    if (name.lowercase() !in taken) return name
    val dot = name.lastIndexOf('.')
    val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
    var n = 1
    while ("$base ($n)$ext".lowercase() in taken) n++
    return "$base ($n)$ext"
}

/** Whether [a] and [b] hold exactly the same bytes (same length, same content), read in chunks. */
fun streamsEqual(a: InputStream, b: InputStream, chunk: Int = 64 * 1024): Boolean {
    val bufA = ByteArray(chunk)
    val bufB = ByteArray(chunk)
    while (true) {
        val readA = fill(a, bufA)
        val readB = fill(b, bufB)
        if (readA != readB) return false
        if (readA == 0) return true
        if (!bufA.contentEquals(bufB, readA)) return false
    }
}

/** Reads until [buffer] is full or the stream ends (a plain read may return less); returns the bytes read. */
private fun fill(input: InputStream, buffer: ByteArray): Int {
    var total = 0
    while (total < buffer.size) {
        val read = input.read(buffer, total, buffer.size - total)
        if (read < 0) break
        total += read
    }
    return total
}

private fun ByteArray.contentEquals(other: ByteArray, length: Int): Boolean {
    for (i in 0 until length) if (this[i] != other[i]) return false
    return true
}

/**
 * The toast after a move that may have used the fallback. [moved] photos are in the new album, [declined] of the
 * others were left where they were because the delete confirmation was not given (their copies were removed),
 * the rest [failed]. [note] says what was skipped before it started, if anything.
 */
fun moveSummary(verb: String, total: Int, moved: Int, failed: Int, declined: Int, note: String?): String {
    val main = when {
        declined > 0 && moved == 0 && failed == 0 -> "Not moved: the delete wasn't allowed, so nothing changed"
        declined > 0 -> buildString {
            append("$verb $moved, $declined not moved (delete wasn't allowed)")
            if (failed > 0) append(", $failed failed")
        }
        failed == 0 -> "$verb $total ${if (total == 1) "item" else "items"}"
        moved == 0 -> "Couldn't complete that"
        else -> "$verb $moved, $failed failed"
    }
    return if (note == null) main else "$main. $note"
}
