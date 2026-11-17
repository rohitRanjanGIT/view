package com.rohit.gallery.core.data.mediastore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.exifinterface.media.ExifInterface
import com.rohit.gallery.core.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the info sheet shows that is not already in the in-memory index. */
@Immutable
data class MediaDetails(
    val fileName: String?,
    /** Folder relative to the storage root, e.g. `DCIM/Camera/`. */
    val folder: String?,
    val camera: String?,
    /** Aperture, exposure, ISO and focal length, already formatted for display. */
    val exposure: List<String>,
    /** Decimal "lat, lon" text; null when the file has no location or it cannot be read. */
    val location: String?,
    /** True when location could not be read because ACCESS_MEDIA_LOCATION is not granted. */
    val locationNeedsPermission: Boolean,
)

/** Reads per-file details on demand (never on the main thread). Originals are only read, never modified. */
@Singleton
class MediaInfoLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun load(item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
        val (name, folder) = queryNameAndFolder(item)
        val canReadLocation = context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

        var camera: String? = null
        var exposure: List<String> = emptyList()
        var location: String? = null
        if (item.isVideo) {
            location = runCatching { videoLocation(item) }.getOrNull()
        } else {
            runCatching {
                // setRequireOriginal gives access to the GPS tags, which are redacted otherwise.
                val uri = if (canReadLocation) MediaStore.setRequireOriginal(item.uri) else item.uri
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    camera = listOfNotNull(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL))
                        .joinToString(" ")
                        .ifBlank { null }
                    exposure = exposureLines(exif)
                    if (canReadLocation) {
                        location = exif.latLong?.let { formatLocation(it[0], it[1]) }
                    }
                }
            }
        }
        MediaDetails(
            fileName = name,
            folder = folder,
            camera = camera,
            exposure = exposure,
            location = location,
            locationNeedsPermission = !canReadLocation && location == null,
        )
    }

    private fun queryNameAndFolder(item: MediaItem): Pair<String?, String?> {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH)
        return runCatching {
            context.contentResolver.query(item.uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
            }
        }.getOrNull() ?: (null to null)
    }

    private fun videoLocation(item: MediaItem): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, item.uri)
            val iso6709 = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION) ?: return null
            // Looks like "+37.7749-122.4194/".
            val match = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(iso6709) ?: return null
            formatLocation(match.groupValues[1].toDouble(), match.groupValues[2].toDouble())
        } finally {
            retriever.release()
        }
    }
}

internal fun formatLocation(lat: Double, lon: Double): String =
    String.format(Locale.US, "%.5f, %.5f", lat, lon)

private fun exposureLines(exif: ExifInterface): List<String> = buildList {
    exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }
        ?.let { add(String.format(Locale.US, "f/%.1f", it)) }
    exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }
        ?.let { add(formatExposure(it)) }
    exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { add("ISO $it") }
    exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }
        ?.let { add(String.format(Locale.US, "%.1f mm", it)) }
}

internal fun formatExposure(seconds: Double): String =
    if (seconds >= 1.0) String.format(Locale.US, "%.1f s", seconds) else "1/${(1.0 / seconds).roundToInt()} s"
