package com.rohit.gallery.core.data.mediastore

import android.content.Context
import android.util.LruCache
import com.rohit.gallery.core.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Things about a photo that only its file header tells (MediaStore does not index them). */
data class MediaTraits(
    /** Ultra HDR: a JPEG carrying a gain map, shown brighter on an HDR screen. */
    val ultraHdr: Boolean = false,
    /** A photo with a short video inside (Google / Samsung / Motorola "motion photo"). */
    val motionPhoto: Boolean = false,
) {
    companion object {
        val None = MediaTraits()
    }
}

/**
 * Reads [MediaTraits] from the start of a photo's file: both are announced in the XMP metadata near the top
 * (`hdrgm:` for the gain map, `MotionPhoto` / `MicroVideo` for the clip), so only the first part is read, on
 * the IO dispatcher, and the answer is cached per item.
 */
@Singleton
class MediaTraitsReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val cache = LruCache<String, MediaTraits>(256)

    suspend fun read(item: MediaItem): MediaTraits {
        if (item.isVideo || !item.mimeType.equals("image/jpeg", ignoreCase = true)) return MediaTraits.None
        val key = "${item.id}:${item.dateModifiedMs}"
        cache.get(key)?.let { return it }
        val traits = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    val head = ByteArray(HEADER_BYTES)
                    var read = 0
                    while (read < head.size) {
                        val n = input.read(head, read, head.size - read)
                        if (n <= 0) break
                        read += n
                    }
                    traitsOf(String(head, 0, read, Charsets.ISO_8859_1))
                }
            }.getOrNull() ?: MediaTraits.None
        }
        cache.put(key, traits)
        return traits
    }

    private companion object {
        const val HEADER_BYTES = 256 * 1024
    }
}

/** [MediaTraits] from the text of a JPEG's first bytes (bytes read as ISO-8859-1, so offsets match). */
internal fun traitsOf(header: String): MediaTraits = MediaTraits(
    ultraHdr = header.contains("hdrgm:Version"),
    motionPhoto = MOTION.containsMatchIn(header),
)

/** `GCamera:MotionPhoto="1"`, `Camera:MotionPhoto="1"` or the older `MicroVideo="1"`, as attribute or element. */
private val MOTION = Regex("""(MotionPhoto|MicroVideo)(="|>)1""")
